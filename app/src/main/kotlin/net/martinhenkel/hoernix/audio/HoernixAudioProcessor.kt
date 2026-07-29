package net.martinhenkel.hoernix.audio

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import net.martinhenkel.hoernix.DspBruecke
import net.martinhenkel.hoernix.profil.EinstellungsVerteiler

/**
 * Hängt den DSP-Kern in die Media3-Wiedergabekette ein (Plan Kap. 2.4).
 *
 * Nimmt 16-Bit-PCM in Mono oder Stereo an; Mono wird auf Stereo verdoppelt,
 * damit beide Ohren getrennt verarbeitet werden können. Ausgabe: Stereo,
 * 16 Bit, gleiche Abtastrate.
 */
@OptIn(UnstableApi::class)
class HoernixAudioProcessor : BaseAudioProcessor() {

    private var griff = 0L
    private var abtastrate = 0
    private var eingangsKanaele = 2
    private var verschraenkt = FloatArray(0)

    /** Provisorisch bis zur echten Profil-Oberfläche (Umsetzungsschritt 6). */
    @Volatile
    var testprofilAktiv = false
        private set

    fun setzeTestprofil(aktiv: Boolean) {
        testprofilAktiv = aktiv
        synchronized(this) {
            if (griff == 0L) {
                return
            }
            if (aktiv) {
                wendeTestprofilAn()
            } else {
                // Erneutes Anmelden spielt das aktive Profil wieder ein.
                EinstellungsVerteiler.registriere(griff)
            }
        }
    }

    override fun onConfigure(
        inputAudioFormat: AudioProcessor.AudioFormat
    ): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT ||
            inputAudioFormat.channelCount !in 1..2
        ) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        eingangsKanaele = inputAudioFormat.channelCount
        abtastrate = inputAudioFormat.sampleRate
        return AudioProcessor.AudioFormat(
            inputAudioFormat.sampleRate, 2, C.ENCODING_PCM_16BIT
        )
    }

    override fun queueInput(inputBuffer: java.nio.ByteBuffer) {
        val rahmen = inputBuffer.remaining() / (2 * eingangsKanaele)
        if (rahmen == 0) {
            return
        }
        if (verschraenkt.size < rahmen * 2) {
            verschraenkt = FloatArray(rahmen * 2)
        }
        for (i in 0 until rahmen) {
            if (eingangsKanaele == 2) {
                verschraenkt[2 * i] = inputBuffer.short / 32768.0f
                verschraenkt[2 * i + 1] = inputBuffer.short / 32768.0f
            } else {
                val wert = inputBuffer.short / 32768.0f
                verschraenkt[2 * i] = wert
                verschraenkt[2 * i + 1] = wert
            }
        }

        synchronized(this) {
            if (griff != 0L) {
                DspBruecke.verarbeiteVerschraenkt(griff, verschraenkt, rahmen)
            }
        }

        val ausgabe = replaceOutputBuffer(rahmen * 2 * 2)
        for (i in 0 until rahmen * 2) {
            val wert = (verschraenkt[i] * 32767.0f)
                .coerceIn(-32768.0f, 32767.0f)
                .toInt()
            ausgabe.putShort(wert.toShort())
        }
        ausgabe.flip()
    }

    override fun onFlush() {
        synchronized(this) {
            if (griff != 0L && abtastrate != motorAbtastrate) {
                EinstellungsVerteiler.entferne(griff)
                DspBruecke.gibFrei(griff)
                griff = 0L
            }
            if (griff == 0L && abtastrate > 0) {
                griff = DspBruecke.erzeuge(abtastrate.toFloat())
                motorAbtastrate = abtastrate
                EinstellungsVerteiler.registriere(griff)
                if (testprofilAktiv) {
                    wendeTestprofilAn()
                }
            } else if (griff != 0L) {
                DspBruecke.ruecksetzen(griff)
            }
        }
    }

    override fun onReset() {
        synchronized(this) {
            if (griff != 0L) {
                EinstellungsVerteiler.entferne(griff)
                DspBruecke.gibFrei(griff)
                griff = 0L
                motorAbtastrate = 0
            }
        }
    }

    private var motorAbtastrate = 0

    /**
     * Testprofil: Höhen ab 2 kHz stark absenken — deutlich hörbar und
     * ungefährlich (keine Verstärkung). Überlagert das aktive Profil, bis der
     * Schalter aus- oder ein neues Profil eingespielt wird.
     */
    private fun wendeTestprofilAn() {
        val baender = FloatArray(DspBruecke.ANZAHL_BAENDER)
        for (b in 6 until DspBruecke.ANZAHL_BAENDER) {
            baender[b] = -24.0f
        }
        for (kanal in intArrayOf(DspBruecke.KANAL_LINKS, DspBruecke.KANAL_RECHTS)) {
            DspBruecke.setzeEinstellung(
                griff, kanal, baender,
                kompressionAktiv = false,
                grenzFrequenzHz = 3000.0f,
                verhaeltnis = 2.0f,
                transpositionAktiv = false,
                quelleVonHz = 6000.0f,
                quelleBisHz = 10000.0f,
                versatzHz = 3000.0f,
                schwelleDb = -12.0f,
            )
        }
    }
}
