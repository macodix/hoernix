package net.martinhenkel.hoernix.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.martinhenkel.hoernix.R
import net.martinhenkel.hoernix.profil.PruefErgebnis
import net.martinhenkel.hoernix.profil.ProfilVerwaltung

/** Profil-Ansicht (Plan Kap. 2.6): Liste, anlegen/umbenennen/löschen/duplizieren, Export/Import. */
@Composable
fun ProfileAnsicht() {
    val context = LocalContext.current
    val bestand by ProfilVerwaltung.bestand.collectAsState()

    var meldung by remember { mutableStateOf("") }
    var nameDialog by remember { mutableStateOf<String?>(null) } // null=zu, ""=neu, sonst=umbenennen
    var loeschDialog by remember { mutableStateOf<String?>(null) }
    var exportName by remember { mutableStateOf("") }

    val textImportiert = stringResource(R.string.profil_importiert)
    val textExportiert = stringResource(R.string.profil_exportiert)
    val textFehlgeschlagen = stringResource(R.string.profil_aktion_fehlgeschlagen)

    val exportZiel = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            meldung = if (ProfilVerwaltung.exportiere(context.contentResolver, uri, exportName)) {
                textExportiert
            } else {
                textFehlgeschlagen
            }
        }
    }
    val importQuelle = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            meldung = when (val ergebnis =
                ProfilVerwaltung.importiere(context.contentResolver, uri)) {
                is PruefErgebnis.Gueltig -> textImportiert
                is PruefErgebnis.Abgewiesen -> ergebnis.grund
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { nameDialog = "" }) {
                Text(stringResource(R.string.profil_neu))
            }
            OutlinedButton(onClick = { importQuelle.launch(arrayOf("*/*")) }) {
                Text(stringResource(R.string.profil_importieren))
            }
        }
        if (meldung.isNotEmpty()) {
            Text(meldung)
        }

        bestand.profile.forEach { profil ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = if (profil.name == bestand.aktivName) {
                            stringResource(R.string.profil_aktiv_markierung, profil.name)
                        } else {
                            profil.name
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (profil.name != bestand.aktivName) {
                            OutlinedButton(onClick = {
                                ProfilVerwaltung.aktiviere(profil.name)
                            }) { Text(stringResource(R.string.profil_aktivieren)) }
                        }
                        OutlinedButton(onClick = { nameDialog = profil.name }) {
                            Text(stringResource(R.string.profil_umbenennen))
                        }
                        OutlinedButton(onClick = {
                            ProfilVerwaltung.dupliziere(profil.name)
                        }) { Text(stringResource(R.string.profil_duplizieren)) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            exportName = profil.name
                            exportZiel.launch("${profil.name}.json")
                        }) { Text(stringResource(R.string.profil_exportieren)) }
                        if (bestand.profile.size > 1) {
                            OutlinedButton(onClick = { loeschDialog = profil.name }) {
                                Text(stringResource(R.string.profil_loeschen))
                            }
                        }
                    }
                }
            }
        }
    }

    nameDialog?.let { vorhandener ->
        NamensDialog(
            titel = if (vorhandener.isEmpty()) {
                stringResource(R.string.profil_neu)
            } else {
                stringResource(R.string.profil_umbenennen)
            },
            start = vorhandener,
            aufBestaetigung = { name ->
                val erfolg = if (vorhandener.isEmpty()) {
                    ProfilVerwaltung.legeAn(name)
                } else {
                    ProfilVerwaltung.benenneUm(vorhandener, name)
                }
                if (erfolg) {
                    nameDialog = null
                } else {
                    meldung = textFehlgeschlagen
                }
            },
            aufAbbruch = { nameDialog = null },
        )
    }

    loeschDialog?.let { name ->
        AlertDialog(
            onDismissRequest = { loeschDialog = null },
            title = { Text(stringResource(R.string.profil_loeschen)) },
            text = { Text(stringResource(R.string.profil_loeschen_frage, name)) },
            confirmButton = {
                TextButton(onClick = {
                    ProfilVerwaltung.loesche(name)
                    loeschDialog = null
                }) { Text(stringResource(R.string.profil_loeschen)) }
            },
            dismissButton = {
                TextButton(onClick = { loeschDialog = null }) {
                    Text(stringResource(R.string.abbrechen))
                }
            },
        )
    }
}

@Composable
private fun NamensDialog(
    titel: String,
    start: String,
    aufBestaetigung: (String) -> Unit,
    aufAbbruch: () -> Unit,
) {
    var name by remember { mutableStateOf(start) }
    AlertDialog(
        onDismissRequest = aufAbbruch,
        title = { Text(titel) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.profil_name)) },
            )
        },
        confirmButton = {
            TextButton(onClick = { aufBestaetigung(name) }) {
                Text(stringResource(R.string.uebernehmen))
            }
        },
        dismissButton = {
            TextButton(onClick = aufAbbruch) {
                Text(stringResource(R.string.abbrechen))
            }
        },
    )
}
