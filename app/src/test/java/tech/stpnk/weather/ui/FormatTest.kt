package tech.stpnk.weather.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatTest {

    @Test
    fun `температура со знаком`() {
        assertEquals("+7°", formatTemp(7))
        assertEquals("-3°", formatTemp(-3))
        assertEquals("0°", formatTemp(0))
        assertEquals("—", formatTemp(null))
    }

    @Test
    fun `направление ветра`() {
        assertEquals("ЮВ", windDirectionRu("SOUTHEAST"))
        assertEquals("В", windDirectionRu("EAST"))
        assertEquals("СЗ", windDirectionRu("northwest"))
        assertEquals(null, windDirectionRu(null))
    }

    @Test
    fun `условия на русском`() {
        assertEquals("Ясно", conditionTextRu("CLEAR"))
        assertEquals("Пасмурно", conditionTextRu("OVERCAST"))
        assertEquals("Неизвестно", conditionTextRu(null, "неизвестно"))
    }

    @Test
    fun `баннер осадков`() {
        assertEquals("В ближайшие 2 часа осадков не ожидается", precipitationOutlook(listOf("12:00: +9°, ясно, Ощущается как +5°.")))
        assertEquals("В ближайшие часы возможны осадки", precipitationOutlook(listOf("12:00: +9°, дождь, Ощущается как +5°.")))
        assertEquals(null, precipitationOutlook(emptyList()))
    }

    @Test
    fun `иконки маппятся по коду и тексту`() {
        assertTrue(weatherIconRes("CLEAR", null, true) != weatherIconRes("CLEAR", null, false))
        assertEquals(weatherIconRes("THUNDERSTORM", null, true), weatherIconRes(null, "гроза", true))
    }
}
