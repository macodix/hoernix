package net.martinhenkel.hoernix.profil

import kotlinx.serialization.Serializable

/**
 * Wertegrenzen der Profilschicht (Plan Kap. 2.5). Die dB-Grenzen entsprechen
 * dsp/parameter.h; die Profilschicht hängt bewusst nicht an der JNI-Brücke,
 * damit sie ohne native Bibliothek testbar bleibt.
 */
object ProfilGrenzen {
    const val ANZAHL_BAENDER = 11

    const val BAND_MIN_DB = -24.0f
    const val BAND_MAX_DB = 24.0f

    const val SCHWELLE_MIN_DB = -24.0f
    const val SCHWELLE_MAX_DB = -6.0f

    const val GRENZ_FREQUENZ_MIN_HZ = 125.0f
    const val GRENZ_FREQUENZ_MAX_HZ = 8000.0f

    const val VERHAELTNIS_MIN = 1.0f
    const val VERHAELTNIS_MAX = 8.0f

    const val QUELLE_MIN_HZ = 500.0f
    const val QUELLE_MAX_HZ = 20000.0f

    const val AUDIOGRAMM_MIN_DB = 0.0f
    const val AUDIOGRAMM_MAX_DB = 120.0f

    const val NAME_MAX_ZEICHEN = 64

    /** Größenschranke für Importdateien (Plan Kap. 2.5). */
    const val IMPORT_MAX_BYTES = 64 * 1024
}

/** Einstellungen eines Ohrs; Voreinstellungen wie dsp/parameter.h. */
@Serializable
data class OhrEinstellung(
    val baenderDb: List<Float> = List(ProfilGrenzen.ANZAHL_BAENDER) { 0.0f },
    val kompressionAktiv: Boolean = false,
    val grenzFrequenzHz: Float = 3000.0f,
    val verhaeltnis: Float = 2.0f,
    val transpositionAktiv: Boolean = false,
    val quelleVonHz: Float = 6000.0f,
    val quelleBisHz: Float = 10000.0f,
    val versatzHz: Float = 3000.0f,
)

/**
 * Ein Profil (Plan Kap. 2.5): Name, je Ohr Bandverstärkungen und
 * Verschiebungs-Einstellungen, Koppel-Zustand, Limiter-Schwelle, optional
 * die Audiogramm-Eingabewerte (dB Hörverlust je Audiometrie-Frequenz).
 */
@Serializable
data class Profil(
    val name: String,
    val links: OhrEinstellung = OhrEinstellung(),
    val rechts: OhrEinstellung = OhrEinstellung(),
    val gekoppelt: Boolean = false,
    val schwelleDb: Float = -12.0f,
    val audiogrammLinksDb: List<Float>? = null,
    val audiogrammRechtsDb: List<Float>? = null,
)
