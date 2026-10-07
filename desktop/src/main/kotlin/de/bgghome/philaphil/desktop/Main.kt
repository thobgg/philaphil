package de.bgghome.philaphil.desktop

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
    SingletonImageLoader.setSafe { context -> philaImageLoader(context, plattform) }

    application {
        val viewModel = remember { AppViewModel(plattform, startMiNr) }
        Window(
            onCloseRequest = ::exitApplication,
            title = APP_NAME,
            state = WindowState(size = DpSize(1200.dp, 800.dp)),
        ) {
            PhilaTheme { AppRoot(viewModel) }
        }
    }
}
