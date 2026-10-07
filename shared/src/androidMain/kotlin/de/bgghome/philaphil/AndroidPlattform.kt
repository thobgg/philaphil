package de.bgghome.philaphil

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import de.bgghome.philaphil.shared.BuildConfig
import okhttp3.OkHttpClient
import java.io.File

/** Die eine Application der App - der SQLite-Treiber braucht einen Context. */
object AndroidKontext { lateinit var app: Application }

class AndroidPlattform(private val app: Application) : Plattform {
    init { AndroidKontext.app = app }
    override val versionName: String = BuildConfig.VERSION_NAME
    override val datenOrdner: File get() = File(app.filesDir, "philaphil").apply { mkdirs() }
    override val cacheOrdner: File get() = app.cacheDir
    override val http: OkHttpClient = httpClient(versionName, "Android ${Build.VERSION.RELEASE}")
    override fun oeffneWeb(url: String) {
        runCatching { app.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
