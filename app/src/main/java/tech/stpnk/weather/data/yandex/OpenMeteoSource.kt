package tech.stpnk.weather.data.yandex

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import tech.stpnk.weather.data.cities.CityEntry
import tech.stpnk.weather.domain.model.CurrentWeather
import tech.stpnk.weather.domain.model.DayForecast
import tech.stpnk.weather.domain.model.DayPartForecast
import tech.stpnk.weather.domain.model.GeoPlace
import tech.stpnk.weather.domain.model.HourForecast
import tech.stpnk.weather.domain.model.WeatherData
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.roundToInt

/** Безключевой прогноз Open-Meteo. Поля запрашиваются в объёме, нужном экрану и виджетам. */
class OpenMeteoSource(private val client: OkHttpClient) : WeatherSource {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun fetch(city: CityEntry): WeatherData = withContext(Dispatchers.IO) {
        val lat = requireNotNull(city.lat) { "Нет координат для резервного источника" }
        val lon = requireNotNull(city.lon) { "Нет координат для резервного источника" }
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m,surface_pressure,precipitation,weather_code,wind_speed_10m,wind_direction_10m,is_day,visibility" +
            "&hourly=temperature_2m,apparent_temperature,precipitation_probability,weather_code,wind_speed_10m,wind_direction_10m,uv_index" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,uv_index_max,precipitation_probability_max" +
            "&timezone=auto&forecast_days=10"
        val body = client.newCall(Request.Builder().url(url).header("User-Agent", USER_AGENT).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Open-Meteo: HTTP ${response.code}")
            response.body?.string() ?: throw IOException("Open-Meteo: пустой ответ")
        }
        parse(city, body)
    }

    internal fun parse(city: CityEntry, body: String): WeatherData {
        val root = json.decodeFromString(OpenMeteoResponse.serializer(), body)
        val current = requireNotNull(root.current) { "Open-Meteo: нет current" }
        val hourly = root.hourly?.toForecast().orEmpty()
        val days = root.daily?.toForecast(hourly).orEmpty()
        return WeatherData(
            place = GeoPlace(city = city.name, province = city.region, lat = city.lat, lon = city.lon),
            current = CurrentWeather(
                temperatureC = current.temperature.roundToInt(),
                feelsLikeC = current.apparentTemperature?.roundToInt(),
                windSpeedMs = current.windSpeed?.div(3.6),
                windDirection = direction(current.windDirection),
                pressureMmHg = current.pressure?.div(1.33322)?.roundToInt(),
                humidity = current.humidity,
                condition = condition(current.weatherCode),
                daytime = current.isDay?.let { it == 1 },
                visibilityM = current.visibility?.roundToInt(),
            ),
            hourly = hourly,
            days = days,
            fetchedAt = System.currentTimeMillis(),
            sourceUrl = "https://api.open-meteo.com/",
        )
    }

    @Serializable private data class OpenMeteoResponse(
        val current: Current? = null,
        val hourly: Hourly? = null,
        val daily: Daily? = null,
    )
    @Serializable private data class Current(
        @SerialName("temperature_2m") val temperature: Double,
        @SerialName("apparent_temperature") val apparentTemperature: Double? = null,
        @SerialName("relative_humidity_2m") val humidity: Int? = null,
        @SerialName("surface_pressure") val pressure: Double? = null,
        @SerialName("weather_code") val weatherCode: Int? = null,
        @SerialName("wind_speed_10m") val windSpeed: Double? = null,
        @SerialName("wind_direction_10m") val windDirection: Double? = null,
        @SerialName("is_day") val isDay: Int? = null,
        val visibility: Double? = null,
    )
    @Serializable private data class Hourly(
        val time: List<String> = emptyList(),
        @SerialName("temperature_2m") val temperature: List<Double?> = emptyList(),
        @SerialName("apparent_temperature") val apparentTemperature: List<Double?> = emptyList(),
        @SerialName("weather_code") val weatherCode: List<Int?> = emptyList(),
        @SerialName("wind_speed_10m") val windSpeed: List<Double?> = emptyList(),
        @SerialName("wind_direction_10m") val windDirection: List<Double?> = emptyList(),
    ) {
        fun toForecast(): List<HourForecast> = time.mapIndexedNotNull { index, value ->
            val parsed = runCatching { LocalDateTime.parse(value) }.getOrNull() ?: return@mapIndexedNotNull null
            HourForecast(
                time = "%02d:%02d".format(parsed.hour, parsed.minute),
                temperatureC = temperature.getOrNull(index)?.roundToInt(),
                summary = conditionText(weatherCode.getOrNull(index)),
            )
        }.take(48)
    }
    @Serializable private data class Daily(
        val time: List<String> = emptyList(),
        @SerialName("weather_code") val weatherCode: List<Int?> = emptyList(),
        @SerialName("temperature_2m_max") val maxTemperature: List<Double?> = emptyList(),
        @SerialName("temperature_2m_min") val minTemperature: List<Double?> = emptyList(),
        val sunrise: List<String?> = emptyList(),
        val sunset: List<String?> = emptyList(),
        @SerialName("uv_index_max") val maxUv: List<Double?> = emptyList(),
    ) {
        fun toForecast(hourly: List<HourForecast>): List<DayForecast> = time.mapIndexedNotNull { index, value ->
            val date = runCatching { LocalDate.parse(value) }.getOrNull() ?: return@mapIndexedNotNull null
            val code = weatherCode.getOrNull(index)
            DayForecast(
                epochDay = date.toEpochDay(),
                label = if (index == 0) "Сегодня" else null,
                parts = mapOf(
                    "d" to DayPartForecast(
                        temperatureC = maxTemperature.getOrNull(index)?.roundToInt(),
                        feelsLikeC = minTemperature.getOrNull(index)?.roundToInt(),
                        conditionText = conditionText(code),
                    ),
                    "n" to DayPartForecast(
                        temperatureC = minTemperature.getOrNull(index)?.roundToInt(),
                        conditionText = conditionText(code),
                    ),
                ),
                sunrise = sunrise.getOrNull(index)?.timePart(),
                sunset = sunset.getOrNull(index)?.timePart(),
                uvText = maxUv.getOrNull(index)?.let(::uvText),
            )
        }
    }

    private companion object {
        const val USER_AGENT = "apk-weather/0.1 (https://github.com/sstpnk/apk-weather)"
    }
}

private fun String.timePart(): String? = runCatching { LocalDateTime.parse(this).toLocalTime().toString().take(5) }.getOrNull()
internal fun direction(degrees: Double?): String? = degrees?.let {
    arrayOf("NORTH", "NORTHEAST", "EAST", "SOUTHEAST", "SOUTH", "SOUTHWEST", "WEST", "NORTHWEST")[((it + 22.5) / 45).toInt() % 8]
}
internal fun condition(code: Int?): String? = when (code) {
    0 -> "CLEAR"; 1 -> "PARTLY_CLOUDY"; 2 -> "CLOUDY"; 3 -> "OVERCAST"
    45, 48 -> "FOG"; in 51..67, in 80..82 -> "RAIN"; in 71..77, 85, 86 -> "SNOW"
    in 95..99 -> "THUNDERSTORM"; else -> null
}
internal fun conditionText(code: Int?): String? = when (condition(code)) {
    "CLEAR" -> "Ясно"; "PARTLY_CLOUDY" -> "Малооблачно"; "CLOUDY" -> "Облачно"
    "OVERCAST" -> "Пасмурно"; "FOG" -> "Туман"; "RAIN" -> "Дождь"; "SNOW" -> "Снег"
    "THUNDERSTORM" -> "Гроза"; else -> null
}
internal fun uvText(value: Double): String = "${value.roundToInt()}, " + when {
    value < 3 -> "низкий"; value < 6 -> "умеренный"; value < 8 -> "высокий"; value < 11 -> "очень высокий"; else -> "экстремальный"
}
