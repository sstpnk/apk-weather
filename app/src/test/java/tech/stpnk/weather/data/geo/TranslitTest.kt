package tech.stpnk.weather.data.geo

import org.junit.Assert.assertEquals
import org.junit.Test

class TranslitTest {

    @Test
    fun `транслитерация городов`() {
        assertEquals("tula", transliterate("Тула"))
        assertEquals("sankt-peterburg", transliterate("Санкт-Петербург"))
        assertEquals("moskva", transliterate("Москва"))
        assertEquals("nizhnii-novgorod", transliterate("Нижний Новгород"))
    }

    @Test
    fun `slugify латиницы`() {
        assertEquals("saint-petersburg", slugify("Saint Petersburg"))
        assertEquals("moscow", slugify("Moscow"))
        assertEquals("nizhny-novgorod", slugify("Nizhny  Novgorod!"))
    }
}
