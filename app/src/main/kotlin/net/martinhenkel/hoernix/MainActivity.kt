package net.martinhenkel.hoernix

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import net.martinhenkel.hoernix.audio.HoernixAudioProcessor
import net.martinhenkel.hoernix.profil.ProfilVerwaltung
import net.martinhenkel.hoernix.ui.AudiogrammAnsicht
import net.martinhenkel.hoernix.ui.EqAnsicht
import net.martinhenkel.hoernix.ui.ProfileAnsicht
import net.martinhenkel.hoernix.ui.StartAnsicht
import net.martinhenkel.hoernix.ui.VerschiebungAnsicht

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ProfilVerwaltung.initialisiere(applicationContext)
        setContent { HoernixApp() }
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

private val reiterTitel = listOf(
    R.string.reiter_start,
    R.string.reiter_eq,
    R.string.reiter_verschiebung,
    R.string.reiter_audiogramm,
    R.string.reiter_profile,
)

@Composable
private fun HoernixApp() {
    val context = LocalContext.current
    val prozessor = remember { HoernixAudioProcessor() }
    val player = remember { erzeugePlayer(context, prozessor) }
    DisposableEffect(Unit) { onDispose { player.release() } }

    var reiter by rememberSaveable { mutableStateOf(0) }

    MaterialTheme {
        Column(modifier = Modifier.fillMaxSize()) {
            ScrollableTabRow(selectedTabIndex = reiter) {
                reiterTitel.forEachIndexed { index, titelRes ->
                    Tab(
                        selected = reiter == index,
                        onClick = { reiter = index },
                        text = { Text(stringResource(titelRes)) },
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                when (reiter) {
                    0 -> StartAnsicht(player)
                    1 -> EqAnsicht()
                    2 -> VerschiebungAnsicht()
                    3 -> AudiogrammAnsicht()
                    else -> ProfileAnsicht()
                }
            }
        }
    }
}
