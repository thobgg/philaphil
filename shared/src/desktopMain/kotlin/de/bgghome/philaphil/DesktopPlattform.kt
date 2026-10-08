package de.bgghome.philaphil

import de.bgghome.philaphil.daten.DateiOrdner
import de.bgghome.philaphil.daten.Ordner
import okhttp3.OkHttpClient
import java.io.File
import java.net.URI
import java.util.prefs.Preferences

class DesktopAblage(name: String) : Ablage {
    private val prefs: Preferences = Preferences.userRoot().node("de/bgghome/philaphil/$name")
    override fun lesen(schluessel: String): String? = prefs.get(schluessel, null)
    override fun schreiben(schluessel: String, wert: String?) { if (wert == null) prefs.remove(schluessel) else prefs.put(schluessel, wert) }
}

/** Linux/Windows: Daten nach XDG bzw. %APPDATA%, Cache getrennt davon. */
class DesktopPlattform : Plattform {
    override val versionName: String = System.getProperty("philaphil.versionName") ?: "dev"
    private val system = System.getProperty("os.name").orEmpty()

    override val datenOrdner: File by lazy {
        val basis = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }?.let(::File)
            ?: System.getenv("APPDATA")?.takeIf { it.isNotBlank() }?.let(::File)
            ?: File(System.getProperty("user.home"), ".local/share")
        File(basis, "philaphil").apply { mkdirs() }
    }
    override val cacheOrdner: File by lazy {
        val basis = System.getenv("XDG_CACHE_HOME")?.takeIf { it.isNotBlank() }?.let(::File)
            ?: System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }?.let(::File)
            ?: File(System.getProperty("user.home"), ".cache")
        File(basis, "philaphil").apply { mkdirs() }
    }
    override val einstellungen: Ablage = DesktopAblage("einstellungen")

    /** Gewaehlter Ordner (gern ein Sync-Ordner zum NAS), sonst ~/PhilaPhil. */
    override fun sammlungsordner(): Ordner {
        val wahl = einstellungen.lesen(EINSTELLUNG_SAMMLUNG)?.let(::File)
        if (wahl != null && (wahl.isDirectory || wahl.mkdirs())) return DateiOrdner(wahl)
        return DateiOrdner(File(System.getProperty("user.home"), "PhilaPhil").apply { mkdirs() })
    }

    override val http: OkHttpClient = httpClient(versionName, system)
    override fun oeffneWeb(url: String) {
        runCatching { java.awt.Desktop.getDesktop().browse(URI(url)) }
            .onFailure { runCatching { ProcessBuilder("xdg-open", url).start() } }
    }
}
