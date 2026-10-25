package tech.stpnk.weather.data.yandex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.zip.GZIPInputStream

/**
 * Герметичные тесты парсера на сохранённой странице (fixtures/yandex_moscow.html.gz).
 * Живой сети в тестах нет.
 */
class FlightParserTest {

    private val payload: String by lazy {
        val stream = javaClass.classLoader!!.getResourceAsStream("fixtures/yandex_moscow.html.gz")
            ?: error("fixture yandex_moscow.html.gz не найден")
        val html = stream.use { GZIPInputStream(it).readBytes().toString(Charsets.UTF_8) }
        FlightExtractor.extract(html)
    }

    private val data by lazy { FlightParser().parse(payload, sourceUrl = "fixture://moscow") }

    @Test
    fun `flight extractor находит поток`() {
        assertTrue("payload слишком короткий: ${payload.length}", payload.length > 100_000)
        assertTrue(payload.contains("\"temperatureInCelsius\""))
    }

    @Test
    fun `текущая погода разбирается`() {
        val current = assertNotNull("current отсутствует", data.current).let { data.current!! }
        assertTrue("температура вне разумного диапазона: ${current.temperatureC}", current.temperatureC in -60..60)
        assertNotNull("нет условия", current.condition)
        assertNotNull("нет давления", current.pressureMmHg)
        assertNotNull("нет влажности", current.humidity)
    }

    @Test
    fun `место определяется`() {
        val place = assertNotNull("place отсутствует", data.place).let { data.place!! }
        assertEquals(213, place.geoId)
        assertEquals("Москва", place.city)
        assertEquals("Россия", place.country)
    }

    @Test
    fun `десять дней с частями суток`() {
        assertEquals("ожидалось 10 дней", 10, data.days.size)
        val future = data.days.drop(1).first { it.parts.keys.containsAll(listOf("m", "d", "e", "n")) }
        listOf("m", "d", "e", "n").forEach { part ->
            assertNotNull("у дня нет температуры части $part", future.parts[part]?.temperatureC)
        }
    }

    @Test
    fun `почасовая лента не пуста и корректна`() {
        assertTrue("часовых ячеек слишком мало: ${data.hourly.size}", data.hourly.size >= 20)
        data.hourly.forEach { hour ->
            assertTrue("время не по формату: ${hour.time}", Regex("""\d{1,2}:\d{2}""").matches(hour.time))
        }
    }
}
