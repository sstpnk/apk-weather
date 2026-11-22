package tech.stpnk.weather.data.repo

import tech.stpnk.weather.data.cache.WeatherCache
import tech.stpnk.weather.data.cities.CityEntry
import tech.stpnk.weather.data.prefs.WeatherProvider
import tech.stpnk.weather.data.yandex.WeatherSource
import tech.stpnk.weather.domain.model.WeatherData

/** Результат загрузки: свежие данные, данные из кэша при ошибке сети, либо отказ. */
sealed interface LoadOutcome {
    data class Fresh(val data: WeatherData) : LoadOutcome
    data class Stale(val data: WeatherData, val error: Throwable?) : LoadOutcome
    data class Failed(val error: Throwable) : LoadOutcome
}

data class SourceChange(val from: WeatherProvider, val to: WeatherProvider)

private class RetryDeferredException(val retryAtMs: Long) : IllegalStateException("Повтор запроса запланирован")

/**
 * Репозиторий погоды: cache-first с TTL, отложенные повторы после сетевой ошибки
 * и переключение на следующий источник только после трёх неудачных повторов.
 */
class WeatherRepository(
    private val sources: Map<WeatherProvider, WeatherSource>,
    private val cache: WeatherCache,
    private val failover: SourceFailoverStore,
    private val maxAgeMs: Long = DEFAULT_MAX_AGE_MS,
    private val now: () -> Long = System::currentTimeMillis,
    private val onRetryScheduled: (Long) -> Unit = {},
    private val onRetryCancelled: () -> Unit = {},
    private val onSourceChanged: (SourceChange) -> Unit = {},
) {

    suspend fun load(
        city: CityEntry,
        preferred: WeatherProvider,
        force: Boolean = false,
        maxAgeMs: Long = this.maxAgeMs,
    ): LoadOutcome {
        var state = failover.load(preferred)
        val cacheKey = cacheKey(city, state.active)
        val cached = cache.read(cacheKey)
        val age = cache.ageMs(cacheKey)
        val isFresh = cached != null && age != null && age <= maxAgeMs

        if (!force && isFresh) return LoadOutcome.Fresh(cached!!)

        val currentTime = now()
        if (state.retryAtMs > currentTime) {
            val error = RetryDeferredException(state.retryAtMs)
            return if (cached != null) LoadOutcome.Stale(cached, error) else LoadOutcome.Failed(error)
        }

        return try {
            val data = source(state.active).fetch(city)
            cache.write(cacheKey, data)
            if (state.failedAttempts > 0 || state.retryAtMs > 0) {
                failover.save(state.copy(failedAttempts = 0, retryAtMs = 0))
                onRetryCancelled()
            }
            LoadOutcome.Fresh(data)
        } catch (t: Throwable) {
            val attempts = state.failedAttempts + 1
            if (attempts <= RETRY_DELAYS_MS.size) {
                val retryAt = currentTime + RETRY_DELAYS_MS[attempts - 1]
                failover.save(state.copy(failedAttempts = attempts, retryAtMs = retryAt))
                onRetryScheduled(retryAt)
                if (cached != null) LoadOutcome.Stale(cached, t) else LoadOutcome.Failed(t)
            } else {
                val next = nextProvider(state.active)
                state = SourceFailoverState(preferred = preferred, active = next)
                try {
                    val data = source(next).fetch(city)
                    cache.write(cacheKey(city, next), data)
                    failover.save(state)
                    onRetryCancelled()
                    onSourceChanged(SourceChange(from = previousProvider(next), to = next))
                    LoadOutcome.Fresh(data)
                } catch (fallbackError: Throwable) {
                    val retryAt = currentTime + RETRY_DELAYS_MS.first()
                    failover.save(state.copy(failedAttempts = 1, retryAtMs = retryAt))
                    onRetryScheduled(retryAt)
                    if (cached != null) LoadOutcome.Stale(cached, fallbackError) else LoadOutcome.Failed(fallbackError)
                }
            }
        }
    }

    private fun source(provider: WeatherProvider): WeatherSource =
        requireNotNull(sources[provider]) { "Не настроен источник $provider" }

    private fun cacheKey(city: CityEntry, provider: WeatherProvider): String = "${city.url}|$provider"

    private fun nextProvider(provider: WeatherProvider): WeatherProvider = when (provider) {
        WeatherProvider.YANDEX -> WeatherProvider.OPEN_METEO
        WeatherProvider.OPEN_METEO -> WeatherProvider.MET_NORWAY
        WeatherProvider.MET_NORWAY -> WeatherProvider.YANDEX
    }

    private fun previousProvider(provider: WeatherProvider): WeatherProvider = when (provider) {
        WeatherProvider.YANDEX -> WeatherProvider.MET_NORWAY
        WeatherProvider.OPEN_METEO -> WeatherProvider.YANDEX
        WeatherProvider.MET_NORWAY -> WeatherProvider.OPEN_METEO
    }

    companion object {
        const val DEFAULT_MAX_AGE_MS: Long = 30 * 60 * 1000L
        private val RETRY_DELAYS_MS = longArrayOf(5 * 60_000L, 15 * 60_000L, 30 * 60_000L)
    }
}
