package tech.stpnk.weather.data.repo

import tech.stpnk.weather.data.cache.WeatherCache
import tech.stpnk.weather.data.yandex.WeatherSource
import tech.stpnk.weather.domain.model.WeatherData

/** Результат загрузки: свежие данные, данные из кэша при ошибке сети, либо отказ. */
sealed interface LoadOutcome {
    data class Fresh(val data: WeatherData) : LoadOutcome
    data class Stale(val data: WeatherData, val error: Throwable?) : LoadOutcome
    data class Failed(val error: Throwable) : LoadOutcome
}

/**
 * Репозиторий погоды: cache-first с TTL, при ошибке сети — устаревший кэш.
 * Живые запросы к Яндексу редкие (TTL 30 мин) — бережём источник от капчи.
 */
class WeatherRepository(
    private val source: WeatherSource,
    private val cache: WeatherCache,
    private val maxAgeMs: Long = DEFAULT_MAX_AGE_MS,
) {

    suspend fun load(url: String, force: Boolean = false): LoadOutcome {
        val cached = cache.read(url)
        val age = cache.ageMs(url)
        val isFresh = cached != null && age != null && age <= maxAgeMs

        if (!force && isFresh) return LoadOutcome.Fresh(cached!!)

        return try {
            val data = source.fetch(url)
            cache.write(url, data)
            LoadOutcome.Fresh(data)
        } catch (t: Throwable) {
            if (cached != null) LoadOutcome.Stale(cached, t) else LoadOutcome.Failed(t)
        }
    }

    companion object {
        const val DEFAULT_MAX_AGE_MS: Long = 30 * 60 * 1000L
    }
}
