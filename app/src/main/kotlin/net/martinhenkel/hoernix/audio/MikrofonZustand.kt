package net.martinhenkel.hoernix.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Zustand der Mikrofon-Durchleitung für Dienst und Oberfläche. */
object MikrofonZustand {
    enum class Status { GESTOPPT, LAEUFT, VERWEIGERT_LAUTSPRECHER, FEHLER }

    private val statusIntern = MutableStateFlow(Status.GESTOPPT)
    val status: StateFlow<Status> = statusIntern

    private val latenzHinweisIntern = MutableStateFlow(false)
    /** Ausgabe läuft über klassisches Bluetooth (A2DP) — hohe Verzögerung. */
    val latenzHinweis: StateFlow<Boolean> = latenzHinweisIntern

    internal fun melde(status: Status, latenzHinweis: Boolean = false) {
        statusIntern.value = status
        latenzHinweisIntern.value = latenzHinweis
    }
}
