package tech.stpnk.weather.data.geo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import tech.stpnk.weather.data.cities.CityEntry
import java.io.IOException
import java.net.URLEncoder

@Serializable
data class GeoHit(
    val name: String,
    val region: String? = null,
    val country: String? = null,
    val lat: Double,
    val lon: Double,
    @SerialName("englishName") val englishName: String? = null,
)

@Serializable
private data class OpenMeteoResponse(val results: List<OpenMeteoHit>? = null)

@Serializable
private data class OpenMeteoHit(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String? = null,
    val admin1: String? = null,
)

@Serializable
private data class NominatimResponse(val name: String? = null, val display_name: String? = null)

/**
 * Геокодинг: поиск городов (Open-Meteo, без ключа), реверс координат (Nominatim),
 * подбор слага Яндекс.Погоды с проверкой живой страницы.
 */
class GeoSearchSource(private val client: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun search(query: String): List<GeoHit> = withContext(Dispatchers.IO) {
        val ru = geocode(query, "ru")
        if (ru.isEmpty()) return@withContext emptyList()
        val en = geocode(query, "en").associateBy { "${it.latitude},${it.longitude}" }
        ru.map { hit ->
            GeoHit(
                name = hit.name,
                region = hit.admin1,
                country = hit.country,
                lat = hit.latitude,
                lon = hit.longitude,
                englishName = en["${hit.latitude},${hit.longitude}"]?.name,
            )
        }
    }

    private fun geocode(query: String, language: String): List<OpenMeteoHit> {
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=${enc(query)}&count=8&language=$language&format=json"
        val body = get(url) ?: return emptyList()
        return runCatching { json.decodeFromString(OpenMeteoResponse.serializer(), body).results }.getOrNull().orEmpty()
    }

    /** Название населённого пункта по координатам (Nominatim, ru). */
    suspend fun reverse(lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        val url = "https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lon&format=jsonv2&accept-language=ru"
        val body = get(url) ?: return@withContext null
        runCatching { json.decodeFromString(NominatimResponse.serializer(), body) }.getOrNull()
            ?.name?.takeIf { it.isNotBlank() }
    }

    /** Строит запись города: кандидаты слага проверяются живой страницей Яндекса. */
    suspend fun toCity(hit: GeoHit): CityEntry? = withContext(Dispatchers.IO) {
        for (slug in slugCandidates(hit)) {
            val url = "https://yandex.ru/pogoda/ru/$slug"
            if (verify(url)) {
                return@withContext CityEntry(name = hit.name, url = url, lat = hit.lat, lon = hit.lon, region = hit.region)
            }
        }
        null
    }

    private fun slugCandidates(hit: GeoHit): List<String> {
        val list = mutableListOf<String>()
        hit.englishName?.let { list += slugify(it) }
        list += slugify(hit.name)
        list += transliterate(hit.name)
        return list.filter { it.isNotBlank() }.distinct()
    }

    private fun verify(url: String): Boolean = runCatching {
        client.newCall(
            Request.Builder().url(url).header("User-Agent", UA).build()
        ).execute().use { it.isSuccessful }
    }.getOrDefault(false)

    private fun get(url: String): String? = runCatching {
        client.newCall(
            Request.Builder().url(url).header("User-Agent", UA).header("Accept-Language", "ru-RU,ru;q=0.9").build()
        ).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body?.string()
        }
    }.getOrNull()

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private companion object {
        const val UA = "apk-weather/0.1 (personal project; https://github.com/sstpnk/apk-weather)"
    }
}

/** Нижний регистр, пробелы → дефисы, прочь всё лишнее. */
fun slugify(raw: String): String =
    raw.lowercase().replace(Regex("[^a-zа-я0-9]+"), "-").trim('-')

/** Русская транслитерация под слаги Яндекса (tula, sankt-peterburg, …). */
fun transliterate(raw: String): String {
    val table = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ё' to "e",
        'ж' to "zh", 'з' to "z", 'и' to "i", 'й' to "i", 'к' to "k", 'л' to "l", 'м' to "m",
        'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u",
        'ф' to "f", 'х' to "kh", 'ц' to "ts", 'ч' to "ch", 'ш' to "sh", 'щ' to "shch",
        'ъ' to "", 'ы' to "y", 'ь' to "", 'э' to "e", 'ю' to "yu", 'я' to "ya",
    )
    val sb = StringBuilder()
    for (ch in raw.lowercase()) {
        when {
            ch in table -> sb.append(table[ch])
            ch.isDigit() -> sb.append(ch)
            ch == ' ' || ch == '-' -> sb.append('-')
            else -> Unit
        }
    }
    return sb.toString().replace(Regex("-+"), "-").trim('-')
}
