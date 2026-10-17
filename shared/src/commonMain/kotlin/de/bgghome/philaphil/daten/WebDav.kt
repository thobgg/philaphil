package de.bgghome.philaphil.daten

import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLSession
import okhttp3.internal.tls.OkHostnameVerifier
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

/** Eine Datei auf dem WebDAV-Server. */
data class DavDatei(val name: String, val groesse: Long, val etag: String?)

/** Der Server hat geantwortet, aber nicht wie erwartet (falsches Passwort, falscher Ordner …). */
class WebDavFehler(val status: Int, meldung: String) : IOException(meldung)

/**
 * Zugriff auf einen Ordner per WebDAV, z. B. auf der Synology-NAS (Paket "WebDAV Server") oder in Nextcloud.
 * Pfade sind relativ zur Adresse: "bestand.json", "Bilder/Bund-1031.jpg".
 */
class WebDav(http: OkHttpClient, adresse: String, benutzer: String, passwort: String, zertifikatName: String? = null) {
    private val basis: HttpUrl = adresse.trim().let { if (it.endsWith("/")) it else "$it/" }.toHttpUrl()
    private val grund = http.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("Authorization", Credentials.basic(benutzer, passwort, Charsets.UTF_8)).build()) }
        .build()

    /**
     * Im Heimnetz ruft man die NAS oft ueber ihre IP-Adresse auf, das Zertifikat lautet aber auf den Namen
     * (nas.example.de). Hat man diesem Namen zugestimmt, gilt ein gueltiges Zertifikat fuer ihn auch unter dieser
     * Adresse. Die Kette prueft das System wie immer, nur der Namensvergleich ist erweitert.
     */
    private val client = if (zertifikatName == null) grund else grund.newBuilder().hostnameVerifier { host, sitzung ->
        OkHostnameVerifier.verify(host, sitzung) ||
            (host.equals(basis.host, ignoreCase = true) && zertifikatNamen(sitzung).any { it.equals(zertifikatName, ignoreCase = true) })
    }.build()

    /**
     * Fuer die Rueckfrage: auf welche Namen lautet das (vom System als gueltig erkannte) Zertifikat des Servers?
     * Leer, wenn es nicht am Namen liegt.
     */
    fun zertifikatNamenErfragen(): List<String> {
        var namen = emptyList<String>()
        val c = grund.newBuilder().hostnameVerifier { _, sitzung -> namen = zertifikatNamen(sitzung); true }.build()
        runCatching {
            c.newCall(Request.Builder().url(basis).method("PROPFIND", PROPFIND_BODY.toRequestBody("application/xml".toMediaType())).header("Depth", "0").build()).execute().close()
        }
        return namen
    }

    private fun url(pfad: String): HttpUrl {
        val b = basis.newBuilder()
        // Der leere letzte Abschnitt der Basis ("…/PhilaPhil/") wird durch den ersten Teil des Pfads ersetzt
        if (basis.pathSegments.lastOrNull() == "") b.removePathSegment(basis.pathSegments.size - 1)
        pfad.split('/').filter { it.isNotEmpty() }.forEach { b.addPathSegment(it) }
        return b.build()
    }

    /** Prueft Adresse und Zugang; wirft WebDavFehler mit einer lesbaren Meldung. */
    fun pruefen() {
        val r = propfind(basis, tiefe = 0)
        if (r == null) throw WebDavFehler(404, "Ordner nicht gefunden")
    }

    /** Dateien eines Unterordners (ohne Unterordner); null, wenn es den Ordner nicht gibt. */
    fun liste(ordner: String): List<DavDatei>? {
        val ziel = url(ordner).newBuilder().addPathSegment("").build()
        val xml = propfind(ziel, tiefe = 1) ?: return null
        return ANTWORT.findAll(xml).mapNotNull { m ->
            val teil = m.groupValues[1]
            if (SAMMLUNG.containsMatchIn(teil)) return@mapNotNull null
            val href = HREF.find(teil)?.groupValues?.get(1)?.trim() ?: return@mapNotNull null
            val name = URLDecoder.decode(href.trimEnd('/').substringAfterLast('/').replace("+", "%2B"), "UTF-8")
            val groesse = LAENGE.find(teil)?.groupValues?.get(1)?.trim()?.toLongOrNull() ?: -1
            val etag = ETAG.find(teil)?.groupValues?.get(1)?.trim()?.replace("&quot;", "\"")
            DavDatei(name, groesse, etag)
        }.filter { it.name.isNotEmpty() }.toList()
    }

    /** Inhalt und ETag einer Datei; null, wenn es sie nicht gibt. */
    fun holen(pfad: String): Pair<ByteArray, String?>? =
        client.newCall(Request.Builder().url(url(pfad)).get().build()).execute().use { r ->
            when {
                r.code == 404 -> null
                !r.isSuccessful -> throw fehler(r.code)
                else -> r.body!!.bytes() to r.header("ETag")
            }
        }

    /**
     * Schreibt eine Datei. Mit `etag` nur, wenn sie seit dem Lesen keiner geaendert hat; mit `neu` nur, wenn es
     * sie noch nicht gibt. false, wenn ein anderes Geraet dazwischen geschrieben hat.
     */
    fun schreiben(pfad: String, bytes: ByteArray, etag: String? = null, neu: Boolean = false): Boolean {
        val b = Request.Builder().url(url(pfad)).put(bytes.toRequestBody(typ(pfad).toMediaType()))
        if (etag != null) b.header("If-Match", etag) else if (neu) b.header("If-None-Match", "*")
        client.newCall(b.build()).execute().use { r ->
            if (r.code == 412) return false
            if (!r.isSuccessful) throw fehler(r.code)
            return true
        }
    }

    /** Loescht eine Datei; eine fehlende ist kein Fehler. */
    fun loeschen(pfad: String) {
        client.newCall(Request.Builder().url(url(pfad)).delete().build()).execute().use { r ->
            if (!r.isSuccessful && r.code != 404) throw fehler(r.code)
        }
    }

    /** Legt einen Unterordner an; einen vorhandenen laesst es stehen. */
    fun ordnerAnlegen(ordner: String) {
        val ziel = url(ordner).newBuilder().addPathSegment("").build()
        client.newCall(Request.Builder().url(ziel).method("MKCOL", null).build()).execute().use { r ->
            if (!r.isSuccessful && r.code != 405) throw fehler(r.code)
        }
    }

    private fun propfind(ziel: HttpUrl, tiefe: Int): String? {
        val body = PROPFIND_BODY.toRequestBody("application/xml; charset=utf-8".toMediaType())
        client.newCall(Request.Builder().url(ziel).method("PROPFIND", body).header("Depth", tiefe.toString()).build()).execute().use { r ->
            if (r.code == 404) return null
            if (r.code != 207 && !r.isSuccessful) throw fehler(r.code)
            return r.body!!.string()
        }
    }

    private fun zertifikatNamen(sitzung: SSLSession): List<String> = runCatching {
        val z = sitzung.peerCertificates.first() as X509Certificate
        z.subjectAlternativeNames.orEmpty().filter { it[0] == 2 }.map { it[1].toString() }
            .ifEmpty { listOfNotNull(Regex("CN=([^,]+)").find(z.subjectX500Principal.name)?.groupValues?.get(1)) }
    }.getOrDefault(emptyList())

    private fun fehler(code: Int) = WebDavFehler(code, when (code) {
        401 -> "Benutzer oder Passwort falsch"
        403 -> "kein Zugriff auf den Ordner"
        404 -> "Ordner nicht gefunden"
        405 -> "Adresse ist kein WebDAV-Ordner"
        507 -> "kein Platz mehr auf dem Server"
        else -> "Server antwortet mit $code"
    })

    private fun typ(pfad: String) = when (pfad.substringAfterLast('.').lowercase()) {
        "json" -> "application/json"; "txt" -> "text/plain; charset=utf-8"
        "png" -> "image/png"; "webp" -> "image/webp"; "jpg", "jpeg" -> "image/jpeg"
        else -> "application/octet-stream"
    }

    companion object {
        private const val PROPFIND_BODY = """<?xml version="1.0" encoding="utf-8"?><d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/><d:getcontentlength/><d:getetag/></d:prop></d:propfind>"""
        // Namensraum-Kuerzel sind je Server verschieden (d:, D:, lp1: …)
        private val ANTWORT = Regex("""<(?:\w+:)?response\b[^>]*>(.*?)</(?:\w+:)?response>""", RegexOption.DOT_MATCHES_ALL)
        private val HREF = Regex("""<(?:\w+:)?href\b[^>]*>(.*?)</(?:\w+:)?href>""", RegexOption.DOT_MATCHES_ALL)
        private val SAMMLUNG = Regex("""<(?:\w+:)?collection\b""")
        private val LAENGE = Regex("""<(?:\w+:)?getcontentlength\b[^>]*>(.*?)</""", RegexOption.DOT_MATCHES_ALL)
        private val ETAG = Regex("""<(?:\w+:)?getetag\b[^>]*>(.*?)</""", RegexOption.DOT_MATCHES_ALL)
    }
}
