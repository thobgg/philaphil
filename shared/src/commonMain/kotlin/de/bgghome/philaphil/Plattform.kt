package de.bgghome.philaphil

import de.bgghome.philaphil.daten.Ordner
import okhttp3.OkHttpClient
import java.io.File

/** Kleine Schluessel-Wert-Ablage fuer Einstellungen (Android: SharedPreferences, Desktop: java.util.prefs). */
interface Ablage {
    fun lesen(schluessel: String): String?
    fun schreiben(schluessel: String, wert: String?)
}

/** Was die Huelle (Android-App, Desktop-Fenster) dem gemeinsamen Kern mitgibt. */
interface Plattform {
    val versionName: String
    /** Datenordner der App: Katalog-Datenbank, lokaler Bestands-Zwischenspeicher, Commons-Angaben. */
    val datenOrdner: File
    /** Cache-Ordner: geladene Bilder (darf jederzeit geleert werden). */
    val cacheOrdner: File
    val einstellungen: Ablage
    /**
     * Der Sammlungsordner (bestand.json, Bilder/): aus der Einstellung "sammlung" (Pfad bzw. Android-Ordneradresse),
     * sonst die Vorgabe. Er darf ein Sync-Ordner zum NAS sein.
     */
    fun sammlungsordner(): Ordner
    /** Ein HTTP-Client fuer Wikipedia und Commons - mit User-Agent, wie Wikimedia es verlangt. */
    val http: OkHttpClient
    /** true, wenn das Netz nicht nach Volumen zaehlt (WLAN, LAN) - fuers Vorladen der Bilder. */
    fun unbegrenztesNetz(): Boolean = true
    /** Oeffnet eine Adresse im Browser des Systems (Wikipedia, Commons). */
    fun oeffneWeb(url: String)
}

const val APP_NAME = "PhilaPhil"
const val EINSTELLUNG_SAMMLUNG = "sammlung"

/** Wikimedia verlangt einen aussagekraeftigen User-Agent mit Kontaktmoeglichkeit. */
fun userAgent(versionName: String, system: String) =
    "$APP_NAME/$versionName ($system; https://github.com/thobgg/philaphil) OkHttp"

fun httpClient(versionName: String, system: String): OkHttpClient {
    val ua = userAgent(versionName, system)
    return OkHttpClient.Builder()
        .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", ua).build()) }
        .build()
}
