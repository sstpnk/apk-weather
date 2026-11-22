package tech.stpnk.weather.data.repo

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import tech.stpnk.weather.data.prefs.KeyValueStore
import tech.stpnk.weather.data.prefs.WeatherProvider

@Serializable
data class SourceFailoverState(
    val preferred: WeatherProvider = WeatherProvider.YANDEX,
    val active: WeatherProvider = WeatherProvider.YANDEX,
    val failedAttempts: Int = 0,
    val retryAtMs: Long = 0,
)

/** Состояние повторов переживает перезапуск приложения и фоновые обновления. */
class SourceFailoverStore(private val kv: KeyValueStore) {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(preferred: WeatherProvider): SourceFailoverState {
        val saved = runCatching {
            kv.getString(KEY)?.let { json.decodeFromString(SourceFailoverState.serializer(), it) }
        }.getOrNull()
        return saved?.takeIf { it.preferred == preferred } ?: SourceFailoverState(preferred, preferred)
    }

    fun save(state: SourceFailoverState) {
        kv.putString(KEY, json.encodeToString(SourceFailoverState.serializer(), state))
    }

    private companion object {
        const val KEY = "weather_source_failover_v1"
    }
}
