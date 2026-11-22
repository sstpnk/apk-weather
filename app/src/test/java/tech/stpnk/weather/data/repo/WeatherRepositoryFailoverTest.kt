package tech.stpnk.weather.data.repo

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.stpnk.weather.data.cache.WeatherCache
import tech.stpnk.weather.data.cities.CityEntry
import tech.stpnk.weather.data.prefs.KeyValueStore
import tech.stpnk.weather.data.prefs.WeatherProvider
import tech.stpnk.weather.data.yandex.WeatherSource
import tech.stpnk.weather.domain.model.CurrentWeather
import tech.stpnk.weather.domain.model.WeatherData
import java.io.IOException
import java.nio.file.Files

class WeatherRepositoryFailoverTest {
    private val city = CityEntry("Москва", "https://yandex.ru/pogoda/ru/moscow", 55.75, 37.62)

    @Test
    fun `переключается только после трёх отложенных повторов`() = runBlocking {
        var clock = 1_000L
        val yandex = FakeSource { throw IOException("Яндекс недоступен") }
        val openMeteo = FakeSource { WeatherData(current = CurrentWeather(temperatureC = 9)) }
        val changes = mutableListOf<SourceChange>()
        val retries = mutableListOf<Long>()
        val repo = WeatherRepository(
            sources = mapOf(
                WeatherProvider.YANDEX to yandex,
                WeatherProvider.OPEN_METEO to openMeteo,
                WeatherProvider.MET_NORWAY to openMeteo,
            ),
            cache = WeatherCache(Files.createTempDirectory("weather-failover").toFile()),
            failover = SourceFailoverStore(FakeKeyValueStore()),
            now = { clock },
            onRetryScheduled = { retries += it },
            onSourceChanged = { changes += it },
        )

        repeat(3) { attempt ->
            assertTrue(repo.load(city, WeatherProvider.YANDEX, force = true) is LoadOutcome.Failed)
            clock = retries[attempt]
        }
        val outcome = repo.load(city, WeatherProvider.YANDEX, force = true)

        assertTrue(outcome is LoadOutcome.Fresh)
        assertEquals(4, yandex.calls)
        assertEquals(1, openMeteo.calls)
        assertEquals(listOf(SourceChange(WeatherProvider.YANDEX, WeatherProvider.OPEN_METEO)), changes)
    }

    private class FakeSource(private val response: () -> WeatherData) : WeatherSource {
        var calls = 0
        override suspend fun fetch(city: CityEntry): WeatherData {
            calls++
            return response()
        }
    }

    private class FakeKeyValueStore : KeyValueStore {
        private val values = mutableMapOf<String, String>()
        override fun getString(key: String): String? = values[key]
        override fun putString(key: String, value: String) { values[key] = value }
    }
}
