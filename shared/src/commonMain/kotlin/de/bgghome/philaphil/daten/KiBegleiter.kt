package de.bgghome.philaphil.daten

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.BadRequestException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.beta.messages.BetaOutputConfig
import com.anthropic.models.beta.messages.MessageCreateParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Ein Vorschlag fuer "Wusstest du?" - ein Satz und das woertliche Zitat aus dem Artikel, auf dem er beruht. */
@Serializable
data class KiEntwurf(val satz: String, val beleg: String, val modell: String)

/** Ergebnis eines Aufrufs: Entwurf oder eine verstaendliche Meldung fuer die Oberflaeche. */
sealed interface KiAntwort {
    data class Entwurf(val entwurf: KiEntwurf) : KiAntwort
    data class Fehler(val meldung: String) : KiAntwort
}

/**
 * KI-Begleiter nach dem Companion-Prinzip: formuliert aus einem gegebenen Wikipedia-Text einen einzigen Satz
 * "Wusstest du?", liefert nie neue Fakten. Abgesichert wird das zweifach: Claude muss das woertliche Zitat
 * angeben, auf dem der Satz beruht, und die App verwirft den Entwurf, wenn das Zitat nicht im Text steht.
 * Laeuft nur nach Opt-in mit dem eigenen API-Schluessel des Nutzers; gesendet wird nur der Artikeltext.
 */
/** Ein KI-Anbieter, der aus einem Artikeltext einen Satz "Wusstest du?" vorschlaegt. */
interface KiAnbieter {
    suspend fun wusstestDu(thema: String, artikeltext: String): KiAntwort
    /** Modelle, die der Schluessel nutzen darf (fuer die Auswahl in den Einstellungen). */
    suspend fun modelle(): List<String>
}

/** Die waehlbaren Anbieter. Lumo (Proton) folgt, sobald es eine offizielle Entwickler-Schnittstelle gibt. */
enum class KiArt(val anzeige: String, val standardModell: String, val schluesselSeite: String) {
    CLAUDE("Claude (Anthropic)", "claude-opus-5-5", "https://console.anthropic.com/settings/keys"),
    OPENAI("ChatGPT (OpenAI)", "gpt-6.1-sol", "https://platform.openai.com/api-keys"),
    GEMINI("Gemini (Google)", "gemini-3.8-flash", "https://aistudio.google.com/apikey"),
}

fun kiAnbieter(art: KiArt, schluessel: String, modell: String, http: okhttp3.OkHttpClient): KiAnbieter = when (art) {
    KiArt.CLAUDE -> KiBegleiter(schluessel, modell)
    KiArt.OPENAI -> OpenAiBegleiter(http, schluessel, modell)
    KiArt.GEMINI -> GeminiBegleiter(http, schluessel, modell)
}

/** Claude ueber das offizielle Anthropic-SDK. */
class KiBegleiter(private val apiSchluessel: String, private val modell: String = MODELL) : KiAnbieter {

    override suspend fun modelle(): List<String> = withContext(Dispatchers.IO) {
        val client = AnthropicOkHttpClient.builder().apiKey(apiSchluessel).build()
        try { client.models().list().autoPager().map { it.id() }.toList() } catch (e: Exception) { emptyList() } finally { client.close() }
    }

    override suspend fun wusstestDu(thema: String, artikeltext: String): KiAntwort = withContext(Dispatchers.IO) {
        val client = AnthropicOkHttpClient.builder().apiKey(apiSchluessel).build()
        try {
            val builder = MessageCreateParams.builder()
                .model(modell)
                .maxTokens(16000L)
                // Ein Satz aus vorgegebenem Text braucht wenig Nachdenken
                .outputConfig(BetaOutputConfig.builder().effort(BetaOutputConfig.Effort.LOW).build())
                // Lehnt das Modell aus Sicherheitsgruenden ab, uebernimmt serverseitig ein passendes anderes
                .system(SYSTEM)
                .addUserMessage(nutzertext(thema, artikeltext))
            if (modell in MIT_RUECKFALL) {
                builder.addBeta("server-side-fallback-2026-07-01").putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
            }
            val params = builder.build()
            val antwort = client.beta().messages().create(params)
            if (antwort.stopReason().map { it.toString() }.orElse("") == "refusal") {
                return@withContext KiAntwort.Fehler("Claude hat diese Anfrage abgelehnt.")
            }
            val text = antwort.content().flatMap { b -> b.text().map { listOf(it.text()) }.orElse(emptyList()) }.joinToString("").trim()
            pruefen(text, artikeltext, antwort.model().toString())
        } catch (e: UnauthorizedException) {
            KiAntwort.Fehler("Der API-Schlüssel wurde nicht angenommen. Bitte in den Einstellungen prüfen.")
        } catch (e: PermissionDeniedException) {
            KiAntwort.Fehler("Der API-Schlüssel darf dieses Modell nicht nutzen.")
        } catch (e: RateLimitException) {
            KiAntwort.Fehler("Zu viele Anfragen in kurzer Zeit. Bitte gleich noch einmal versuchen.")
        } catch (e: BadRequestException) {
            KiAntwort.Fehler("Die Anfrage wurde abgelehnt: ${e.message?.take(160)}")
        } catch (e: AnthropicServiceException) {
            val typ = e.errorType().map { it.toString() }.orElse("")
            KiAntwort.Fehler(if (typ.contains("billing", true)) "Das Guthaben des API-Kontos reicht nicht." else "Claude ist gerade nicht erreichbar ($typ).")
        } catch (e: AnthropicIoException) {
            KiAntwort.Fehler("Keine Verbindung zu Claude. Bitte die Internetverbindung prüfen.")
        } finally {
            client.close()
        }
    }

    companion object {
        const val MODELL = "claude-opus-5-5"
        private val MIT_RUECKFALL = setOf("claude-opus-5-5", "claude-opus-5", "claude-fable-5-1", "claude-sonnet-5-5")
        fun kuerzen(text: String) = KiGemeinsam.kuerzen(text)
        fun pruefen(antwort: String, artikeltext: String, modell: String) = KiGemeinsam.pruefen(antwort, artikeltext, modell)
        private val SYSTEM get() = KiGemeinsam.SYSTEM
        private fun nutzertext(thema: String, text: String) = KiGemeinsam.nutzertext(thema, text)
    }
}

/** Gemeinsam fuer alle Anbieter: Auftrag, Kuerzen und die Belegpruefung. */
object KiGemeinsam {

        fun nutzertext(thema: String, artikeltext: String) = "Thema: $thema\n\n<artikel>\n$artikeltext\n</artikel>"

        /** Wie viel Artikeltext mitgeht: Einleitung und die ersten Abschnitte, an einer Absatzgrenze geschnitten. */
        const val HOECHSTENS_ZEICHEN = 12_000

        val SYSTEM = """
            Du hilfst einer Briefmarken-App, zu einem Thema einen einzigen Satz für die Rubrik „Wusstest du?“ zu formulieren.
            Die Leser sind Briefmarkensammler, oft älter, und lesen die App am Tablet. Der Satz soll eine überraschende,
            anschauliche Einzelheit erzählen, die nicht schon die Grunddaten wiederholt (Geburtstag, Beruf, Gründungsjahr).

            Du darfst ausschließlich Fakten verwenden, die im mitgeschickten Artikeltext stehen. Dein eigenes Wissen
            bringst du nicht ein, auch wenn es richtig wäre: Jede Angabe im Satz muss sich auf eine Stelle im Text stützen.
            Formuliere eigenständig auf Deutsch, in einem Satz mit höchstens 30 Wörtern, ohne Anrede und ohne „Wusstest du“ am Anfang.

            Antworte nur mit einem JSON-Objekt ohne weiteren Text:
            {"satz": "…", "beleg": "…"}
            "beleg" ist ein wörtliches, unverändertes Zitat aus dem Artikeltext (ein Satz oder Satzteil, höchstens 300 Zeichen),
            auf dem dein Satz beruht. Findest du keine geeignete Einzelheit, antworte mit {"satz": "", "beleg": ""}.
        """.trimIndent()

        private val json = Json { ignoreUnknownKeys = true }

        private fun normal(s: String) = s.replace(Regex("\\s+"), " ").replace('„', '"').replace('“', '"').replace('”', '"').trim().lowercase()

        /** JSON lesen und pruefen, dass der Beleg woertlich im Artikel steht - sonst kein Entwurf. */
        fun pruefen(antwort: String, artikeltext: String, modell: String): KiAntwort {
            val roh = antwort.substringAfter("{", "").substringBeforeLast("}", "")
            if (roh.isBlank()) return KiAntwort.Fehler("Die KI hat keinen verwertbaren Vorschlag geliefert.")
            val obj = runCatching { json.parseToJsonElement("{$roh}").jsonObject }.getOrNull()
                ?: return KiAntwort.Fehler("Die KI hat keinen verwertbaren Vorschlag geliefert.")
            val satz = obj["satz"]?.jsonPrimitive?.content.orEmpty().trim()
            val beleg = obj["beleg"]?.jsonPrimitive?.content.orEmpty().trim()
            if (satz.isEmpty()) return KiAntwort.Fehler("Im Artikel fand sich keine passende Einzelheit.")
            if (beleg.length < 15 || !normal(artikeltext).contains(normal(beleg).trimEnd('.', '…'))) {
                return KiAntwort.Fehler("Der Vorschlag ließ sich nicht im Artikeltext belegen und wurde verworfen.")
            }
            return KiAntwort.Entwurf(KiEntwurf(satz, beleg, modell))
        }

        /** Artikeltext auf die ersten HOECHSTENS_ZEICHEN kuerzen, an einer Absatzgrenze. */
        fun kuerzen(text: String): String {
            if (text.length <= HOECHSTENS_ZEICHEN) return text
            val schnitt = text.lastIndexOf("\n", HOECHSTENS_ZEICHEN).takeIf { it > HOECHSTENS_ZEICHEN / 2 } ?: HOECHSTENS_ZEICHEN
            return text.substring(0, schnitt)
        }
}
