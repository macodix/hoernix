package net.martinhenkel.hoernix

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import net.martinhenkel.hoernix.audio.HoernixAudioProcessor

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PlayerAnsicht() }
    }
}

@OptIn(UnstableApi::class)
private fun erzeugePlayer(
    context: Context,
    prozessor: HoernixAudioProcessor,
): ExoPlayer {
    val fabrik = object : DefaultRenderersFactory(context) {
        override fun buildAudioSink(
            context: Context,
            enableFloatOutput: Boolean,
            enableAudioTrackPlaybackParams: Boolean,
        ): AudioSink {
            return DefaultAudioSink.Builder(context)
                .setAudioProcessors(arrayOf(prozessor))
                .build()
        }
    }
    return ExoPlayer.Builder(context, fabrik).build()
}

@Composable
private fun PlayerAnsicht() {
    val context = LocalContext.current
    val prozessor = remember { HoernixAudioProcessor() }
    val player = remember { erzeugePlayer(context, prozessor) }
    DisposableEffect(Unit) { onDispose { player.release() } }

    var titel by remember { mutableStateOf("") }
    var spielt by remember { mutableStateOf(false) }
    var testprofil by remember { mutableStateOf(false) }

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

    MaterialTheme {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = stringResource(R.string.app_name),
                 style = MaterialTheme.typography.headlineMedium)
            Text(text = stringResource(R.string.dsp_version, DspBruecke.version()))

            Button(onClick = { dateiWahl.launch(arrayOf("audio/*")) }) {
                Text(stringResource(R.string.dateien_oeffnen))
            }

            Text(
                text = if (titel.isEmpty()) {
                    stringResource(R.string.keine_datei)
                } else {
                    titel
                }
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

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Switch(
                    checked = testprofil,
                    onCheckedChange = {
                        testprofil = it
                        prozessor.setzeTestprofil(it)
                    },
                )
                Text(stringResource(R.string.testverarbeitung))
            }
        }
    }
}
