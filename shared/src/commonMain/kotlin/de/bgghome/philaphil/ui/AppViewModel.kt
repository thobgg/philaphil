package de.bgghome.philaphil.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.bgghome.philaphil.Plattform
import de.bgghome.philaphil.bestanddb.Bestand
import de.bgghome.philaphil.daten.BildInfo
import de.bgghome.philaphil.daten.CommonsBilder
import de.bgghome.philaphil.daten.EigeneBilder
import de.bgghome.philaphil.daten.Katalog
import de.bgghome.philaphil.daten.MarkenThema
import de.bgghome.philaphil.daten.Ordner
import de.bgghome.philaphil.daten.OrdnerDatei
import de.bgghome.philaphil.daten.Sammlung
import de.bgghome.philaphil.EINSTELLUNG_SAMMLUNG
import de.bgghome.philaphil.db.Jahrgaenge
import de.bgghome.philaphil.db.Marke
import de.bgghome.philaphil.db.Quelle
import de.bgghome.philaphil.db.Thema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Eine Themenseite: das Thema mit Kurztext und alle Marken dazu, ueber alle Jahrgaenge. */
data class ThemaSeite(val thema: Thema, val marken: List<Marke>)

/** Filter der Liste nach dem eigenen Bestand. */
enum class BestandFilter { Alle, Vorhanden, Fehlend }

/** Der ganze Zustand der Oberflaeche an einer Stelle - die Huellen zeigen ihn nur an. */
data class AppZustand(
    val laedt: Boolean = true,
    val fehler: String? = null,
    val gebiet: String = "Bund",
    val jahr: Long = 1979,
    /** Alle Jahrgaenge im Katalog, zum Blaettern. */
    val jahrgaenge: List<Jahrgaenge> = emptyList(),
    /** Die Marken des Jahrgangs. */
    val jahrgang: List<Marke> = emptyList(),
    val quelle: Quelle? = null,
    /** Suche: Eingabe und Treffer; bei leerer Eingabe zeigt die Liste den Jahrgang. */
    val suchtext: String = "",
    val suchtreffer: List<Marke>? = null,
    val filter: BestandFilter = BestandFilter.Alle,
    /** Commons-Angaben je Dateiname; fehlt ein Eintrag, zeigt die Liste den Platzhalter. */
    val bilder: Map<String, BildInfo> = emptyMap(),
    /** Eigene Bilder je (Gebiet, MiNr). */
    val eigeneBilder: Map<Pair<String, String>, List<OrdnerDatei>> = emptyMap(),
    /** Anzeigename des Sammlungsordners (bestand.json, Bilder/). */
    val sammlungsordner: String = "",
    /** Eigener Bestand: Erhaltungen je (Gebiet, MiNr) als Kurztext fuer die Liste. */
    val bestand: Map<Pair<String, String>, String> = emptyMap(),
    /** Gewaehlte Marke - am Handy eine eigene Seite, am Tablet die rechte Spalte. */
    val marke: Marke? = null,
    val themen: List<MarkenThema> = emptyList(),
    val eintraege: List<Bestand> = emptyList(),
    /** Geoeffnete Themenseite (liegt ueber der Marke). */
    val themaSeite: ThemaSeite? = null,
    /** Vollbild mit Wischen und Zoom ueber alle Marken der Liste mit Bild. */
    val vollbild: Boolean = false,
    val einstellungenOffen: Boolean = false,
) {
    /** Was die Liste zeigt: Suchtreffer oder der Jahrgang, nach Bestand gefiltert. */
    val liste: List<Marke> get() = (suchtreffer ?: jahrgang).filter {
        when (filter) {
            BestandFilter.Alle -> true
            BestandFilter.Vorhanden -> imBestand(it)
            BestandFilter.Fehlend -> !imBestand(it)
        }
    }
    val hauptthema: MarkenThema? get() = themen.firstOrNull { it.haupt }
    private val jahrIndex: Int get() = jahrgaenge.indexOfFirst { it.gebiet == gebiet && it.jahr == jahr }
    val aktuellerJahrgang: Jahrgaenge? get() = jahrgaenge.getOrNull(jahrIndex)
    val voriger: Jahrgaenge? get() = jahrgaenge.getOrNull(jahrIndex - 1)
    val naechster: Jahrgaenge? get() = jahrgaenge.getOrNull(jahrIndex + 1)
    fun bild(marke: Marke?): BildInfo? = marke?.commons_datei?.let { bilder[it] }
    fun eigene(marke: Marke?): List<OrdnerDatei> = marke?.let { eigeneBilder[it.gebiet to it.mi_nr] }.orEmpty()
    fun bestandText(marke: Marke): String? = bestand[marke.gebiet to marke.mi_nr]
    fun imBestand(marke: Marke) = bestandText(marke) != null
    /** Was die Liste und die Karte zeigen: eigener Scan vor Commons. */
    fun vorschau(marke: Marke): Any? = eigene(marke).firstOrNull()?.modell ?: bild(marke)?.vorschauUrl
    val imJahrgangVorhanden: Int get() = jahrgang.count { imBestand(it) }
}

/** @param startMiNr Marke, die nach dem Laden gleich geoeffnet wird (Desktop: Aufruf mit --minr 1031). */
class AppViewModel(val plattform: Plattform, private val startMiNr: String? = null) : ViewModel() {
    private val _zustand = MutableStateFlow(AppZustand())
    val zustand: StateFlow<AppZustand> = _zustand

    private var katalog: Katalog? = null
    private var sammlung: Sammlung? = null
    private val commons = CommonsBilder(plattform.http, plattform.datenOrdner)
    private var ordner: Ordner = plattform.sammlungsordner()
    private val eigene = EigeneBilder(ordner)
    private var suchlauf: Job? = null
    private var notizlauf: Job? = null

    init {
        viewModelScope.launch {
            try {
                val k = Katalog.oeffnen(plattform.datenOrdner).also { katalog = it }
                val s = withContext(Dispatchers.IO) { Sammlung(plattform.datenOrdner, ordner, plattform.einstellungen) }.also { sammlung = it }
                val jahrgaenge = withContext(Dispatchers.IO) { k.jahrgaenge }
                val bestand = withContext(Dispatchers.IO) { s.uebersicht() }
                val scans = withContext(Dispatchers.IO) { eigene.einlesen() }
                _zustand.update { it.copy(jahrgaenge = jahrgaenge, bestand = bestand, eigeneBilder = scans, sammlungsordner = ordner.anzeige) }
                // Zuletzt gesehener Jahrgang, sonst der Pilot 1979
                val jahr = plattform.einstellungen.lesen("jahr")?.toLongOrNull()?.takeIf { j -> jahrgaenge.any { it.jahr == j } } ?: _zustand.value.jahr
                jahrgangLaden(_zustand.value.gebiet, jahr)
                startMiNr?.let { nr -> _zustand.value.jahrgang.firstOrNull { it.mi_nr == nr }?.let(::waehlen) }
            } catch (e: Exception) {
                _zustand.update { it.copy(laedt = false, fehler = e.message ?: e.toString()) }
            }
        }
    }

    private suspend fun jahrgangLaden(gebiet: String, jahr: Long) {
        val k = katalog ?: return
        val marken = withContext(Dispatchers.IO) { k.markenImJahr(gebiet, jahr) }
        val quelle = withContext(Dispatchers.IO) { k.quelle(gebiet, jahr) }
        _zustand.update { it.copy(laedt = false, gebiet = gebiet, jahr = jahr, jahrgang = marken, quelle = quelle) }
        plattform.einstellungen.schreiben("jahr", jahr.toString())
        bilderNachladen(marken)
    }

    /** Blaettern wie im Geschichtsbuch: anderer Jahrgang, Auswahl und Suche zu. */
    fun jahrgangWaehlen(j: Jahrgaenge) {
        _zustand.update { it.copy(marke = null, themen = emptyList(), eintraege = emptyList(), themaSeite = null, suchtext = "", suchtreffer = null) }
        viewModelScope.launch { jahrgangLaden(j.gebiet, j.jahr) }
    }

    /** Bildadressen und Lizenzen - erst aus der Ablage, sonst von Commons. Marken mit eigenem Scan brauchen Commons nicht. */
    private fun bilderNachladen(marken: List<Marke>) {
        val z = _zustand.value
        val fehlend = marken.filter { z.eigene(it).isEmpty() }.mapNotNull { it.commons_datei }.filter { it !in z.bilder }
        if (fehlend.isEmpty()) return
        viewModelScope.launch {
            val infos = commons.infos(fehlend)
            _zustand.update { it.copy(bilder = it.bilder + infos) }
        }
    }

    fun waehlen(marke: Marke?) {
        _zustand.update { it.copy(marke = marke, themen = emptyList(), eintraege = emptyList(), themaSeite = null) }
        if (marke == null) return
        viewModelScope.launch {
            val themen = withContext(Dispatchers.IO) { katalog?.themenZurMarke(marke.id).orEmpty() }
            val eintraege = withContext(Dispatchers.IO) { sammlung?.zurMarke(marke.gebiet, marke.mi_nr).orEmpty() }
            // Nur eintragen, wenn inzwischen keine andere Marke gewaehlt wurde.
            _zustand.update { if (it.marke?.id == marke.id) it.copy(themen = themen, eintraege = eintraege) else it }
        }
    }

    // ------------------------------------------------------------ Bestand

    private fun bestandAktualisieren(marke: Marke) {
        val s = sammlung ?: return
        viewModelScope.launch {
            val eintraege = withContext(Dispatchers.IO) { s.zurMarke(marke.gebiet, marke.mi_nr) }
            val uebersicht = withContext(Dispatchers.IO) { s.uebersicht() }
            _zustand.update { it.copy(bestand = uebersicht, eintraege = if (it.marke?.id == marke.id) eintraege else it.eintraege) }
        }
    }

    /** Erhaltung an- oder abwaehlen bzw. Anzahl setzen (0 = entfernen). */
    fun erhaltungSetzen(marke: Marke, erhaltung: String, anzahl: Long) {
        val s = sammlung ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { s.setzen(marke.gebiet, marke.mi_nr, erhaltung, anzahl) }
            bestandAktualisieren(marke)
        }
    }

    fun notizSetzen(marke: Marke, text: String) {
        val s = sammlung ?: return
        // Sofort im Zustand, in der Datenbank kurz verzoegert, damit nicht jeder Buchstabe schreibt
        _zustand.update { z ->
            if (z.marke?.id != marke.id) z else z.copy(eintraege = if (z.eintraege.isEmpty()) z.eintraege else z.eintraege.map { it.copy(notiz = text) })
        }
        notizlauf?.cancel()
        notizlauf = viewModelScope.launch {
            delay(400)
            withContext(Dispatchers.IO) { s.notiz(marke.gebiet, marke.mi_nr, text) }
            bestandAktualisieren(marke)
        }
    }

    fun filterSetzen(filter: BestandFilter) = _zustand.update { it.copy(filter = filter) }

    // ------------------------------------------------------------ Eigene Bilder

    /** Ein gewaehltes Bild verkleinert und unter dem richtigen Namen im Sammlungsordner ablegen. */
    fun bildHinzufuegen(marke: Marke, quelle: File) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val (bytes, endung) = bildVorbereiten(quelle)
                eigene.ablegen(marke.gebiet, marke.mi_nr, endung, bytes)
                if (quelle.parentFile == plattform.cacheOrdner) quelle.delete()
            }
            bilderNeuEinlesen()
        }
    }

    /** Sammlungsordner neu lesen (nach Sync oder Aenderungen von aussen). */
    fun bilderNeuEinlesen() {
        viewModelScope.launch {
            val scans = withContext(Dispatchers.IO) { eigene.einlesen() }
            val s = sammlung
            val bestand = withContext(Dispatchers.IO) { s?.abgleichen(); s?.uebersicht().orEmpty() }
            _zustand.update { it.copy(eigeneBilder = scans, bestand = bestand, sammlungsordner = ordner.anzeige) }
            _zustand.value.marke?.let { waehlen(it) }
        }
    }

    /** Neuer Sammlungsordner (Pfad oder Android-Ordneradresse): merken, Bestand abgleichen, Bilder einlesen. */
    fun sammlungsordnerSetzen(wert: String?) {
        plattform.einstellungen.schreiben(EINSTELLUNG_SAMMLUNG, wert)
        ordner = plattform.sammlungsordner()
        eigene.ordner = ordner
        viewModelScope.launch {
            withContext(Dispatchers.IO) { sammlung?.ordnerWechseln(ordner) }
            bilderNeuEinlesen()
        }
    }

    fun einstellungen(offen: Boolean) = _zustand.update { it.copy(einstellungenOffen = offen) }

    // ------------------------------------------------------------ Themen, Suche, Vollbild

    fun themaOeffnen(thema: Thema) {
        viewModelScope.launch {
            val marken = withContext(Dispatchers.IO) { katalog?.markenZumThema(thema.id).orEmpty() }
            _zustand.update { it.copy(themaSeite = ThemaSeite(thema, marken)) }
            bilderNachladen(marken)
        }
    }

    fun themaSchliessen() = _zustand.update { it.copy(themaSeite = null) }

    /** Von der Themenseite zu einer Marke springen: die Liste wechselt mit, damit Wischen und Rahmen stimmen. */
    fun zurMarke(marke: Marke) {
        val inListe = _zustand.value.liste.any { it.id == marke.id }
        if (!inListe) {
            val z = _zustand.value
            val themaMarken = z.themaSeite?.marken.orEmpty()
            _zustand.update { it.copy(suchtext = z.themaSeite?.thema?.titel.orEmpty(), suchtreffer = themaMarken, filter = BestandFilter.Alle) }
        }
        waehlen(marke)
    }

    fun suchen(text: String) {
        _zustand.update { it.copy(suchtext = text) }
        suchlauf?.cancel()
        if (text.isBlank()) {
            _zustand.update { it.copy(suchtreffer = null) }
            return
        }
        suchlauf = viewModelScope.launch {
            delay(250)      // kurz warten, bis der Mensch zu Ende getippt hat
            val treffer = withContext(Dispatchers.IO) { katalog?.suche(text).orEmpty() }
            _zustand.update { if (it.suchtext == text) it.copy(suchtreffer = treffer) else it }
            bilderNachladen(treffer)
        }
    }

    fun vollbild(an: Boolean) = _zustand.update { it.copy(vollbild = an) }

    fun zurueck(): Boolean {
        val z = _zustand.value
        return when {
            z.einstellungenOffen -> { einstellungen(false); true }
            z.vollbild -> { vollbild(false); true }
            z.themaSeite != null -> { themaSchliessen(); true }
            z.marke != null -> { waehlen(null); true }
            z.suchtreffer != null -> { suchen(""); true }
            else -> false
        }
    }

    fun oeffneWeb(url: String) = plattform.oeffneWeb(url)
}
