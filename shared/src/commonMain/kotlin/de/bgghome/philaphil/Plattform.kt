package de.bgghome.philaphil

import okhttp3.OkHttpClient
import java.io.File

/** Was die Huelle (Android-App, Desktop-Fenster) dem gemeinsamen Kern mitgibt. */
interface Plattform {
    val versionName: String
    /** Datenordner: Katalog-Datenbank, Commons-Zwischenspeicher, spaeter der eigene Bestand. */
    val datenOrdner: File
    /** Cache-Ordner: geladene Bilder (darf jederzeit geleert werden). */
    val cacheOrdner: File
    /** Ein HTTP-Client fuer Wikipedia und Commons - mit User-Agent, wie Wikimedia es verlangt. */
    val http: OkHttpClient
    /** Oeffnet eine Adresse im Browser des Systems (Wikipedia, Commons). */
    fun oeffneWeb(url: String)
}

const val APP_NAME = "PhilaPhil"

/** Wikimedia verlangt einen aussagekraeftigen User-Agent mit Kontaktmoeglichkeit. */
fun userAgent(versionName: String, system: String) =
    "$APP_NAME/$versionName ($system; https://github.com/thobgg/philaphil) OkHttp"

fun httpClient(versionName: String, system: String): OkHttpClient {
    val ua = userAgent(versionName, system)
    return OkHttpClient.Builder()
        .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", ua).build()) }
        .build()
}
