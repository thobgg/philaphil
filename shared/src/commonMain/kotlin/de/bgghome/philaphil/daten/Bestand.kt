package de.bgghome.philaphil.daten

import app.cash.sqldelight.db.SqlDriver
import de.bgghome.philaphil.Ablage
import de.bgghome.philaphil.bestanddb.Bestand
import de.bgghome.philaphil.bestanddb.BestandDb
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDateTime

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
            val eintraege = runCatching { json.decodeFromString<List<BestandEintrag>>(text) }.getOrNull() ?: return
            db.transaction {
                db.bestandQueries.alleLoeschen()
                eintraege.forEach { db.bestandQueries.setzen(it.gebiet, it.mi_nr, it.erhaltung, it.anzahl, it.notiz, it.geaendert) }
            }
            einstellungen.schreiben("bestandStand:${ordner.anzeige}", stand.toString())
        } else if (stand == 0L && alle().isNotEmpty()) {
            sichern()      // neuer Ordner ohne JSON: den lokalen Bestand hineinschreiben
        }
    }

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
        if (anzahl <= 0) db.bestandQueries.loeschen(gebiet, miNr, erhaltung)
        else db.bestandQueries.setzen(gebiet, miNr, erhaltung, anzahl, notiz, jetzt())
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

    private fun jetzt() = LocalDateTime.now().withNano(0).toString()

    private fun sichern() {
        runCatching {
            val text = json.encodeToString(alle().map { BestandEintrag(it.gebiet, it.mi_nr, it.erhaltung, it.anzahl, it.notiz, it.geaendert) })
            ordner.schreiben(BESTAND_JSON, text.toByteArray(Charsets.UTF_8))
            einstellungen.schreiben("bestandStand:${ordner.anzeige}", ordner.geaendert(BESTAND_JSON).toString())
        }
    }
}
