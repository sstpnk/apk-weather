package tech.stpnk.weather.ui.cities

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tech.stpnk.weather.Graph
import tech.stpnk.weather.data.cities.CityEntry
import tech.stpnk.weather.data.geo.GeoHit

data class CitiesUiState(
    val cities: List<CityEntry> = emptyList(),
    val selectedUrl: String = "",
    val query: String = "",
    val results: List<GeoHit> = emptyList(),
    val searching: Boolean = false,
    val adding: Boolean = false,
    val locationWorking: Boolean = false,
    val error: String? = null,
    val addedAndClosed: Boolean = false,
)

class CitiesViewModel : ViewModel() {

    private val citiesStore = Graph.cities
    private val geo = Graph.geo

    private val _state = MutableStateFlow(CitiesUiState(cities = citiesStore.cities(), selectedUrl = citiesStore.selected().url))
    val state: StateFlow<CitiesUiState> = _state

    private var searchJob: Job? = null

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query, error = null) }
        searchJob?.cancel()
        if (query.trim().length < 2) {
            _state.update { it.copy(results = emptyList(), searching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(350)
            _state.update { it.copy(searching = true) }
            runCatching { geo.search(query.trim()) }
                .onSuccess { hits -> _state.update { it.copy(searching = false, results = hits) } }
                .onFailure { t -> _state.update { it.copy(searching = false, error = t.message ?: "Ошибка поиска") } }
        }
    }

    fun add(hit: GeoHit) {
        viewModelScope.launch {
            _state.update { it.copy(adding = true, error = null) }
            val city = runCatching { geo.toCity(hit) }.getOrNull()
            if (city == null) {
                _state.update { it.copy(adding = false, error = "Город не найден на Яндекс Погоде") }
                return@launch
            }
            citiesStore.add(city)
            citiesStore.select(city.url)
            _state.update {
                it.copy(adding = false, addedAndClosed = true, cities = citiesStore.cities(), selectedUrl = citiesStore.selected().url)
            }
        }
    }

    fun select(url: String) {
        citiesStore.select(url)
        _state.update { it.copy(selectedUrl = citiesStore.selected().url) }
    }

    fun remove(url: String) {
        citiesStore.remove(url)
        _state.update { it.copy(cities = citiesStore.cities(), selectedUrl = citiesStore.selected().url) }
    }

    fun useMyLocation() {
        viewModelScope.launch {
            _state.update { it.copy(locationWorking = true, error = null) }
            val coords = runCatching { Graph.location.currentCoords() }.getOrNull()
            if (coords == null) {
                _state.update { it.copy(locationWorking = false, error = "Не удалось определить координаты") }
                return@launch
            }
            val name = runCatching { geo.reverse(coords.first, coords.second) }.getOrNull()
            if (name.isNullOrBlank()) {
                _state.update { it.copy(locationWorking = false, error = "Не удалось определить город") }
                return@launch
            }
            add(GeoHit(name = name, lat = coords.first, lon = coords.second))
            _state.update { it.copy(locationWorking = false) }
        }
    }
}
