package de.bgghome.philaphil.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import coil3.SingletonImageLoader
import de.bgghome.philaphil.APP_NAME
import de.bgghome.philaphil.DesktopPlattform
import de.bgghome.philaphil.ui.AppRoot
import de.bgghome.philaphil.ui.AppViewModel
import de.bgghome.philaphil.ui.PhilaTheme
import de.bgghome.philaphil.ui.philaImageLoader

/** Linux/Windows: derselbe Kern wie auf Android, in einem Fenster. */
fun main(args: Array<String>) {
    val plattform = DesktopPlattform()
    // --minr 1031: diese Marke gleich oeffnen
    val startMiNr = args.toList().zipWithNext().firstOrNull { it.first == "--minr" }?.second
    // --vorladen: Vorschaubilder gleich nach dem Start laden (zum Testen ohne Klick)
    val vorladen = "--vorladen" in args
    // --heute: gleich mit der Seite "Heute vor Jahren" starten
    val heute = "--heute" in args
    // --einstellungen: gleich mit geoeffneten Einstellungen starten (zum Testen)
    val einstellungen = "--einstellungen" in args
    // --jahr 1989: mit diesem Jahrgang starten (Gebiet aus --gebiet, sonst Bund)
    val startJahr = args.toList().zipWithNext().firstOrNull { it.first == "--jahr" }?.second?.toLongOrNull()
    // --gebiet Reich: mit diesem Sammelgebiet starten (zum Testen)
    val startGebiet = args.toList().zipWithNext().firstOrNull { it.first == "--gebiet" }?.second
    SingletonImageLoader.setSafe { context -> philaImageLoader(context, plattform) }

    application {
        val viewModel = remember { AppViewModel(plattform, startMiNr, startGebiet, startJahr) }
        Window(
            onCloseRequest = ::exitApplication,
            title = APP_NAME,
            state = WindowState(size = DpSize(1200.dp, 800.dp)),
        ) {
            val context = coil3.compose.LocalPlatformContext.current
            val zustand by viewModel.zustand.collectAsState()
            LaunchedEffect(zustand.laedt) {
                if (vorladen && !zustand.laedt) viewModel.vorladen(context)
                if (heute && !zustand.laedt) viewModel.heuteOeffnen()
                if (einstellungen && !zustand.laedt) viewModel.einstellungen(true)
            }
            LaunchedEffect(zustand.vorladenMeldung) { if (vorladen) zustand.vorladenMeldung?.let { println("Vorladen: $it") } }
            PhilaTheme { AppRoot(viewModel) }
        }
    }
}
