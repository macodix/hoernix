package net.martinhenkel.hoernix.profil

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Ergebnis einer Profil-Prüfung bzw. eines Imports. */
sealed interface PruefErgebnis {
    /** Gültig; [profil] ist bereits geklemmt. */
    data class Gueltig(val profil: Profil) : PruefErgebnis

    /** Abgewiesen; [grund] ist eine anzeigefertige deutsche Begründung. */
    data class Abgewiesen(val grund: String) : PruefErgebnis
}

/**
 * Prüfung und Klemmung von Profilen (Plan Kap. 2.5): Wertebereiche werden
 * geprüft, Pegelwerte gegen ihre festen Grenzen geklemmt; ungültige Daten
 * werden abgewiesen, nie teilweise übernommen. Die JSON-Verarbeitung ist
 * strikt (unbekannte Schlüssel führen zum Fehler, keine polymorphe
 * Deserialisierung).
 */
object ProfilPruefung {

    private class Ablehnung(val grund: String) : Exception(grund)

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    fun nachJson(profil: Profil): String = json.encodeToString(profil)

    /** Parst und prüft einen Profil-Import; wendet die Größenschranke an. */
    fun vonJson(text: String): PruefErgebnis {
        if (text.toByteArray(Charsets.UTF_8).size > ProfilGrenzen.IMPORT_MAX_BYTES) {
            return PruefErgebnis.Abgewiesen("Datei größer als 64 KiB")
        }
        val profil = try {
            json.decodeFromString<Profil>(text)
        } catch (_: SerializationException) {
            return PruefErgebnis.Abgewiesen(
                "Kein gültiges HörNix-Profil (JSON-Aufbau fehlerhaft oder unbekannte Felder)"
            )
        } catch (_: IllegalArgumentException) {
            return PruefErgebnis.Abgewiesen("Kein gültiges HörNix-Profil (Feldinhalte fehlerhaft)")
        }
        return pruefeUndKlemme(profil)
    }

    /** Prüft Wertebereiche; klemmt Pegelwerte (Bänder, Limiter-Schwelle). */
    fun pruefeUndKlemme(profil: Profil): PruefErgebnis = try {
        if (profil.name.isBlank()) {
            throw Ablehnung("Profilname fehlt")
        }
        if (profil.name.length > ProfilGrenzen.NAME_MAX_ZEICHEN) {
            throw Ablehnung("Profilname länger als ${ProfilGrenzen.NAME_MAX_ZEICHEN} Zeichen")
        }
        if (!profil.schwelleDb.isFinite()) {
            throw Ablehnung("Limiter-Schwelle ist kein gültiger Zahlenwert")
        }
        pruefeAudiogramm(profil.audiogrammLinksDb, "links")
        pruefeAudiogramm(profil.audiogrammRechtsDb, "rechts")

        PruefErgebnis.Gueltig(
            profil.copy(
                links = pruefeUndKlemmeOhr(profil.links, "links"),
                rechts = pruefeUndKlemmeOhr(profil.rechts, "rechts"),
                schwelleDb = profil.schwelleDb.coerceIn(
                    ProfilGrenzen.SCHWELLE_MIN_DB, ProfilGrenzen.SCHWELLE_MAX_DB
                ),
            )
        )
    } catch (a: Ablehnung) {
        PruefErgebnis.Abgewiesen(a.grund)
    }

    private fun pruefeUndKlemmeOhr(ohr: OhrEinstellung, seite: String): OhrEinstellung {
        fun ablehnen(grund: String): Nothing = throw Ablehnung("Ohr $seite: $grund")

        if (ohr.baenderDb.size != ProfilGrenzen.ANZAHL_BAENDER) {
            ablehnen(
                "erwartet ${ProfilGrenzen.ANZAHL_BAENDER} Bandwerte, enthalten: ${ohr.baenderDb.size}"
            )
        }
        if (ohr.baenderDb.any { !it.isFinite() }) {
            ablehnen("Bandwert ist kein gültiger Zahlenwert")
        }
        val verschiebungsWerte = listOf(
            ohr.grenzFrequenzHz, ohr.verhaeltnis,
            ohr.quelleVonHz, ohr.quelleBisHz, ohr.versatzHz,
        )
        if (verschiebungsWerte.any { !it.isFinite() }) {
            ablehnen("Verschiebungs-Wert ist kein gültiger Zahlenwert")
        }
        if (ohr.grenzFrequenzHz !in
            ProfilGrenzen.GRENZ_FREQUENZ_MIN_HZ..ProfilGrenzen.GRENZ_FREQUENZ_MAX_HZ
        ) {
            ablehnen(
                "Grenzfrequenz außerhalb " +
                        "${ProfilGrenzen.GRENZ_FREQUENZ_MIN_HZ.toInt()}–" +
                        "${ProfilGrenzen.GRENZ_FREQUENZ_MAX_HZ.toInt()} Hz"
            )
        }
        if (ohr.verhaeltnis !in ProfilGrenzen.VERHAELTNIS_MIN..ProfilGrenzen.VERHAELTNIS_MAX) {
            ablehnen(
                "Kompressionsverhältnis außerhalb " +
                        "${ProfilGrenzen.VERHAELTNIS_MIN}–${ProfilGrenzen.VERHAELTNIS_MAX}"
            )
        }
        if (ohr.quelleVonHz !in ProfilGrenzen.QUELLE_MIN_HZ..ProfilGrenzen.QUELLE_MAX_HZ ||
            ohr.quelleBisHz !in ProfilGrenzen.QUELLE_MIN_HZ..ProfilGrenzen.QUELLE_MAX_HZ ||
            ohr.quelleVonHz >= ohr.quelleBisHz
        ) {
            ablehnen("Transpositions-Quellbereich ungültig")
        }
        if (ohr.versatzHz <= 0.0f || ohr.versatzHz > ohr.quelleVonHz) {
            ablehnen(
                "Transpositions-Versatz ungültig " +
                        "(muss über 0 liegen und darf den Quellbeginn nicht überschreiten)"
            )
        }

        return ohr.copy(
            baenderDb = ohr.baenderDb.map {
                it.coerceIn(ProfilGrenzen.BAND_MIN_DB, ProfilGrenzen.BAND_MAX_DB)
            },
        )
    }

    private fun pruefeAudiogramm(werte: List<Float>?, seite: String) {
        if (werte == null) {
            return
        }
        if (werte.size != ProfilGrenzen.ANZAHL_BAENDER) {
            throw Ablehnung(
                "Audiogramm $seite: erwartet ${ProfilGrenzen.ANZAHL_BAENDER} Werte, " +
                        "enthalten: ${werte.size}"
            )
        }
        if (werte.any {
                !it.isFinite() ||
                        it !in ProfilGrenzen.AUDIOGRAMM_MIN_DB..ProfilGrenzen.AUDIOGRAMM_MAX_DB
            }
        ) {
            throw Ablehnung(
                "Audiogramm $seite: Wert außerhalb " +
                        "${ProfilGrenzen.AUDIOGRAMM_MIN_DB.toInt()}–" +
                        "${ProfilGrenzen.AUDIOGRAMM_MAX_DB.toInt()} dB"
            )
        }
    }
}
