package de.bgghome.philaphil.daten

import app.cash.sqldelight.db.SqlDriver
import de.bgghome.philaphil.Ablage
import de.bgghome.philaphil.bestanddb.Bestand
import de.bgghome.philaphil.bestanddb.BestandDb
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Oeffnet bestand.db; legt das Schema an, wenn die Datei neu ist. */
expect fun bestandTreiber(datei: File): SqlDriver

/** Die vier Erhaltungen, wie Sammler sie schreiben. */
val ERHALTUNGEN = listOf("**", "*", "⊙", "FDC")
const val BESTAND_JSON = "bestand.json"

@Serializable
data class BestandEintrag(val gebiet: String, val mi_nr: String, val erhaltung: String, val anzahl: Long = 1, val notiz: String? = null, val geaendert: String? = null)

/**
 * Der eigene Bestand: je Marke und Erhaltung ein Eintrag mit Anzahl, dazu eine Notiz je Marke.
 * Die Wahrheit ist bestand.json im Sammlungsordner (offen, lesbar, von einer Sync-App auf andere
 * Geraete gespiegelt); bestand.db im Datenordner ist nur der lokale Zwischenspeicher.
 */
class Sammlung(datenOrdner: File, private var ordner: Ordner, private val einstellungen: Ablage) {
    private val db = BestandDb(bestandTreiber(File(datenOrdner, "bestand.db")))
    private val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = true }

    init { abgleichen() }

    /** JSON im Sammlungsordner neuer als das zuletzt eingelesene (anderes Geraet): komplett uebernehmen. */
    fun abgleichen() {
        val stand = ordner.geaendert(BESTAND_JSON)
        val gelesen = einstellungen.lesen("bestandStand:${ordner.anzeige}")?.toLongOrNull() ?: 0L
        if (stand > 0 && (stand > gelesen || alle().isEmpty())) {
            val text = ordner.lesen(BESTAND_JSON)?.toString(Charsets.UTF_8) ?: return
            val eintraege = bestandLesen(text) ?: return
            eintraegeSetzen(eintraege)
            einstellungen.schreiben("bestandStand:${ordner.anzeige}", stand.toString())
        } else if (stand == 0L && alle().isNotEmpty()) {
            sichern()      // neuer Ordner ohne JSON: den lokalen Bestand hineinschreiben
        }
    }

    fun bestandJson(eintraege: List<BestandEintrag>): String = json.encodeToString(eintraege)

    fun bestandLesen(text: String): List<BestandEintrag>? = runCatching { json.decodeFromString<List<BestandEintrag>>(text) }.getOrNull()

    fun ordnerWechseln(neu: Ordner) { ordner = neu; abgleichen() }

    fun alle(): List<Bestand> = db.bestandQueries.alle().executeAsList()

    fun zurMarke(gebiet: String, miNr: String): List<Bestand> = db.bestandQueries.zurMarke(gebiet, miNr).executeAsList()

    /** Erhaltungen je Marke als kurzer Text fuer die Liste: "** ⊙". */
    fun uebersicht(): Map<Pair<String, String>, String> =
        alle().groupBy { it.gebiet to it.mi_nr }.mapValues { (_, e) ->
            e.sortedBy { ERHALTUNGEN.indexOf(it.erhaltung) }.joinToString(" ") { if (it.anzahl > 1) "${it.erhaltung}×${it.anzahl}" else it.erhaltung }
        }

    fun setzen(gebiet: String, miNr: String, erhaltung: String, anzahl: Long) {
        val notiz = zurMarke(gebiet, miNr).firstNotNullOfOrNull { it.notiz }
        // Entfernen setzt die Anzahl auf 0: der Vermerk reist mit, damit andere Geraete die Marke auch austragen
        db.bestandQueries.setzen(gebiet, miNr, erhaltung, maxOf(anzahl, 0), notiz, jetzt())
        sichern()
    }

    fun notiz(gebiet: String, miNr: String, text: String) {
        val t = text.ifBlank { null }
        if (zurMarke(gebiet, miNr).isEmpty()) {
            // Notiz ohne Erhaltung: als "vorhanden, Erhaltung unbekannt" fuehren
            if (t != null) db.bestandQueries.setzen(gebiet, miNr, "?", 1, t, jetzt())
        } else {
            db.bestandQueries.notizSetzen(t, jetzt(), gebiet, miNr)
        }
        sichern()
    }

    /** Alle Eintraege samt Loeschvermerken, wie sie in bestand.json stehen. */
    fun eintraege(): List<BestandEintrag> =
        db.bestandQueries.alleMitVermerken().executeAsList().map { BestandEintrag(it.gebiet, it.mi_nr, it.erhaltung, it.anzahl, it.notiz, it.geaendert) }

    /** Ergebnis eines Abgleichs uebernehmen: Datenbank ersetzen und bestand.json im Sammlungsordner schreiben. */
    fun uebernehmen(eintraege: List<BestandEintrag>) {
        eintraegeSetzen(eintraege)
        sichern()
    }

    private fun eintraegeSetzen(eintraege: List<BestandEintrag>) {
        db.transaction {
            db.bestandQueries.alleLoeschen()
            eintraege.forEach { db.bestandQueries.setzen(it.gebiet, it.mi_nr, it.erhaltung, it.anzahl, it.notiz, it.geaendert) }
        }
    }

    /** Weltzeit (UTC), damit Geraete und Zeitumstellung die Reihenfolge nicht durcheinanderbringen. */
    private fun jetzt() = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString()

    private fun sichern() {
        runCatching {
            val text = bestandJson(eintraege())
            ordner.schreiben(BESTAND_JSON, text.toByteArray(Charsets.UTF_8))
            einstellungen.schreiben("bestandStand:${ordner.anzeige}", ordner.geaendert(BESTAND_JSON).toString())
        }
    }
}

/**
 * Zwei Fassungen des Bestands zusammenfuehren (dieses Geraet und die NAS): je Marke und Erhaltung gilt der
 * zuletzt geaenderte Eintrag. Loeschvermerke (Anzahl 0) zaehlen mit, so geht keine Aenderung verloren.
 */
fun zusammenfuehren(a: List<BestandEintrag>, b: List<BestandEintrag>): List<BestandEintrag> =
    (a + b).groupBy { Triple(it.gebiet, it.mi_nr, it.erhaltung) }.values
        .map { l -> l.maxWith(compareBy<BestandEintrag>({ zeitpunkt(it.geaendert) }, { it.anzahl }, { it.notiz.orEmpty() })) }
        .sortedWith(compareBy({ it.gebiet }, { it.mi_nr }, { it.erhaltung }))

/**
 * Zeitpunkt eines Eintrags: neu in Weltzeit ("…Z"), aeltere Fassungen (bis 0.7) schrieben Ortszeit ohne Zone -
 * die gilt als Zeit dieses Geraets.
 */
fun zeitpunkt(s: String?): Instant {
    if (s.isNullOrBlank()) return Instant.EPOCH
    return runCatching { Instant.parse(s) }
        .recoverCatching { OffsetDateTime.parse(s).toInstant() }
        .recoverCatching { LocalDateTime.parse(s).atZone(ZoneId.systemDefault()).toInstant() }
        .getOrDefault(Instant.EPOCH)
}
