package net.martinhenkel.hoernix.profil

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Zentrale Profilverwaltung (Plan Kap. 2.5): hält den Bestand, speichert
 * jede Änderung sofort und verteilt das aktive Profil an die laufenden
 * DSP-Motoren. Vor Nutzung [initialisiere] rufen (idempotent).
 */
object ProfilVerwaltung {

    private var speicher: ProfilSpeicher? = null

    private val _bestand = MutableStateFlow(ProfilBestand.standard())
    val bestand: StateFlow<ProfilBestand> = _bestand

    @Synchronized
    fun initialisiere(context: Context) {
        if (speicher != null) {
            return
        }
        val neu = ProfilSpeicher(context.filesDir)
        speicher = neu
        _bestand.value = neu.lade()
        EinstellungsVerteiler.verteile(_bestand.value.aktives())
    }

    fun aktives(): Profil = _bestand.value.aktives()

    @Synchronized
    fun aktiviere(name: String) {
        val bestand = _bestand.value
        if (bestand.profile.none { it.name == name } || bestand.aktivName == name) {
            return
        }
        uebernimm(bestand.copy(aktivName = name))
    }

    /** Ändert das aktive Profil (z. B. Reglerbewegung) und verteilt sofort. */
    @Synchronized
    fun aendereAktives(aenderung: (Profil) -> Profil) {
        val bestand = _bestand.value
        val alt = bestand.aktives()
        val neu = aenderung(alt).copy(name = alt.name)
        val geprueft = ProfilPruefung.pruefeUndKlemme(neu) as? PruefErgebnis.Gueltig ?: return
        uebernimm(
            bestand.copy(
                profile = bestand.profile.map {
                    if (it.name == alt.name) geprueft.profil else it
                }
            )
        )
    }

    /** true = angelegt; false = Name leer, zu lang oder schon vergeben. */
    @Synchronized
    fun legeAn(name: String): Boolean {
        val bereinigt = name.trim()
        val bestand = _bestand.value
        if (bereinigt.isEmpty() || bereinigt.length > ProfilGrenzen.NAME_MAX_ZEICHEN ||
            bestand.profile.any { it.name == bereinigt }
        ) {
            return false
        }
        uebernimm(
            bestand.copy(
                aktivName = bereinigt,
                profile = bestand.profile + Profil(bereinigt),
            )
        )
        return true
    }

    /** true = umbenannt; false = Zielname ungültig/vergeben oder Quelle fehlt. */
    @Synchronized
    fun benenneUm(alt: String, neu: String): Boolean {
        val bereinigt = neu.trim()
        val bestand = _bestand.value
        if (bereinigt.isEmpty() || bereinigt.length > ProfilGrenzen.NAME_MAX_ZEICHEN ||
            bestand.profile.any { it.name == bereinigt } ||
            bestand.profile.none { it.name == alt }
        ) {
            return false
        }
        uebernimm(
            bestand.copy(
                aktivName = if (bestand.aktivName == alt) bereinigt else bestand.aktivName,
                profile = bestand.profile.map {
                    if (it.name == alt) it.copy(name = bereinigt) else it
                },
            )
        )
        return true
    }

    /** Löscht [name]; das letzte verbliebene Profil ist nicht löschbar. */
    @Synchronized
    fun loesche(name: String): Boolean {
        val bestand = _bestand.value
        if (bestand.profile.size <= 1 || bestand.profile.none { it.name == name }) {
            return false
        }
        val rest = bestand.profile.filterNot { it.name == name }
        uebernimm(
            bestand.copy(
                aktivName = if (bestand.aktivName == name) rest.first().name
                else bestand.aktivName,
                profile = rest,
            )
        )
        return true
    }

    @Synchronized
    fun dupliziere(name: String): Boolean {
        val bestand = _bestand.value
        val quelle = bestand.profile.firstOrNull { it.name == name } ?: return false
        val neuName = freierName(quelle.name)
        uebernimm(bestand.copy(profile = bestand.profile + quelle.copy(name = neuName)))
        return true
    }

    /** Schreibt das Profil [name] als JSON nach [ziel] (Systemdateidialog). */
    fun exportiere(resolver: ContentResolver, ziel: Uri, name: String): Boolean {
        val profil = _bestand.value.profile.firstOrNull { it.name == name } ?: return false
        return try {
            resolver.openOutputStream(ziel, "wt")?.use {
                it.write(ProfilPruefung.nachJson(profil).toByteArray(Charsets.UTF_8))
            } != null
        } catch (_: java.io.IOException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    /**
     * Liest, prüft und übernimmt ein Profil aus [quelle]; bei Namenskollision
     * erhält es einen Zählersuffix. Das importierte Profil wird aktiv.
     */
    @Synchronized
    fun importiere(resolver: ContentResolver, quelle: Uri): PruefErgebnis {
        val text = try {
            resolver.openInputStream(quelle)?.use { strom ->
                // Ein Byte über der Schranke lesen, damit Überlänge erkannt wird.
                String(liesBegrenzt(strom, ProfilGrenzen.IMPORT_MAX_BYTES + 1), Charsets.UTF_8)
            }
        } catch (_: java.io.IOException) {
            null
        } catch (_: SecurityException) {
            null
        } ?: return PruefErgebnis.Abgewiesen("Datei nicht lesbar")

        val ergebnis = ProfilPruefung.vonJson(text)
        if (ergebnis !is PruefErgebnis.Gueltig) {
            return ergebnis
        }
        val profil = ergebnis.profil.copy(name = freierName(ergebnis.profil.name))
        uebernimm(
            _bestand.value.copy(
                aktivName = profil.name,
                profile = _bestand.value.profile + profil,
            )
        )
        return PruefErgebnis.Gueltig(profil)
    }

    /** Liest höchstens [maxBytes] aus [strom] (readNBytes erst ab API 33). */
    private fun liesBegrenzt(strom: java.io.InputStream, maxBytes: Int): ByteArray {
        val puffer = ByteArray(maxBytes)
        var gelesen = 0
        while (gelesen < maxBytes) {
            val anzahl = strom.read(puffer, gelesen, maxBytes - gelesen)
            if (anzahl < 0) {
                break
            }
            gelesen += anzahl
        }
        return puffer.copyOf(gelesen)
    }

    private fun uebernimm(neu: ProfilBestand) {
        val altAktiv = _bestand.value.aktives()
        _bestand.value = neu
        speicher?.speichere(neu)
        if (neu.aktives() != altAktiv) {
            EinstellungsVerteiler.verteile(neu.aktives())
        }
    }

    /** [wunsch], bei Kollision „[wunsch] (2)", „[wunsch] (3)" … */
    private fun freierName(wunsch: String): String {
        val vergeben = _bestand.value.profile.map { it.name }.toSet()
        val basis = wunsch.take(ProfilGrenzen.NAME_MAX_ZEICHEN)
        if (basis !in vergeben) {
            return basis
        }
        var zaehler = 2
        while (true) {
            val zusatz = " ($zaehler)"
            val kandidat = basis.take(ProfilGrenzen.NAME_MAX_ZEICHEN - zusatz.length) + zusatz
            if (kandidat !in vergeben) {
                return kandidat
            }
            zaehler++
        }
    }
}
