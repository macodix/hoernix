package net.martinhenkel.hoernix.profil

import net.martinhenkel.hoernix.DspBruecke

/**
 * Verteilt das aktive Profil an alle laufenden DSP-Motoren (Player- und
 * Mikrofonpfad). Motoren melden sich mit ihrem Griff an und erhalten sofort
 * den aktuellen Stand; Profiländerungen erreichen alle Angemeldeten.
 * Sprunghafte Änderungen glättet der DSP-Kern selbst über ca. 200 ms
 * (dsp/parameter.h, kGlaettungSekunden).
 */
object EinstellungsVerteiler {

    private val griffe = mutableSetOf<Long>()
    private var aktuell: Profil = Profil(ProfilBestand.STANDARD_NAME)

    @Synchronized
    fun registriere(griff: Long) {
        if (griff == 0L) {
            return
        }
        griffe += griff
        wendeAn(griff, aktuell)
    }

    @Synchronized
    fun entferne(griff: Long) {
        griffe -= griff
    }

    @Synchronized
    fun verteile(profil: Profil) {
        aktuell = profil
        for (griff in griffe) {
            wendeAn(griff, profil)
        }
    }

    private fun wendeAn(griff: Long, profil: Profil) {
        wendeOhrAn(griff, DspBruecke.KANAL_LINKS, profil.links, profil.schwelleDb)
        wendeOhrAn(griff, DspBruecke.KANAL_RECHTS, profil.rechts, profil.schwelleDb)
    }

    private fun wendeOhrAn(griff: Long, kanal: Int, ohr: OhrEinstellung, schwelleDb: Float) {
        DspBruecke.setzeEinstellung(
            griff, kanal,
            baenderDb = ohr.baenderDb.toFloatArray(),
            kompressionAktiv = ohr.kompressionAktiv,
            grenzFrequenzHz = ohr.grenzFrequenzHz,
            verhaeltnis = ohr.verhaeltnis,
            transpositionAktiv = ohr.transpositionAktiv,
            quelleVonHz = ohr.quelleVonHz,
            quelleBisHz = ohr.quelleBisHz,
            versatzHz = ohr.versatzHz,
            schwelleDb = schwelleDb,
        )
    }
}
