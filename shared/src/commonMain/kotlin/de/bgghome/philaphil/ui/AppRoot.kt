package de.bgghome.philaphil.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import de.bgghome.philaphil.daten.datumLesbar
import de.bgghome.philaphil.daten.wertLesbar
import de.bgghome.philaphil.db.Marke
import de.bgghome.philaphil.res.Res
import de.bgghome.philaphil.res.app_titel
import de.bgghome.philaphil.res.fehler_laden
import de.bgghome.philaphil.res.jahrgang_titel
import de.bgghome.philaphil.res.jahrgang_untertitel
import de.bgghome.philaphil.res.jahrgang_vor
import de.bgghome.philaphil.res.jahrgang_zurueck
import de.bgghome.philaphil.res.keine_treffer
import de.bgghome.philaphil.res.suche_hinweis
import de.bgghome.philaphil.res.suche_loeschen
import de.bgghome.philaphil.res.suche_treffer
import de.bgghome.philaphil.res.zurueck
import org.jetbrains.compose.resources.stringResource

/** Ab dieser Breite (Tablet, Desktop) stehen Liste und Marke nebeneinander. */
private val BREIT_AB = 840.dp

@Composable
fun AppRoot(viewModel: AppViewModel) {
    val zustand by viewModel.zustand.collectAsState()

    BackHandler(enabled = zustand.vollbild || zustand.themaSeite != null || zustand.marke != null || zustand.suchtreffer != null) {
        viewModel.zurueck()
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val breit = maxWidth >= BREIT_AB
        when {
            zustand.vollbild && zustand.marke != null ->
                Bildbetrachter(zustand, viewModel, onClose = { viewModel.vollbild(false) })
            // Handy: Themenseite und Marke sind eigene Seiten mit Zurueck-Pfeil.
            !breit && zustand.themaSeite != null ->
                Seite(zustand.themaSeite!!.thema.titel, onZurueck = viewModel::themaSchliessen) { ThemaSeite(zustand, viewModel) }
            !breit && zustand.marke != null ->
                Seite("MiNr. ${zustand.marke!!.mi_nr}", onZurueck = { viewModel.waehlen(null) }) { MarkenDetail(zustand, viewModel) }
            else -> Scaffold(topBar = { Kopf(zustand, viewModel) }, containerColor = MaterialTheme.colorScheme.background) { innen ->
                Row(Modifier.padding(innen).fillMaxSize()) {
                    Box(if (breit) Modifier.width(420.dp).fillMaxHeight() else Modifier.fillMaxSize()) {
                        Markenliste(zustand, onWahl = viewModel::waehlen)
                    }
                    if (breit) {
                        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            when {
                                zustand.themaSeite != null -> Seite(zustand.themaSeite!!.thema.titel, onZurueck = viewModel::themaSchliessen) { ThemaSeite(zustand, viewModel) }
                                zustand.marke != null -> MarkenDetail(zustand, viewModel)
                                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(stringResource(Res.string.jahrgang_untertitel), style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(32.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Kopfzeile mit Jahrgang und Suchfeld. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Kopf(zustand: AppZustand, viewModel: AppViewModel) {
    Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
        TopAppBar(
            title = {
                Column {
                    Text(stringResource(Res.string.jahrgang_titel, zustand.gebiet, zustand.jahr.toString()))
                    Text(stringResource(Res.string.app_titel), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            actions = {
                // Blaettern durch die Jahrgaenge wie durch ein Geschichtsbuch
                val voriger = zustand.voriger
                val naechster = zustand.naechster
                IconButton(onClick = { voriger?.let(viewModel::jahrgangWaehlen) }, enabled = voriger != null) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(Res.string.jahrgang_zurueck))
                }
                Text(voriger?.jahr?.toString() ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text(naechster?.jahr?.toString() ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(onClick = { naechster?.let(viewModel::jahrgangWaehlen) }, enabled = naechster != null) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(Res.string.jahrgang_vor))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        OutlinedTextField(
            value = zustand.suchtext,
            onValueChange = viewModel::suchen,
            singleLine = true,
            placeholder = { Text(stringResource(Res.string.suche_hinweis)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (zustand.suchtext.isNotEmpty()) IconButton(onClick = { viewModel.suchen("") }) {
                    Icon(Icons.Default.Clear, contentDescription = stringResource(Res.string.suche_loeschen))
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        )
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun Markenliste(zustand: AppZustand, onWahl: (Marke) -> Unit) {
    when {
        zustand.laedt -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        zustand.fehler != null -> Text(stringResource(Res.string.fehler_laden, zustand.fehler), Modifier.padding(24.dp),
            color = MaterialTheme.colorScheme.error)
        zustand.suchtreffer?.isEmpty() == true -> Text(stringResource(Res.string.keine_treffer), Modifier.padding(24.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        else -> LazyColumn(Modifier.fillMaxSize()) {
            if (zustand.suchtreffer != null) {
                item {
                    Text(stringResource(Res.string.suche_treffer, zustand.suchtreffer.size), Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(zustand.liste, key = { it.id }) { marke ->
                MarkenZeile(marke, zustand, gewaehlt = zustand.marke?.id == marke.id, mitJahr = zustand.suchtreffer != null, onClick = { onWahl(marke) })
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
fun MarkenZeile(marke: Marke, zustand: AppZustand, gewaehlt: Boolean, mitJahr: Boolean, onClick: () -> Unit) {
    val hintergrund = if (gewaehlt) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background
    Row(
        Modifier.fillMaxWidth().background(hintergrund).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MarkenBild(zustand.bild(marke)?.vorschauUrl, marke, Modifier.size(width = 72.dp, height = 84.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(marke.anlass.orEmpty(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2)
            if (!marke.bild_beschreibung.isNullOrBlank()) {
                Text(marke.bild_beschreibung, style = MaterialTheme.typography.bodyMedium, maxLines = 2,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            val nummer = (if (mitJahr) "${marke.gebiet} ${marke.jahr} · MiNr. ${marke.mi_nr}" else "MiNr. ${marke.mi_nr}") +
                (marke.block?.let { " · $it" } ?: "")
            Text("$nummer · ${wertLesbar(marke)} · ${datumLesbar(marke.ausgabetag)}",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Vorschau oder Platzhalter (Rahmen mit MiNr.), wenn Commons kein Bild hat. */
@Composable
fun MarkenBild(url: String?, marke: Marke, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Fit) {
    Box(
        modifier.clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = marke.anlass, contentScale = contentScale, modifier = Modifier.fillMaxSize().padding(2.dp))
        } else {
            Text(marke.mi_nr, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Eine Unterseite mit Titel und Zurueck-Pfeil (Handy: ganze Seite, Tablet: rechte Spalte). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Seite(titel: String, onZurueck: () -> Unit, inhalt: @Composable () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titel, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onZurueck) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.zurueck)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innen -> Box(Modifier.padding(innen)) { inhalt() } }
}
