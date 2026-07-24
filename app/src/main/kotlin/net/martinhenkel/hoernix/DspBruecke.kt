package net.martinhenkel.hoernix

/** JNI-Brücke zum C++-DSP-Kern (libhoernix_dsp). */
object DspBruecke {
    init {
        System.loadLibrary("hoernix_dsp")
    }

    /** Versionskennung des DSP-Kerns. */
    external fun version(): String
}
