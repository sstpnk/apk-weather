package tech.stpnk.weather.data.yandex

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import tech.stpnk.weather.domain.model.WeatherData
import java.io.IOException

/** Источник данных: веб-страница pogoda.yandex.ru (RSC/flight). */
interface WeatherSource {
    suspend fun fetch(url: String): WeatherData
}

class YandexWebSource(
    private val client: OkHttpClient,
    private val parser: FlightParser = FlightParser(),
) : WeatherSource {

    override suspend fun fetch(url: String): WeatherData = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html")
            .header("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.8")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} для $url")
            val html = response.body?.string() ?: throw IOException("Пустой ответ для $url")
            val payload = FlightExtractor.extract(html)
            if (payload.isEmpty()) throw IOException("Не найден flight-поток в ответе для $url")
            parser.parse(payload, sourceUrl = url)
        }
    }

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
    }
}
