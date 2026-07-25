package net.martinhenkel.hoernix.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import net.martinhenkel.hoernix.DspBruecke
import net.martinhenkel.hoernix.R

/**
 * Vordergrund-Dienst der Mikrofon-Durchleitung (Plan Kap. 2.3).
 *
 * Sicherheitsregeln: Start nur bei Kopfhörer-Ausgabe; der eingebaute
 * Lautsprecher ist generell ausgeschlossen (Rückkopplungsgefahr). Fällt die
 * Kopfhörer-Route im Betrieb weg, stoppt die Durchleitung sofort.
 */
class MikrofonDienst : Service() {

    private lateinit var audioManager: AudioManager
    private var griff = 0L
    private val pruefTakt = Handler(Looper.getMainLooper())

    private val geraeteBeobachter = object : AudioDeviceCallback() {
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            if (kopfhoererAusgabe() == null) {
                beende(MikrofonZustand.Status.GESTOPPT)
            }
        }
    }

    private val fehlerPruefung = object : Runnable {
        override fun run() {
            if (DspBruecke.mikFehler()) {
                beende(MikrofonZustand.Status.FEHLER)
            } else {
                pruefTakt.postDelayed(this, 1000)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == AKTION_STOPP) {
            beende(MikrofonZustand.Status.GESTOPPT)
            return START_NOT_STICKY
        }

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        starteVordergrund()

        val ausgabe = kopfhoererAusgabe()
        if (ausgabe == null) {
            beende(MikrofonZustand.Status.VERWEIGERT_LAUTSPRECHER)
            return START_NOT_STICKY
        }
        val mikrofon = geraetemikrofon()
        if (mikrofon == null) {
            beende(MikrofonZustand.Status.FEHLER)
            return START_NOT_STICKY
        }

        griff = DspBruecke.mikStarte(mikrofon.id)
        if (griff == 0L) {
            beende(MikrofonZustand.Status.FEHLER)
            return START_NOT_STICKY
        }

        audioManager.registerAudioDeviceCallback(geraeteBeobachter, null)
        pruefTakt.postDelayed(fehlerPruefung, 1000)
        MikrofonZustand.melde(
            MikrofonZustand.Status.LAEUFT,
            latenzHinweis = ausgabe.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        )
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        pruefTakt.removeCallbacksAndMessages(null)
        runCatching { audioManager.unregisterAudioDeviceCallback(geraeteBeobachter) }
        DspBruecke.mikStoppe()
        griff = 0L
        if (MikrofonZustand.status.value == MikrofonZustand.Status.LAEUFT) {
            MikrofonZustand.melde(MikrofonZustand.Status.GESTOPPT)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun beende(status: MikrofonZustand.Status) {
        MikrofonZustand.melde(status)
        stopSelf()
    }

    /** Erste taugliche Kopfhörer-Ausgabe; null = nur Lautsprecher/Hörmuschel. */
    private fun kopfhoererAusgabe(): AudioDeviceInfo? =
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull {
            it.type in intArrayOf(
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_USB_DEVICE,
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLE_HEADSET,
                AudioDeviceInfo.TYPE_HEARING_AID,
            )
        }

    /** Eingebautes Mikrofon — nie das Headset-Mikrofon (Plan Kap. 2.3). */
    private fun geraetemikrofon(): AudioDeviceInfo? =
        audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }

    private fun starteVordergrund() {
        val kanal = NotificationChannel(
            KANAL_ID,
            getString(R.string.mik_benachrichtigung_kanal),
            NotificationManager.IMPORTANCE_LOW,
        )
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(kanal)

        val stoppAbsicht = PendingIntent.getService(
            this, 0,
            Intent(this, MikrofonDienst::class.java).setAction(AKTION_STOPP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val benachrichtigung = Notification.Builder(this, KANAL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(getString(R.string.mik_benachrichtigung_titel))
            .addAction(
                Notification.Action.Builder(
                    null, getString(R.string.mik_stoppen), stoppAbsicht
                ).build()
            )
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                BENACHRICHTIGUNG_ID, benachrichtigung,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } else {
            startForeground(BENACHRICHTIGUNG_ID, benachrichtigung)
        }
    }

    companion object {
        const val AKTION_STOPP = "net.martinhenkel.hoernix.MIK_STOPP"
        private const val KANAL_ID = "mikrofon"
        private const val BENACHRICHTIGUNG_ID = 1
    }
}
