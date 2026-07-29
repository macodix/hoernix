package net.martinhenkel.hoernix.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.martinhenkel.hoernix.R
import net.martinhenkel.hoernix.profil.ProfilGrenzen
import net.martinhenkel.hoernix.profil.ProfilVerwaltung
import kotlin.math.roundToInt

/** EQ-Ansicht (Plan Kap. 2.6): 11 Regler je Ohr, links/rechts/gekoppelt. */
@Composable
fun EqAnsicht() {
    val bestand by ProfilVerwaltung.bestand.collectAsState()
    val profil = bestand.aktives()
    var seiteIndex by rememberSaveable {
        mutableStateOf(if (profil.gekoppelt) OhrSeite.GEKOPPELT.ordinal else OhrSeite.LINKS.ordinal)
    }
    var koppelFrage by remember { mutableStateOf(false) }
    val seite = OhrSeite.entries[seiteIndex]

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OhrWahl(
            profil = profil,
            seite = seite,
            koppelFrage = koppelFrage,
            setzeSeite = { seiteIndex = it.ordinal },
            setzeKoppelFrage = { koppelFrage = it },
        )
        Text(
            text = stringResource(R.string.eq_hinweis),
            style = MaterialTheme.typography.bodySmall,
        )

        val werte = profil.ohr(seite).baenderDb
        bandBeschriftungen.forEachIndexed { index, beschriftung ->
            BandRegler(
                beschriftung = beschriftung,
                wert = werte.getOrElse(index) { 0.0f },
                aufAenderung = { neu ->
                    ProfilVerwaltung.aendereAktives { aktuelles ->
                        aktuelles.mitOhr(seite) { ohr ->
                            ohr.copy(
                                baenderDb = ohr.baenderDb.toMutableList()
                                    .also { it[index] = neu }
                            )
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun BandRegler(
    beschriftung: String,
    wert: Float,
    aufAenderung: (Float) -> Unit,
) {
    // Lokaler Wert während des Ziehens; Übernahme ins Profil beim Loslassen
    // (vermeidet Speichern bei jeder Reglerbewegung).
    var zieht by remember { mutableStateOf(false) }
    var lokal by remember { mutableStateOf(wert) }
    val angezeigt = if (zieht) lokal else wert

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.band_hz, beschriftung),
            modifier = Modifier.width(72.dp),
        )
        Slider(
            value = angezeigt,
            onValueChange = {
                zieht = true
                lokal = it.roundToInt().toFloat()
            },
            onValueChangeFinished = {
                zieht = false
                aufAenderung(lokal)
            },
            valueRange = ProfilGrenzen.BAND_MIN_DB..ProfilGrenzen.BAND_MAX_DB,
            steps = 47,
            modifier = Modifier
                .weight(1.0f)
                .fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.wert_db, angezeigt.roundToInt()),
            modifier = Modifier.width(64.dp),
        )
    }
}
