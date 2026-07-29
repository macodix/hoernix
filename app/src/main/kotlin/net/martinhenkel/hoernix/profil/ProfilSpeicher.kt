package net.martinhenkel.hoernix.profil

import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** Gesamtbestand: alle Profile plus Markierung des aktiven (Plan Kap. 2.5). */
@Serializable
data class ProfilBestand(
    val aktivName: String,
    val profile: List<Profil>,
) {
    fun aktives(): Profil =
        profile.firstOrNull { it.name == aktivName } ?: profile.first()

    companion object {
        const val STANDARD_NAME = "Standard"

        fun standard(): ProfilBestand =
            ProfilBestand(STANDARD_NAME, listOf(Profil(STANDARD_NAME)))
    }
}

/**
 * Ablage des Profilbestands als JSON im App-Datenverzeichnis.
 *
 * Schreiben erfolgt atomar (Temporärdatei + Umbenennen). Eine unlesbare oder
 * ungültige Bestandsdatei wird nach `profile.json.defekt` gesichert und durch
 * den Standardbestand ersetzt — nie teilweise übernommen.
 */
class ProfilSpeicher(verzeichnis: File) {

    private val datei = File(verzeichnis, DATEINAME)
    private val json = Json { prettyPrint = true }

    fun lade(): ProfilBestand {
        if (!datei.isFile) {
            return ProfilBestand.standard()
        }
        val bestand = try {
            json.decodeFromString<ProfilBestand>(datei.readText())
        } catch (_: SerializationException) {
            null
        } catch (_: java.io.IOException) {
            null
        }
        if (bestand == null || bestand.profile.isEmpty() ||
            bestand.profile.any { ProfilPruefung.pruefeUndKlemme(it) !is PruefErgebnis.Gueltig }
        ) {
            datei.renameTo(File(datei.parentFile, "$DATEINAME.defekt"))
            return ProfilBestand.standard()
        }
        return if (bestand.profile.any { it.name == bestand.aktivName }) {
            bestand
        } else {
            bestand.copy(aktivName = bestand.profile.first().name)
        }
    }

    fun speichere(bestand: ProfilBestand) {
        val temporaer = File(datei.parentFile, "$DATEINAME.neu")
        temporaer.writeText(json.encodeToString(bestand))
        if (!temporaer.renameTo(datei)) {
            temporaer.delete()
        }
    }

    companion object {
        private const val DATEINAME = "profile.json"
    }
}
