package de.bgghome.philaphil.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.bgghome.philaphil.APP_NAME
import de.bgghome.philaphil.res.Res
import de.bgghome.philaphil.res.einstellungen_titel
import de.bgghome.philaphil.res.neu_einlesen
import de.bgghome.philaphil.res.ordner_hinweis
import de.bgghome.philaphil.res.ordner_standard
import de.bgghome.philaphil.res.ordner_waehlen
import de.bgghome.philaphil.res.sammlungsordner
import de.bgghome.philaphil.res.schliessen
import de.bgghome.philaphil.res.ueber
import org.jetbrains.compose.resources.stringResource

/** Einstellungen: der Sammlungsordner (bestand.json, Bilder/) und ein paar Zeilen ueber die App. */
@Composable
fun Einstellungen(zustand: AppZustand, viewModel: AppViewModel, onClose: () -> Unit) {
    val ordnerWaehlen = rememberOrdnerWaehler { viewModel.sammlungsordnerSetzen(it) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(Res.string.einstellungen_titel)) },
        text = {
            Column {
                Text(stringResource(Res.string.sammlungsordner), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(zustand.sammlungsordner, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Row {
                    OutlinedButton(onClick = ordnerWaehlen) { Text(stringResource(Res.string.ordner_waehlen)) }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { viewModel.bilderNeuEinlesen() }) { Text(stringResource(Res.string.neu_einlesen)) }
                }
                TextButton(onClick = { viewModel.sammlungsordnerSetzen(null) }) { Text(stringResource(Res.string.ordner_standard)) }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(Res.string.ordner_hinweis), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Text(stringResource(Res.string.ueber, APP_NAME, viewModel.plattform.versionName), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(Res.string.schliessen)) } },
    )
}
