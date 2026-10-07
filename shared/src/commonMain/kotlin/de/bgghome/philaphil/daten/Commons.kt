package de.bgghome.philaphil.daten

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/** Was die App von einem Commons-Bild wissen muss: Adressen und die Lizenz fuer die Zeile unter dem Bild. */
@Serializable
data class BildInfo(
    val datei: String,
    val vorschauUrl: String,
    val originalUrl: String,
    val seiteUrl: String,
    val breite: Int,
    val hoehe: Int,
    val lizenz: String,
    val lizenzUrl: String? = null,
    val urheber: String? = null,
)

/**
 * Bilder kommen nur ueber die Commons-API (nie Dateien ins Repo): imageinfo mit iiurlwidth fuer die
 * Vorschau, das Original fuer das Vollbild, Lizenz aus extmetadata. Antworten werden in einer JSON-Datei
 * im Datenordner behalten, damit die App offline die Adressen kennt (die Bilder selbst cached Coil).
 */
class CommonsBilder(private val http: OkHttpClient, datenOrdner: File) {
    private val ablage = File(datenOrdner, "commons-bilder.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val bekannt = HashMap<String, BildInfo>()
    private val schloss = Mutex()
    private var geladen = false

    private fun laden() {
        if (geladen) return
        geladen = true
        runCatching {
            if (ablage.isFile) json.decodeFromString<List<BildInfo>>(ablage.readText()).forEach { bekannt[it.datei] = it }
        }
    }

    private fun sichern() {
        runCatching { ablage.parentFile?.mkdirs(); ablage.writeText(json.encodeToString(bekannt.values.sortedBy { it.datei })) }
    }

    /** Liefert die Angaben zu den Dateien; fehlende werden in Bloecken zu 50 bei Commons erfragt. */
    suspend fun infos(dateien: Collection<String>, vorschauBreite: Int = VORSCHAU_BREITE): Map<String, BildInfo> =
        withContext(Dispatchers.IO) {
            schloss.withLock {
                laden()
                val fehlend = dateien.filter { it.isNotBlank() && it !in bekannt }.distinct()
                fehlend.chunked(50).forEach { block ->
                    runCatching { abfragen(block, vorschauBreite) }
                        .onSuccess { neu -> neu.forEach { bekannt[it.datei] = it } }
                        .onFailure { println("Commons: ${it.message}") }
                }
                if (fehlend.isNotEmpty()) sichern()
                dateien.mapNotNull { d -> bekannt[d]?.let { d to it } }.toMap()
            }
        }

    private fun abfragen(dateien: List<String>, vorschauBreite: Int): List<BildInfo> {
        val url = API.toHttpUrl().newBuilder()
            .addQueryParameter("action", "query")
            .addQueryParameter("prop", "imageinfo")
            .addQueryParameter("iiprop", "url|size|extmetadata")
            .addQueryParameter("iiurlwidth", vorschauBreite.toString())
            .addQueryParameter("iiextmetadatafilter", "LicenseShortName|LicenseUrl|Artist|Credit")
            .addQueryParameter("format", "json")
            .addQueryParameter("formatversion", "2")
            .addQueryParameter("titles", dateien.joinToString("|") { "File:$it" })
            .build()
        val antwort = http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            check(r.isSuccessful) { "HTTP ${r.code}" }
            r.body!!.string()
        }
        val seiten = json.parseToJsonElement(antwort).jsonObject["query"]?.jsonObject?.get("pages")?.jsonArray ?: return emptyList()
        // Commons normalisiert Titel (Unterstriche, erster Buchstabe gross) - darum die Antwort ueber den Titel zuordnen.
        val normalisiert = json.parseToJsonElement(antwort).jsonObject["query"]?.jsonObject?.get("normalized")?.jsonArray
            ?.associate { it.jsonObject["to"]!!.jsonPrimitive.content to it.jsonObject["from"]!!.jsonPrimitive.content }.orEmpty()
        return seiten.mapNotNull { s ->
            val seite = s.jsonObject
            val titel = seite["title"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val angefragt = normalisiert[titel] ?: titel
            val ii = seite["imageinfo"]?.jsonArray?.firstOrNull()?.jsonObject ?: return@mapNotNull null
            val meta = ii["extmetadata"]?.jsonObject.orEmpty()
            fun metaWert(name: String) = meta[name]?.jsonObject?.get("value")?.jsonPrimitive?.content?.let(::ohneHtml)
            BildInfo(
                datei = angefragt.removePrefix("File:"),
                vorschauUrl = ii["thumburl"]?.jsonPrimitive?.content ?: ii["url"]!!.jsonPrimitive.content,
                originalUrl = ii["url"]!!.jsonPrimitive.content,
                seiteUrl = ii["descriptionurl"]?.jsonPrimitive?.content ?: "https://commons.wikimedia.org/wiki/$titel",
                breite = ii["width"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                hoehe = ii["height"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                lizenz = metaWert("LicenseShortName") ?: "Lizenz siehe Commons",
                lizenzUrl = metaWert("LicenseUrl"),
                urheber = metaWert("Artist") ?: metaWert("Credit"),
            )
        }
    }

    companion object {
        const val API = "https://commons.wikimedia.org/w/api.php"
        const val VORSCHAU_BREITE = 480
        private fun ohneHtml(s: String) = s.replace(Regex("<[^>]+>"), "").replace("&amp;", "&").trim()
    }
}

private fun Map<String, kotlinx.serialization.json.JsonElement>?.orEmpty(): JsonObject = (this as? JsonObject) ?: JsonObject(emptyMap())
