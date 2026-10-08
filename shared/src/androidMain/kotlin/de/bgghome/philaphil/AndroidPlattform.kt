package de.bgghome.philaphil

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import de.bgghome.philaphil.daten.DateiOrdner
import de.bgghome.philaphil.daten.DokumentOrdner
import de.bgghome.philaphil.daten.Ordner
import de.bgghome.philaphil.shared.BuildConfig
import okhttp3.OkHttpClient
import java.io.File

/** Die eine Application der App - der SQLite-Treiber braucht einen Context. */
object AndroidKontext { lateinit var app: Application }

class AndroidAblage(context: Context, name: String) : Ablage {
    private val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    override fun lesen(schluessel: String): String? = prefs.getString(schluessel, null)
    override fun schreiben(schluessel: String, wert: String?) { prefs.edit().apply { if (wert == null) remove(schluessel) else putString(schluessel, wert) }.apply() }
}

class AndroidPlattform(private val app: Application) : Plattform {
    init { AndroidKontext.app = app }
    override val versionName: String = BuildConfig.VERSION_NAME
    override val datenOrdner: File get() = File(app.filesDir, "philaphil").apply { mkdirs() }
    override val cacheOrdner: File get() = app.cacheDir
    override val einstellungen: Ablage = AndroidAblage(app, "einstellungen")

    /** Gewaehlter Ordner (Dokumentensystem, z. B. ein Sync-Ordner), sonst Android/data/…/files/Sammlung (per USB erreichbar). */
    override fun sammlungsordner(): Ordner {
        val wahl = einstellungen.lesen(EINSTELLUNG_SAMMLUNG)
        if (wahl != null && wahl.startsWith("content://")) {
            val uri = Uri.parse(wahl)
            val erlaubt = app.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
            if (erlaubt) return DokumentOrdner(app, uri)
        }
        return DateiOrdner((app.getExternalFilesDir("Sammlung") ?: File(app.filesDir, "Sammlung")).apply { mkdirs() })
    }

    override val http: OkHttpClient = httpClient(versionName, "Android ${Build.VERSION.RELEASE}")
    override fun oeffneWeb(url: String) {
        runCatching { app.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
