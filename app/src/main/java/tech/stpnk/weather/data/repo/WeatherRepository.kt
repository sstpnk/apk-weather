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

/**
 * Репозиторий погоды: cache-first с TTL и деградацией на устаревший кэш.
 * Источник — только выбранный пользователем (без автоматического переключения):
 * если данные не получены, отдаём свой кэш; нечего отдавать — ошибка.
 */
class WeatherRepository(
    private val sources: Map<WeatherProvider, WeatherSource>,
    private val cache: WeatherCache,
    private val maxAgeMs: Long = DEFAULT_MAX_AGE_MS,
) {

    suspend fun load(
        city: CityEntry,
        preferred: WeatherProvider,
        force: Boolean = false,
        maxAgeMs: Long = this.maxAgeMs,
    ): LoadOutcome {
        val key = cacheKey(city, preferred)
        val cached = cache.read(key)
        val age = cache.ageMs(key)
        val isFresh = cached != null && age != null && age <= maxAgeMs

        if (!force && isFresh) return LoadOutcome.Fresh(cached!!)

        return try {
            val data = source(preferred).fetch(city)
            cache.write(key, data)
            LoadOutcome.Fresh(data)
        } catch (t: Throwable) {
            if (cached != null) LoadOutcome.Stale(cached, t) else LoadOutcome.Failed(t)
        }
    }

    /** Данные из кэша по выбранному источнику (без сети). */
    fun cached(city: CityEntry, provider: WeatherProvider): WeatherData? = cache.read(cacheKey(city, provider))

    private fun source(provider: WeatherProvider): WeatherSource =
        requireNotNull(sources[provider]) { "Не настроен источник $provider" }

    private fun cacheKey(city: CityEntry, provider: WeatherProvider): String = "${city.url}|$provider"

    companion object {
        const val DEFAULT_MAX_AGE_MS: Long = 30 * 60 * 1000L
    }
}
