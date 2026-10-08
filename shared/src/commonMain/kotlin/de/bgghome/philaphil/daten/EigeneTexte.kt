package de.bgghome.philaphil.daten

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.LocalDate

/** Ein von dir freigegebener Satz "Wusstest du?" zu einem Thema. */
@Serializable
data class EigenerSatz(
    val thema: String,
    val satz: String,
    val beleg: String? = null,
    /** 'ki-entwurf' (von dir geprueft) oder 'eigen' */
    val quelle: String = "ki-entwurf",
    val modell: String? = null,
    val datum: String = LocalDate.now().toString(),
)

/**
 * Eigene Saetze "Wusstest du?" im Sammlungsordner (wusstest_du.json): offen, lesbar, reist mit der Sync-App
 * mit. Schluessel ist die Wikidata-Kennung des Themas, sonst "titel:<Artikel>".
 */
class EigeneTexte(var ordner: Ordner) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    fun alle(): Map<String, EigenerSatz> = runCatching {
        ordner.lesen(DATEI)?.toString(Charsets.UTF_8)?.let { json.decodeFromString<Map<String, EigenerSatz>>(it) }
    }.getOrNull().orEmpty()

    fun setzen(schluessel: String, satz: EigenerSatz?) {
        val neu = alle().toMutableMap()
        if (satz == null) neu.remove(schluessel) else neu[schluessel] = satz
        ordner.schreiben(DATEI, json.encodeToString(neu.toSortedMap()).toByteArray(Charsets.UTF_8))
    }

    companion object { const val DATEI = "wusstest_du.json" }
}

/** Klartext eines Wikipedia-Artikels (ganzer Artikel, ohne Auszeichnung) fuer den KI-Begleiter. */
fun wikipediaText(http: OkHttpClient, titel: String): String? {
    val url = "https://de.wikipedia.org/w/api.php".toHttpUrl().newBuilder()
        .addQueryParameter("action", "query").addQueryParameter("prop", "extracts")
        .addQueryParameter("explaintext", "1").addQueryParameter("redirects", "1")
        .addQueryParameter("titles", titel).addQueryParameter("format", "json").addQueryParameter("formatversion", "2")
        .build()
    return runCatching {
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) return null
            val seiten = Json.parseToJsonElement(r.body!!.string()).jsonObject["query"]?.jsonObject?.get("pages")?.jsonArray
            seiten?.firstOrNull()?.jsonObject?.get("extract")?.jsonPrimitive?.content
        }
    }.getOrNull()?.takeIf { it.isNotBlank() }
}
