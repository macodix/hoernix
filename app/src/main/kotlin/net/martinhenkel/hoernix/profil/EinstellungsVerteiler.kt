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

    /** Ein/Aus der Oberfläche: aus = neutrale Kette, Begrenzer bleibt aktiv. */
    @Volatile
    var verarbeitungAktiv: Boolean = true
        private set

    @Synchronized
    fun setzeVerarbeitung(aktiv: Boolean) {
        if (verarbeitungAktiv == aktiv) {
            return
        }
        verarbeitungAktiv = aktiv
        for (griff in griffe) {
            wendeAn(griff, aktuell)
        }
    }

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

    /** Läuft mindestens ein Motor (Player oder Mikrofon)? */
    @Synchronized
    fun motorenAktiv(): Boolean = griffe.isNotEmpty()

    /** Größte Ausgabespitze aller Motoren seit der letzten Abfrage (linear). */
    @Synchronized
    fun spitzenPegel(kanal: Int): Float =
        griffe.maxOfOrNull { DspBruecke.spitzenPegel(it, kanal) } ?: 0.0f

    @Synchronized
    fun begrenzerEingriff(): Boolean =
        griffe.any { DspBruecke.begrenzerEingriff(it) }

    /** Fail-closed-Zustand irgendeines Motors (Ausgabe stummgeschaltet). */
    @Synchronized
    fun fehlerVorhanden(): Boolean = griffe.any { DspBruecke.fehler(it) }

    private fun wendeAn(griff: Long, profil: Profil) {
        // Bei ausgeschalteter Verarbeitung: neutrale Ohren (Bänder 0 dB,
        // Verschiebung aus); die Limiter-Schwelle des Profils bleibt bestehen.
        val wirksam = if (verarbeitungAktiv) {
            profil
        } else {
            profil.copy(links = OhrEinstellung(), rechts = OhrEinstellung())
        }
        wendeOhrAn(griff, DspBruecke.KANAL_LINKS, wirksam.links, wirksam.schwelleDb)
        wendeOhrAn(griff, DspBruecke.KANAL_RECHTS, wirksam.rechts, wirksam.schwelleDb)
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
