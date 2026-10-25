package tech.stpnk.weather.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.stpnk.weather.domain.model.CurrentWeather
import tech.stpnk.weather.domain.model.DayForecast
import tech.stpnk.weather.domain.model.DayPartForecast
import tech.stpnk.weather.domain.model.GeoPlace
import tech.stpnk.weather.domain.model.HourForecast
import tech.stpnk.weather.domain.model.WeatherData

/** Чистые построители текстов виджетов/уведомлений. */
class WidgetTextTest {

    private fun data(): WeatherData = WeatherData(
        place = GeoPlace(geoId = 213, city = "Москва", country = "Россия"),
        current = CurrentWeather(
            temperatureC = 11, feelsLikeC = 8, windSpeedMs = 3.0, windDirection = "EAST",
            pressureMmHg = 758, humidity = 50, condition = "CLEAR", daytime = true,
        ),
        hourly = listOf(
            HourForecast("08:00", 4, 26, "08:00: +4°, ясно, Ощущается как +2°."),
            HourForecast("12:00", 9, 26, "12:00: +9°, ясно, Ощущается как +5°."),
            HourForecast("15:00", 11, 26, "15:00: +11°, ясно, Ощущается как +8°."),
        ),
        days = listOf(
            DayForecast(0, "Сегодня, 25 октября", isRed = true, parts = mapOf("m" to DayPartForecast(temperatureC = 6))),
            DayForecast(
                1, "Завтра, 26 октября",
                parts = mapOf(
                    "m" to DayPartForecast(temperatureC = 4),
                    "d" to DayPartForecast(temperatureC = 9),
                    "e" to DayPartForecast(temperatureC = 7),
                    "n" to DayPartForecast(temperatureC = 4),
                ),
            ),
        ),
    )

    @Test
    fun `сводка большого виджета`() {
        assertEquals("Ясно · Днём +11° · Ночью +4°", WidgetRenderer.bigSummary(data()))
    }

    @Test
    fun `текст ежедневного уведомления`() {
        assertEquals("+4°…+11° · Ясно · Ветер 3 м/с", WidgetRenderer.dailyText(data()))
        assertEquals("Погода на сегодня · Москва", WidgetRenderer.dailyTitle(data()))
    }

    @Test
    fun `текст виджета в шторке`() {
        assertEquals("Ощущается как: +8° · Ветер 3 м/с, В · Москва", WidgetRenderer.shadeText(data()))
        assertEquals("+11° · Ясно", WidgetRenderer.formatShadeTitle(data()))
    }

    @Test
    fun `дата и город в большом виджете`() {
        assertTrue(WidgetRenderer.bigDateCity(data()).endsWith(" · Москва"))
    }

    @Test
    fun `пустые данные не ломают тексты`() {
        val empty = WeatherData()
        assertEquals("— · Днём — · Ночью —", WidgetRenderer.bigSummary(empty))
        assertEquals("Погода на сегодня · —", WidgetRenderer.dailyTitle(empty))
    }
}
