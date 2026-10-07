package de.bgghome.philaphil

import okhttp3.OkHttpClient
import java.io.File
import java.net.URI

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
    override val http: OkHttpClient = httpClient(versionName, system)
    override fun oeffneWeb(url: String) {
        runCatching { java.awt.Desktop.getDesktop().browse(URI(url)) }
            .onFailure { runCatching { ProcessBuilder("xdg-open", url).start() } }
    }
}
