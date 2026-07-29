package net.martinhenkel.hoernix

/**
 * JNI-Brücke zum C++-DSP-Kern (libhoernix_dsp).
 *
 * Ein „Griff" (Handle) bezeichnet eine Stereo-Motor-Instanz (zwei unabhängige
 * Kanalketten). [gibFrei] muss zu jedem [erzeuge] gerufen werden.
 */
object DspBruecke {
    const val KANAL_LINKS = 0
    const val KANAL_RECHTS = 1
    const val ANZAHL_BAENDER = 11

    init {
        System.loadLibrary("hoernix_dsp")
    }

    /** Versionskennung des DSP-Kerns. */
    external fun version(): String

    external fun erzeuge(abtastrate: Float): Long

    external fun gibFrei(griff: Long)

    /** Verarbeitet verschränktes Stereo (L R L R …) in-place; [rahmen] Paare. */
    external fun verarbeiteVerschraenkt(griff: Long, daten: FloatArray, rahmen: Int)

    external fun setzeEinstellung(
        griff: Long,
        kanal: Int,
        baenderDb: FloatArray,
        kompressionAktiv: Boolean,
        grenzFrequenzHz: Float,
        verhaeltnis: Float,
        transpositionAktiv: Boolean,
        quelleVonHz: Float,
        quelleBisHz: Float,
        versatzHz: Float,
        schwelleDb: Float,
    )

    /** Fail-closed-Zustand (Ausgabe stummgeschaltet). */
    external fun fehler(griff: Long): Boolean

    /** Greift der Begrenzer aktuell ein? (Pegelanzeige) */
    external fun begrenzerEingriff(griff: Long): Boolean

    /**
     * Betragsspitze der Ausgabe (linear, 0…1+) seit der letzten Abfrage;
     * die Abfrage setzt den Merker zurück.
     */
    external fun spitzenPegel(griff: Long, kanal: Int): Float

    external fun ruecksetzen(griff: Long)

    /**
     * Startet die Mikrofon-Durchleitung über das Gerät mit der übergebenen
     * Eingabe-Geräte-Id (Gerätemikrofon). Rückgabe: Motor-Griff für
     * [setzeEinstellung] u. a. — gehört der Schleife, nie [gibFrei] rufen.
     * 0 = Start fehlgeschlagen oder schon aktiv.
     */
    external fun mikStarte(mikGeraetId: Int): Long

    external fun mikStoppe()

    /** Stromabriss der Mikrofon-Durchleitung (z. B. Gerät getrennt). */
    external fun mikFehler(): Boolean
}
