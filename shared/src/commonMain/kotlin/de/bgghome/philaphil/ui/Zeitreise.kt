package de.bgghome.philaphil.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.bgghome.philaphil.daten.datumLesbar
import de.bgghome.philaphil.res.Res
import de.bgghome.philaphil.res.zeitreise_quelle
import de.bgghome.philaphil.res.zeitreise_titel
import org.jetbrains.compose.resources.stringResource

/**
 * Zeitreise: was im Jahr des Jahrgangs geschah - die Einleitung des Jahresartikels und eine Auswahl von
 * Ereignissen. Ereignisse mit Stern beruehren ein Thema einer Marke dieses Jahres und oeffnen es.
 */
@Composable
fun Zeitreise(zustand: AppZustand, viewModel: AppViewModel, mitTitel: Boolean = false) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        item {
            Spacer(Modifier.height(16.dp))
            if (mitTitel) {
                Text(stringResource(Res.string.zeitreise_titel, zustand.jahr.toString()), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
            }
            zustand.jahrInfo?.einleitung?.let {
                Text(it, style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic)
                Spacer(Modifier.height(12.dp))
            }
        }
        items(zustand.ereignisse, key = { it.id }) { e ->
            val oeffnet = e.thema_id
            Column(Modifier.fillMaxWidth().then(if (oeffnet != null) Modifier.clickable { viewModel.themaOeffnen(oeffnet) } else Modifier).padding(vertical = 8.dp)) {
                Row {
                    Text(e.datum?.let { datumLesbar(it).removeSuffix(" " + zustand.jahr) } ?: zustand.jahr.toString(),
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    e.rubrik?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.width(8.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (oeffnet != null) {
                        Spacer(Modifier.width(8.dp))
                        Text("★ " + e.bezug.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                    }
                }
                Text(e.text, style = MaterialTheme.typography.bodyLarge)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        item {
            Text(stringResource(Res.string.zeitreise_quelle, zustand.jahr.toString()), Modifier.padding(vertical = 16.dp).clickable {
                zustand.jahrInfo?.quelle_url?.let(viewModel::oeffneWeb)
            }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
