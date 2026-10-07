package de.bgghome.philaphil.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.bgghome.philaphil.Plattform
import de.bgghome.philaphil.daten.BildInfo
import de.bgghome.philaphil.daten.CommonsBilder
import de.bgghome.philaphil.daten.Katalog
import de.bgghome.philaphil.daten.MarkenThema
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

/** Eine Themenseite: das Thema mit Kurztext und alle Marken dazu, ueber alle Jahrgaenge. */
data class ThemaSeite(val thema: Thema, val marken: List<Marke>)

/** Der ganze Zustand der Oberflaeche an einer Stelle - die Huellen zeigen ihn nur an. */
data class AppZustand(
    val laedt: Boolean = true,
    val fehler: String? = null,
    val gebiet: String = "Bund",
    val jahr: Long = 1979,
    /** Die Marken des Jahrgangs. */
    val jahrgang: List<Marke> = emptyList(),
    val quelle: Quelle? = null,
    /** Suche: Eingabe und Treffer; bei leerer Eingabe zeigt die Liste den Jahrgang. */
    val suchtext: String = "",
    val suchtreffer: List<Marke>? = null,
    /** Commons-Angaben je Dateiname; fehlt ein Eintrag, zeigt die Liste den Platzhalter. */
    val bilder: Map<String, BildInfo> = emptyMap(),
    /** Gewaehlte Marke - am Handy eine eigene Seite, am Tablet die rechte Spalte. */
    val marke: Marke? = null,
    val themen: List<MarkenThema> = emptyList(),
    /** Geoeffnete Themenseite (liegt ueber der Marke). */
    val themaSeite: ThemaSeite? = null,
    /** Vollbild mit Wischen und Zoom ueber alle Marken der Liste mit Bild. */
    val vollbild: Boolean = false,
) {
    /** Was die Liste zeigt: Suchtreffer oder der Jahrgang. */
    val liste: List<Marke> get() = suchtreffer ?: jahrgang
    val hauptthema: MarkenThema? get() = themen.firstOrNull { it.haupt }
    fun bild(marke: Marke?): BildInfo? = marke?.commons_datei?.let { bilder[it] }
}

/** @param startMiNr Marke, die nach dem Laden gleich geoeffnet wird (Desktop: Aufruf mit --minr 1031). */
class AppViewModel(val plattform: Plattform, private val startMiNr: String? = null) : ViewModel() {
    private val _zustand = MutableStateFlow(AppZustand())
    val zustand: StateFlow<AppZustand> = _zustand

    private var katalog: Katalog? = null
    private val commons = CommonsBilder(plattform.http, plattform.datenOrdner)
    private var suchlauf: Job? = null

    init {
        viewModelScope.launch {
            try {
                val k = Katalog.oeffnen(plattform.datenOrdner).also { katalog = it }
                val z = _zustand.value
                val marken = withContext(Dispatchers.IO) { k.markenImJahr(z.gebiet, z.jahr) }
                val quelle = withContext(Dispatchers.IO) { k.quelle(z.gebiet, z.jahr) }
                _zustand.update { it.copy(laedt = false, jahrgang = marken, quelle = quelle) }
                bilderNachladen(marken)
                startMiNr?.let { nr -> marken.firstOrNull { it.mi_nr == nr }?.let(::waehlen) }
            } catch (e: Exception) {
                _zustand.update { it.copy(laedt = false, fehler = e.message ?: e.toString()) }
            }
        }
    }

    /** Bildadressen und Lizenzen - erst aus der Ablage, sonst von Commons. */
    private fun bilderNachladen(marken: List<Marke>) {
        val fehlend = marken.mapNotNull { it.commons_datei }.filter { it !in _zustand.value.bilder }
        if (fehlend.isEmpty()) return
        viewModelScope.launch {
            val infos = commons.infos(fehlend)
            _zustand.update { it.copy(bilder = it.bilder + infos) }
        }
    }

    fun waehlen(marke: Marke?) {
        _zustand.update { it.copy(marke = marke, themen = emptyList(), themaSeite = null) }
        if (marke == null) return
        viewModelScope.launch {
            val themen = withContext(Dispatchers.IO) { katalog?.themenZurMarke(marke.id).orEmpty() }
            // Nur eintragen, wenn inzwischen keine andere Marke gewaehlt wurde.
            _zustand.update { if (it.marke?.id == marke.id) it.copy(themen = themen) else it }
        }
    }

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
            _zustand.update { it.copy(suchtext = z.themaSeite?.thema?.titel.orEmpty(), suchtreffer = themaMarken) }
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
            z.vollbild -> { vollbild(false); true }
            z.themaSeite != null -> { themaSchliessen(); true }
            z.marke != null -> { waehlen(null); true }
            z.suchtreffer != null -> { suchen(""); true }
            else -> false
        }
    }

    fun oeffneWeb(url: String) = plattform.oeffneWeb(url)
}
