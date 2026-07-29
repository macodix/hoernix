package net.martinhenkel.hoernix.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import net.martinhenkel.hoernix.R
import net.martinhenkel.hoernix.audio.MikrofonDienst
import net.martinhenkel.hoernix.audio.MikrofonZustand
import net.martinhenkel.hoernix.profil.EinstellungsVerteiler
import net.martinhenkel.hoernix.profil.ProfilGrenzen
import net.martinhenkel.hoernix.profil.ProfilVerwaltung
import kotlin.math.log10
import kotlin.math.roundToInt
import androidx.compose.runtime.DisposableEffect

/** Hauptansicht (Plan Kap. 2.6): Modus, Profilwahl, Pegel, Ein/Aus. */
@Composable
fun StartAnsicht(player: ExoPlayer) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ProfilSchnellwahl()
        VerarbeitungsSchalter()
        Pegelanzeige()
        BegrenzerSchwelle()
        Gesamtlautstaerke()
        HorizontalDivider()
        PlayerBereich(player)
        HorizontalDivider()
        MikrofonBereich()
        HorizontalDivider()
        Text(
            text = stringResource(R.string.gehoerschutz_hinweis),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun ProfilSchnellwahl() {
    val bestand by ProfilVerwaltung.bestand.collectAsState()
    var offen by remember { mutableStateOf(false) }

    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.aktives_profil))
        OutlinedButton(onClick = { offen = true }) {
            Text(bestand.aktivName)
        }
        DropdownMenu(expanded = offen, onDismissRequest = { offen = false }) {
            bestand.profile.forEach { profil ->
                DropdownMenuItem(
                    text = { Text(profil.name) },
                    onClick = {
                        ProfilVerwaltung.aktiviere(profil.name)
                        offen = false
                    },
                )
            }
        }
    }
}

@Composable
private fun VerarbeitungsSchalter() {
    var aktiv by remember { mutableStateOf(EinstellungsVerteiler.verarbeitungAktiv) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Switch(checked = aktiv, onCheckedChange = {
            aktiv = it
            EinstellungsVerteiler.setzeVerarbeitung(it)
        })
        Text(stringResource(R.string.verarbeitung_aktiv))
    }
}

/** Pegelanzeige mit Limiter-Indikator und Fail-closed-Meldung (Kap. 2.2). */
@Composable
private fun Pegelanzeige() {
    var pegelLinksDb by remember { mutableFloatStateOf(-60.0f) }
    var pegelRechtsDb by remember { mutableFloatStateOf(-60.0f) }
    var begrenzer by remember { mutableStateOf(false) }
    var fehler by remember { mutableStateOf(false) }
    var motoren by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            motoren = EinstellungsVerteiler.motorenAktiv()
            pegelLinksDb = nachDb(EinstellungsVerteiler.spitzenPegel(0))
            pegelRechtsDb = nachDb(EinstellungsVerteiler.spitzenPegel(1))
            begrenzer = EinstellungsVerteiler.begrenzerEingriff()
            fehler = EinstellungsVerteiler.fehlerVorhanden()
            delay(150)
        }
    }

    if (!motoren) {
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PegelBalken(stringResource(R.string.seite_links), pegelLinksDb)
        PegelBalken(stringResource(R.string.seite_rechts), pegelRechtsDb)
        if (begrenzer) {
            Text(
                text = stringResource(R.string.begrenzer_eingriff),
                style = MaterialTheme.typography.labelLarge,
            )
        }
        if (fehler) {
            Text(
                text = stringResource(R.string.audio_fehler_stumm),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun PegelBalken(beschriftung: String, pegelDb: Float) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(beschriftung, modifier = Modifier.width(64.dp))
        LinearProgressIndicator(
            progress = { ((pegelDb + 60.0f) / 60.0f).coerceIn(0.0f, 1.0f) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun nachDb(linear: Float): Float =
    if (linear <= 0.001f) -60.0f else 20.0f * log10(linear)

/** Ansprechschwelle des Begrenzers, −24 bis −6 dBFS (Plan Kap. 2.2). */
@Composable
private fun BegrenzerSchwelle() {
    val bestand by ProfilVerwaltung.bestand.collectAsState()
    val schwelle = bestand.aktives().schwelleDb
    var zieht by remember { mutableStateOf(false) }
    var lokal by remember { mutableFloatStateOf(schwelle) }
    val angezeigt = if (zieht) lokal else schwelle

    Column {
        Text(
            stringResource(
                R.string.begrenzer_schwelle_wert,
                angezeigt.roundToInt(),
            )
        )
        Slider(
            value = angezeigt,
            onValueChange = {
                zieht = true
                lokal = it.roundToInt().toFloat()
            },
            onValueChangeFinished = {
                zieht = false
                ProfilVerwaltung.aendereAktives { it.copy(schwelleDb = lokal) }
            },
            valueRange = ProfilGrenzen.SCHWELLE_MIN_DB..ProfilGrenzen.SCHWELLE_MAX_DB,
            steps = 17,
        )
    }
}

@Composable
private fun Gesamtlautstaerke() {
    val context = LocalContext.current
    val audioManager = remember {
        context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager
    }
    val maximum = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    var stufe by remember {
        mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat())
    }

    Column {
        Text(stringResource(R.string.gesamtlautstaerke))
        Slider(
            value = stufe,
            onValueChange = {
                stufe = it
                audioManager.setStreamVolume(
                    AudioManager.STREAM_MUSIC, it.toInt(), 0
                )
            },
            valueRange = 0f..maximum.toFloat(),
            steps = (maximum - 1).coerceAtLeast(0),
        )
    }
}

@Composable
private fun PlayerBereich(player: ExoPlayer) {
    var titel by remember { mutableStateOf("") }
    var spielt by remember { mutableStateOf(false) }

    DisposableEffect(player) {
        val beobachter = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                spielt = isPlaying
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                titel = mediaItem?.localConfiguration?.uri?.lastPathSegment ?: ""
            }
        }
        player.addListener(beobachter)
        onDispose { player.removeListener(beobachter) }
    }

    val dateiWahl = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            player.setMediaItems(uris.map { MediaItem.fromUri(it) })
            player.prepare()
            player.play()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.player_ueberschrift),
            style = MaterialTheme.typography.titleMedium,
        )
        Button(onClick = { dateiWahl.launch(arrayOf("audio/*")) }) {
            Text(stringResource(R.string.dateien_oeffnen))
        }
        Text(
            text = if (titel.isEmpty()) stringResource(R.string.keine_datei) else titel
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { player.seekToPreviousMediaItem() }) {
                Text(stringResource(R.string.zurueck))
            }
            Button(onClick = { if (spielt) player.pause() else player.play() }) {
                Text(
                    if (spielt) stringResource(R.string.pause)
                    else stringResource(R.string.wiedergabe)
                )
            }
            Button(onClick = { player.seekToNextMediaItem() }) {
                Text(stringResource(R.string.weiter))
            }
        }
    }
}

@Composable
private fun MikrofonBereich() {
    val context = LocalContext.current
    val status by MikrofonZustand.status.collectAsState()
    val latenzHinweis by MikrofonZustand.latenzHinweis.collectAsState()
    var berechtigungFehlt by remember { mutableStateOf(false) }

    val berechtigungsAnfrage = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { ergebnis ->
        if (ergebnis[Manifest.permission.RECORD_AUDIO] == true) {
            berechtigungFehlt = false
            context.startForegroundService(Intent(context, MikrofonDienst::class.java))
        } else {
            berechtigungFehlt = true
        }
    }

    Text(
        text = stringResource(R.string.mik_ueberschrift),
        style = MaterialTheme.typography.titleMedium,
    )

    val laeuft = status == MikrofonZustand.Status.LAEUFT
    Button(onClick = {
        if (laeuft) {
            context.startService(
                Intent(context, MikrofonDienst::class.java)
                    .setAction(MikrofonDienst.AKTION_STOPP)
            )
        } else {
            val gewuenscht = mutableListOf(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gewuenscht += Manifest.permission.POST_NOTIFICATIONS
            }
            val fehlend = gewuenscht.filter {
                ContextCompat.checkSelfPermission(context, it) !=
                        PackageManager.PERMISSION_GRANTED
            }
            if (fehlend.isEmpty()) {
                context.startForegroundService(Intent(context, MikrofonDienst::class.java))
            } else {
                berechtigungsAnfrage.launch(fehlend.toTypedArray())
            }
        }
    }) {
        Text(
            if (laeuft) stringResource(R.string.mik_stoppen)
            else stringResource(R.string.mik_starten)
        )
    }

    Text(
        text = when {
            berechtigungFehlt -> stringResource(R.string.mik_berechtigung_fehlt)
            status == MikrofonZustand.Status.LAEUFT ->
                stringResource(R.string.mik_laeuft)
            status == MikrofonZustand.Status.VERWEIGERT_LAUTSPRECHER ->
                stringResource(R.string.mik_nur_lautsprecher)
            status == MikrofonZustand.Status.FEHLER ->
                stringResource(R.string.mik_fehler)
            else -> stringResource(R.string.mik_gestoppt)
        }
    )
    if (laeuft && latenzHinweis) {
        Text(text = stringResource(R.string.mik_latenz_hinweis))
    }
}
