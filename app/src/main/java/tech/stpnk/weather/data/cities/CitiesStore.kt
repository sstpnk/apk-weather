package tech.stpnk.weather.data.cities

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import tech.stpnk.weather.data.prefs.KeyValueStore

@Serializable
data class CityEntry(
    val name: String,
    val url: String,
    val lat: Double? = null,
    val lon: Double? = null,
    val region: String? = null,
)

/** Список городов и выбранный город — в SharedPreferences как JSON. */
class CitiesStore(private val kv: KeyValueStore) {

    private val json = Json { ignoreUnknownKeys = true }

    fun cities(): List<CityEntry> = runCatching {
        val raw = kv.getString(KEY_CITIES) ?: return@runCatching defaultList()
        json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(CityEntry.serializer()), raw)
    }.getOrNull()?.takeIf { it.isNotEmpty() } ?: defaultList()

    fun selected(): CityEntry {
        val selectedUrl = kv.getString(KEY_SELECTED)
        return cities().firstOrNull { it.url == selectedUrl } ?: cities().first()
    }

    fun select(url: String) = kv.putString(KEY_SELECTED, url)

    fun add(city: CityEntry) {
        val list = cities()
        if (list.none { it.url == city.url }) save(list + city)
    }

    fun remove(url: String) {
        val list = cities().filterNot { it.url == url }
        save(list.ifEmpty { defaultList() })
        if (selected().url == url) select(cities().first().url)
    }

    private fun save(list: List<CityEntry>) {
        kv.putString(KEY_CITIES, json.encodeToString(kotlinx.serialization.builtins.ListSerializer(CityEntry.serializer()), list))
    }

    private fun defaultList() = listOf(DEFAULT)

    companion object {
        val DEFAULT = CityEntry(
            name = "Москва",
            url = "https://yandex.ru/pogoda/ru/moscow",
            lat = 55.7522,
            lon = 37.6156,
        )
        private const val KEY_CITIES = "cities_v1"
        private const val KEY_SELECTED = "selected_city_url"
    }
}
