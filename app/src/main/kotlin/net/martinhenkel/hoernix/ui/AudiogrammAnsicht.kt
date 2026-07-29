package net.martinhenkel.hoernix.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import net.martinhenkel.hoernix.R
import net.martinhenkel.hoernix.profil.ProfilGrenzen
import net.martinhenkel.hoernix.profil.ProfilVerwaltung

/**
 * Audiogramm-Maske (Plan Kap. 2.6): Hörverlust in dB je Audiometrie-Frequenz
 * je Ohr; „übernehmen" belegt die Bandverstärkungen nach der
 * Halbverstärkungsregel vor (halber Hörverlust, gedeckelt auf +24 dB).
 */
@Composable
fun AudiogrammAnsicht() {
    val bestand by ProfilVerwaltung.bestand.collectAsState()
    val profil = bestand.aktives()

    val links = remember(profil.name) {
        vorbelegung(profil.audiogrammLinksDb).toMutableStateList()
    }
    val rechts = remember(profil.name) {
        vorbelegung(profil.audiogrammRechtsDb).toMutableStateList()
    }
    var meldung by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.audiogramm_erklaerung),
            style = MaterialTheme.typography.bodySmall,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.audiogramm_frequenz),
                modifier = Modifier.width(72.dp),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(R.string.seite_links),
                modifier = Modifier.weight(1.0f),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(R.string.seite_rechts),
                modifier = Modifier.weight(1.0f),
                style = MaterialTheme.typography.titleSmall,
            )
        }

        bandBeschriftungen.forEachIndexed { index, beschriftung ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.band_hz, beschriftung),
                    modifier = Modifier.width(72.dp),
                )
                AudiogrammFeld(links[index], Modifier.weight(1.0f)) { links[index] = it }
                AudiogrammFeld(rechts[index], Modifier.weight(1.0f)) { rechts[index] = it }
            }
        }

        val textUebernommen = stringResource(R.string.audiogramm_uebernommen)
        val textFehler = stringResource(R.string.audiogramm_eingabe_fehler)
        Button(onClick = {
            meldung = if (uebernimm(links.toList(), rechts.toList())) {
                textUebernommen
            } else {
                textFehler
            }
        }) {
            Text(stringResource(R.string.audiogramm_uebernehmen))
        }
        if (meldung.isNotEmpty()) {
            Text(meldung)
        }
    }
}

@Composable
private fun AudiogrammFeld(
    wert: String,
    modifier: Modifier,
    aufAenderung: (String) -> Unit,
) {
    OutlinedTextField(
        value = wert,
        onValueChange = { neu ->
            if (neu.length <= 3 && neu.all { it.isDigit() }) {
                aufAenderung(neu)
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = modifier,
    )
}

private fun vorbelegung(gespeichert: List<Float>?): List<String> =
    List(ProfilGrenzen.ANZAHL_BAENDER) { index ->
        gespeichert?.getOrNull(index)?.let { "${it.toInt()}" } ?: ""
    }

/** true = übernommen; false = ungültige Eingabe. */
private fun uebernimm(links: List<String>, rechts: List<String>): Boolean {
    val linksWerte = parseSpalte(links) ?: return false
    val rechtsWerte = parseSpalte(rechts) ?: return false

    ProfilVerwaltung.aendereAktives { profil ->
        profil.copy(
            gekoppelt = false,
            links = profil.links.copy(baenderDb = linksWerte.map(::halbverstaerkung)),
            rechts = profil.rechts.copy(baenderDb = rechtsWerte.map(::halbverstaerkung)),
            audiogrammLinksDb = linksWerte,
            audiogrammRechtsDb = rechtsWerte,
        )
    }
    return true
}

/** Leere Felder zählen als 0 dB Hörverlust; null = ungültige Eingabe. */
private fun parseSpalte(spalte: List<String>): List<Float>? =
    spalte.map { feld ->
        if (feld.isEmpty()) {
            0.0f
        } else {
            val wert = feld.toIntOrNull() ?: return null
            if (wert.toFloat() !in
                ProfilGrenzen.AUDIOGRAMM_MIN_DB..ProfilGrenzen.AUDIOGRAMM_MAX_DB
            ) {
                return null
            }
            wert.toFloat()
        }
    }

private fun halbverstaerkung(verlustDb: Float): Float =
    (verlustDb / 2.0f).coerceAtMost(ProfilGrenzen.BAND_MAX_DB)
