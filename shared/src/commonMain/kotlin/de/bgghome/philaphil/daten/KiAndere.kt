package de.bgghome.philaphil.daten

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

private val JSON_TYP = "application/json".toMediaType()

/** Gemeinsame HTTP-Behandlung: Statuscodes in verstaendliche Meldungen uebersetzen. */
private fun meldung(code: Int, koerper: String): String {
    val text = runCatching {
        Json.parseToJsonElement(koerper).jsonObject["error"]?.let { e ->
            (e as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull ?: e.jsonPrimitive.contentOrNull
        }
    }.getOrNull().orEmpty().take(160)
    return when (code) {
        400 -> "Die Anfrage wurde abgelehnt: $text"
        401, 403 -> "Der API-Schlüssel wurde nicht angenommen. Bitte in den Einstellungen prüfen."
        404 -> "Das Modell ist unbekannt. Bitte in den Einstellungen ein anderes wählen. $text"
        402 -> "Das Guthaben des API-Kontos reicht nicht."
        429 -> "Zu viele Anfragen oder Kontingent erschöpft. Bitte später noch einmal versuchen. $text"
        else -> "Der Dienst ist gerade nicht erreichbar ($code)."
    }
}

private fun OkHttpClient.langsam() = newBuilder().readTimeout(120, TimeUnit.SECONDS).callTimeout(150, TimeUnit.SECONDS).build()

/** ChatGPT ueber die OpenAI Responses API (POST /v1/responses). */
class OpenAiBegleiter(http: OkHttpClient, private val schluessel: String, private val modell: String) : KiAnbieter {
    private val http = http.langsam()

    override suspend fun wusstestDu(thema: String, artikeltext: String): KiAntwort = withContext(Dispatchers.IO) {
        val koerper = buildJsonObject {
            put("model", modell)
            put("instructions", KiGemeinsam.SYSTEM)
            put("input", KiGemeinsam.nutzertext(thema, artikeltext))
        }.toString()
        val anfrage = Request.Builder().url("https://api.openai.com/v1/responses")
            .header("Authorization", "Bearer $schluessel").post(koerper.toRequestBody(JSON_TYP)).build()
        try {
            http.newCall(anfrage).execute().use { r ->
                val text = r.body?.string().orEmpty()
                if (!r.isSuccessful) return@withContext KiAntwort.Fehler(meldung(r.code, text))
                // output[] -> message -> content[] -> output_text
                val ausgabe = Json.parseToJsonElement(text).jsonObject["output"]?.jsonArray.orEmpty()
                    .flatMap { (it.jsonObject["content"] as? kotlinx.serialization.json.JsonArray).orEmpty() }
                    .filter { it.jsonObject["type"]?.jsonPrimitive?.contentOrNull == "output_text" }
                    .joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty() }
                KiGemeinsam.pruefen(ausgabe, artikeltext, modell)
            }
        } catch (e: IOException) {
            KiAntwort.Fehler("Keine Verbindung zu OpenAI. Bitte die Internetverbindung prüfen.")
        }
    }

    override suspend fun modelle(): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url("https://api.openai.com/v1/models").header("Authorization", "Bearer $schluessel").build()).execute().use { r ->
                if (!r.isSuccessful) return@use emptyList()
                Json.parseToJsonElement(r.body!!.string()).jsonObject["data"]?.jsonArray.orEmpty()
                    .mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull }
                    .filter { it.startsWith("gpt") && listOf("audio", "realtime", "image", "tts", "transcribe", "search").none { x -> x in it } }
                    .sortedDescending()
            }
        }.getOrDefault(emptyList())
    }
}

/** Gemini ueber die Gemini API (generateContent). */
class GeminiBegleiter(http: OkHttpClient, private val schluessel: String, private val modell: String) : KiAnbieter {
    private val http = http.langsam()

    override suspend fun wusstestDu(thema: String, artikeltext: String): KiAntwort = withContext(Dispatchers.IO) {
        val koerper = buildJsonObject {
            putJsonObject("systemInstruction") { putJsonArray("parts") { add(buildJsonObject { put("text", KiGemeinsam.SYSTEM) }) } }
            putJsonArray("contents") {
                add(buildJsonObject {
                    put("role", "user")
                    putJsonArray("parts") { add(buildJsonObject { put("text", KiGemeinsam.nutzertext(thema, artikeltext)) }) }
                })
            }
        }.toString()
        val anfrage = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$modell:generateContent")
            .header("x-goog-api-key", schluessel).post(koerper.toRequestBody(JSON_TYP)).build()
        try {
            http.newCall(anfrage).execute().use { r ->
                val text = r.body?.string().orEmpty()
                if (!r.isSuccessful) return@withContext KiAntwort.Fehler(meldung(r.code, text))
                val kandidat = Json.parseToJsonElement(text).jsonObject["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
                    ?: return@withContext KiAntwort.Fehler("Gemini hat keinen Vorschlag geliefert.")
                if (kandidat["finishReason"]?.jsonPrimitive?.contentOrNull == "SAFETY") return@withContext KiAntwort.Fehler("Gemini hat diese Anfrage abgelehnt.")
                val ausgabe = kandidat["content"]?.jsonObject?.get("parts")?.jsonArray.orEmpty()
                    .joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty() }
                KiGemeinsam.pruefen(ausgabe, artikeltext, modell)
            }
        } catch (e: IOException) {
            KiAntwort.Fehler("Keine Verbindung zu Google. Bitte die Internetverbindung prüfen.")
        }
    }

    override suspend fun modelle(): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url("https://generativelanguage.googleapis.com/v1beta/models?pageSize=200").header("x-goog-api-key", schluessel).build()).execute().use { r ->
                if (!r.isSuccessful) return@use emptyList()
                Json.parseToJsonElement(r.body!!.string()).jsonObject["models"]?.jsonArray.orEmpty()
                    .filter { m -> m.jsonObject["supportedGenerationMethods"]?.jsonArray.orEmpty().any { it.jsonPrimitive.contentOrNull == "generateContent" } }
                    .mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull?.removePrefix("models/") }
                    .filter { it.startsWith("gemini") }
                    .sortedDescending()
            }
        }.getOrDefault(emptyList())
    }
}

private fun kotlinx.serialization.json.JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()
