package tech.stpnk.weather.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import tech.stpnk.weather.Graph
import tech.stpnk.weather.data.repo.LoadOutcome
import tech.stpnk.weather.domain.model.WeatherData

sealed interface MainUiState {
    data object Loading : MainUiState
    data class Content(
        val data: WeatherData,
        val stale: Boolean = false,
        val refreshing: Boolean = false,
    ) : MainUiState

    data class Error(val message: String) : MainUiState
}

class MainViewModel : ViewModel() {

    private val repository = Graph.repository

    private val _state = MutableStateFlow<MainUiState>(MainUiState.Loading)
    val state: StateFlow<MainUiState> = _state

    init {
        refresh()
    }

    fun refresh(force: Boolean = false) {
        val previous = _state.value
        _state.value = if (previous is MainUiState.Content) {
            previous.copy(refreshing = true)
        } else {
            MainUiState.Loading
        }
        viewModelScope.launch {
            _state.value = when (val outcome = repository.load(Graph.DEFAULT_CITY_URL, force = force)) {
                is LoadOutcome.Fresh -> MainUiState.Content(outcome.data)
                is LoadOutcome.Stale -> MainUiState.Content(outcome.data, stale = true)
                is LoadOutcome.Failed -> MainUiState.Error(outcome.error.message ?: "Не удалось загрузить прогноз")
            }
        }
    }
}
