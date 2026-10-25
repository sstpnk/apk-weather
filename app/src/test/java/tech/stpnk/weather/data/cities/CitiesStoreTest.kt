package tech.stpnk.weather.data.cities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.stpnk.weather.data.prefs.KeyValueStore

class CitiesStoreTest {

    private class FakeKv : KeyValueStore {
        private val map = HashMap<String, String?>()
        override fun getString(key: String): String? = map[key]
        override fun putString(key: String, value: String) {
            map[key] = value
        }
    }

    @Test
    fun `по умолчанию Москва`() {
        val store = CitiesStore(FakeKv())
        assertEquals(1, store.cities().size)
        assertEquals("Москва", store.selected().name)
        assertEquals("https://yandex.ru/pogoda/ru/moscow", store.selected().url)
    }

    @Test
    fun `добавление и выбор города`() {
        val store = CitiesStore(FakeKv())
        val tula = CityEntry("Тула", "https://yandex.ru/pogoda/ru/tula", lat = 54.2, lon = 37.6)
        store.add(tula)
        store.select(tula.url)
        assertEquals(2, store.cities().size)
        assertEquals("Тула", store.selected().name)
        store.add(tula) // дубликат не добавляется
        assertEquals(2, store.cities().size)
    }

    @Test
    fun `удаление выбранного возвращает дефолт`() {
        val store = CitiesStore(FakeKv())
        val tula = CityEntry("Тула", "https://yandex.ru/pogoda/ru/tula")
        store.add(tula)
        store.select(tula.url)
        store.remove(tula.url)
        assertEquals(1, store.cities().size)
        assertEquals("Москва", store.selected().name)
    }

    @Test
    fun `удаление невыбранного не меняет выбор`() {
        val store = CitiesStore(FakeKv())
        val tula = CityEntry("Тула", "https://yandex.ru/pogoda/ru/tula")
        store.add(tula)
        store.remove(tula.url)
        assertEquals("Москва", store.selected().name)
        assertTrue(store.cities().none { it.name == "Тула" })
    }
}
