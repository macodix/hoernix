package net.martinhenkel.hoernix.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import net.martinhenkel.hoernix.R
import net.martinhenkel.hoernix.profil.OhrEinstellung
import net.martinhenkel.hoernix.profil.Profil
import net.martinhenkel.hoernix.profil.ProfilVerwaltung

/** Beschriftungen der 11 Audiometrie-Bänder (siehe dsp/parameter.h). */
val bandBeschriftungen = listOf(
    "125", "250", "500", "750", "1000", "1500", "2000", "3000", "4000", "6000", "8000"
)

enum class OhrSeite { LINKS, RECHTS, GEKOPPELT }

fun Profil.ohr(seite: OhrSeite): OhrEinstellung =
    if (seite == OhrSeite.RECHTS) rechts else links

/** Wendet [aenderung] auf die gewählte Seite an; gekoppelt = beide Ohren. */
fun Profil.mitOhr(seite: OhrSeite, aenderung: (OhrEinstellung) -> OhrEinstellung): Profil =
    when (seite) {
        OhrSeite.LINKS -> copy(links = aenderung(links))
        OhrSeite.RECHTS -> copy(rechts = aenderung(rechts))
        OhrSeite.GEKOPPELT -> copy(links = aenderung(links), rechts = aenderung(rechts))
    }

/**
 * Seitenwahl Links/Rechts/Gekoppelt. Der Wechsel auf „Gekoppelt" fragt die
 * Übernahmerichtung ab (links→beide oder rechts→beide, Plan Kap. 2.6) und
 * setzt den Koppel-Zustand im Profil; die Abwahl entkoppelt.
 */
@Composable
fun OhrWahl(
    profil: Profil,
    seite: OhrSeite,
    koppelFrage: Boolean,
    setzeSeite: (OhrSeite) -> Unit,
    setzeKoppelFrage: (Boolean) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        OhrSeite.entries.forEachIndexed { index, kandidat ->
            SegmentedButton(
                selected = seite == kandidat,
                onClick = {
                    if (kandidat == OhrSeite.GEKOPPELT && !profil.gekoppelt) {
                        setzeKoppelFrage(true)
                    } else {
                        if (kandidat != OhrSeite.GEKOPPELT && profil.gekoppelt) {
                            ProfilVerwaltung.aendereAktives { it.copy(gekoppelt = false) }
                        }
                        setzeSeite(kandidat)
                    }
                },
                shape = SegmentedButtonDefaults.itemShape(index, OhrSeite.entries.size),
            ) {
                Text(
                    when (kandidat) {
                        OhrSeite.LINKS -> stringResource(R.string.seite_links)
                        OhrSeite.RECHTS -> stringResource(R.string.seite_rechts)
                        OhrSeite.GEKOPPELT -> stringResource(R.string.seite_gekoppelt)
                    }
                )
            }
        }
    }

    if (koppelFrage) {
        AlertDialog(
            onDismissRequest = { setzeKoppelFrage(false) },
            title = { Text(stringResource(R.string.koppeln_titel)) },
            text = { Text(stringResource(R.string.koppeln_frage)) },
            confirmButton = {
                TextButton(onClick = {
                    ProfilVerwaltung.aendereAktives {
                        it.copy(gekoppelt = true, rechts = it.links)
                    }
                    setzeSeite(OhrSeite.GEKOPPELT)
                    setzeKoppelFrage(false)
                }) { Text(stringResource(R.string.koppeln_links_beide)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    ProfilVerwaltung.aendereAktives {
                        it.copy(gekoppelt = true, links = it.rechts)
                    }
                    setzeSeite(OhrSeite.GEKOPPELT)
                    setzeKoppelFrage(false)
                }) { Text(stringResource(R.string.koppeln_rechts_beide)) }
            },
        )
    }
}
