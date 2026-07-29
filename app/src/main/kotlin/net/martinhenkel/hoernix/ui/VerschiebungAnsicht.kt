package net.martinhenkel.hoernix.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.martinhenkel.hoernix.R
import net.martinhenkel.hoernix.profil.OhrEinstellung
import net.martinhenkel.hoernix.profil.ProfilVerwaltung
import kotlin.math.roundToInt

/**
 * Verschiebungs-Ansicht (Plan Kap. 2.6): je Ohr Kompression (Grenzfrequenz,
 * Verhältnis) und Transposition (Quellbereich, Versatz), getrennt schaltbar.
 */
@Composable
fun VerschiebungAnsicht() {
    val bestand by ProfilVerwaltung.bestand.collectAsState()
    val profil = bestand.aktives()
    var seiteIndex by rememberSaveable {
        mutableStateOf(if (profil.gekoppelt) OhrSeite.GEKOPPELT.ordinal else OhrSeite.LINKS.ordinal)
    }
    var koppelFrage by remember { mutableStateOf(false) }
    val seite = OhrSeite.entries[seiteIndex]
    val ohr = profil.ohr(seite)

    fun aendere(aenderung: (OhrEinstellung) -> OhrEinstellung) {
        ProfilVerwaltung.aendereAktives { it.mitOhr(seite, aenderung) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OhrWahl(
            profil = profil,
            seite = seite,
            koppelFrage = koppelFrage,
            setzeSeite = { seiteIndex = it.ordinal },
            setzeKoppelFrage = { koppelFrage = it },
        )

        Text(
            text = stringResource(R.string.kompression_ueberschrift),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.kompression_erklaerung),
            style = MaterialTheme.typography.bodySmall,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Switch(
                checked = ohr.kompressionAktiv,
                onCheckedChange = { neu -> aendere { it.copy(kompressionAktiv = neu) } },
            )
            Text(stringResource(R.string.kompression_aktiv))
        }
        WertRegler(
            beschriftung = stringResource(
                R.string.grenzfrequenz_wert, ohr.grenzFrequenzHz.roundToInt()
            ),
            wert = ohr.grenzFrequenzHz,
            bereich = 500.0f..8000.0f,
            schritt = 250.0f,
            aufUebernahme = { neu -> aendere { it.copy(grenzFrequenzHz = neu) } },
        )
        WertRegler(
            beschriftung = stringResource(
                R.string.verhaeltnis_wert, formatiereVerhaeltnis(ohr.verhaeltnis)
            ),
            wert = ohr.verhaeltnis,
            bereich = 1.0f..4.0f,
            schritt = 0.5f,
            aufUebernahme = { neu -> aendere { it.copy(verhaeltnis = neu) } },
        )

        Text(
            text = stringResource(R.string.transposition_ueberschrift),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.transposition_erklaerung),
            style = MaterialTheme.typography.bodySmall,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Switch(
                checked = ohr.transpositionAktiv,
                onCheckedChange = { neu -> aendere { it.copy(transpositionAktiv = neu) } },
            )
            Text(stringResource(R.string.transposition_aktiv))
        }
        WertRegler(
            beschriftung = stringResource(
                R.string.quelle_von_wert, ohr.quelleVonHz.roundToInt()
            ),
            wert = ohr.quelleVonHz,
            bereich = 1000.0f..12000.0f,
            schritt = 500.0f,
            aufUebernahme = { neu ->
                aendere {
                    it.copy(
                        quelleVonHz = neu,
                        quelleBisHz = maxOf(it.quelleBisHz, neu + 500.0f),
                        versatzHz = minOf(it.versatzHz, neu),
                    )
                }
            },
        )
        WertRegler(
            beschriftung = stringResource(
                R.string.quelle_bis_wert, ohr.quelleBisHz.roundToInt()
            ),
            wert = ohr.quelleBisHz,
            bereich = 1500.0f..16000.0f,
            schritt = 500.0f,
            aufUebernahme = { neu ->
                aendere { it.copy(quelleBisHz = maxOf(neu, it.quelleVonHz + 500.0f)) }
            },
        )
        WertRegler(
            beschriftung = stringResource(
                R.string.versatz_wert, ohr.versatzHz.roundToInt()
            ),
            wert = ohr.versatzHz,
            bereich = 500.0f..8000.0f,
            schritt = 500.0f,
            aufUebernahme = { neu ->
                aendere { it.copy(versatzHz = minOf(neu, it.quelleVonHz)) }
            },
        )
    }
}

private fun formatiereVerhaeltnis(wert: Float): String {
    val zehntel = (wert * 10.0f).roundToInt()
    return if (zehntel % 10 == 0) {
        "${zehntel / 10}"
    } else {
        "${zehntel / 10},${zehntel % 10}"
    }
}

/** Regler mit Rastschritten; Übernahme ins Profil beim Loslassen. */
@Composable
private fun WertRegler(
    beschriftung: String,
    wert: Float,
    bereich: ClosedFloatingPointRange<Float>,
    schritt: Float,
    aufUebernahme: (Float) -> Unit,
) {
    var zieht by remember { mutableStateOf(false) }
    var lokal by remember { mutableStateOf(wert) }
    val angezeigt = if (zieht) lokal else wert.coerceIn(bereich)
    val schritte =
        (((bereich.endInclusive - bereich.start) / schritt).roundToInt() - 1)
            .coerceAtLeast(0)

    Column {
        Text(beschriftung)
        Slider(
            value = angezeigt,
            onValueChange = {
                zieht = true
                lokal = ((it - bereich.start) / schritt).roundToInt() * schritt +
                        bereich.start
            },
            onValueChangeFinished = {
                zieht = false
                aufUebernahme(lokal)
            },
            valueRange = bereich,
            steps = schritte,
        )
    }
}
