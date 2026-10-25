package tech.stpnk.weather.data.repo

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.stpnk.weather.data.cache.WeatherCache
import tech.stpnk.weather.data.yandex.WeatherSource
import tech.stpnk.weather.domain.model.CurrentWeather
import tech.stpnk.weather.domain.model.WeatherData
import java.io.IOException
import java.nio.file.Files

class WeatherRepositoryTest {

    private val dir = Files.createTempDirectory("weather-cache").toFile()
    private val url = "https://yandex.ru/pogoda/ru/moscow"

    private fun data(temp: Int) = WeatherData(
        current = CurrentWeather(temperatureC = temp),
        fetchedAt = System.currentTimeMillis(),
        sourceUrl = url,
    )

    private class FakeSource(var result: () -> WeatherData) : WeatherSource {
        var calls = 0
        override suspend fun fetch(url: String): WeatherData {
            calls++
            return result()
        }
    }

    @Test
    fun `первый запрос идёт в сеть и пишет кэш`() = runBlocking {
        val source = FakeSource { data(10) }
        val repo = WeatherRepository(source, WeatherCache(dir))
        val outcome = repo.load(url)
        assertEquals(1, source.calls)
        assertTrue(outcome is LoadOutcome.Fresh)
        assertEquals(10, (outcome as LoadOutcome.Fresh).data.current?.temperatureC)
    }

    @Test
    fun `свежий кэш не дёргает сеть`() = runBlocking {
        val source = FakeSource { data(11) }
        val repo = WeatherRepository(source, WeatherCache(dir))
        repo.load(url)
        val second = repo.load(url)
        assertEquals(1, source.calls)
        assertTrue(second is LoadOutcome.Fresh)
    }

    @Test
    fun `force игнорирует кэш`() = runBlocking {
        val source = FakeSource { data(12) }
        val repo = WeatherRepository(source, WeatherCache(dir))
        repo.load(url)
        repo.load(url, force = true)
        assertEquals(2, source.calls)
    }

    @Test
    fun `ошибка сети возвращает устаревший кэш`() = runBlocking {
        val source = FakeSource { data(13) }
        val repo = WeatherRepository(source, WeatherCache(dir))
        repo.load(url)
        source.result = { throw IOException("нет сети") }
        val outcome = repo.load(url, force = true)
        assertTrue("ожидался Stale", outcome is LoadOutcome.Stale)
        assertEquals(13, (outcome as LoadOutcome.Stale).data.current?.temperatureC)
    }

    @Test
    fun `ошибка без кэша — Failed`() = runBlocking {
        val emptyDir = Files.createTempDirectory("weather-cache-empty").toFile()
        val source = FakeSource { throw IOException("нет сети") }
        val repo = WeatherRepository(source, WeatherCache(emptyDir))
        val outcome = repo.load(url)
        assertTrue("ожидался Failed", outcome is LoadOutcome.Failed)
    }
}
