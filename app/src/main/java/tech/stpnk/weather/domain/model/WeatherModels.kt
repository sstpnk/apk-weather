package tech.stpnk.weather.domain.model

import kotlinx.serialization.Serializable

/** Место: идентификатор Яндекса + иерархия (для шапки «город / район»). */
@Serializable
data class GeoPlace(
    val geoId: Int? = null,
    val city: String? = null,
    val district: String? = null,
    val province: String? = null,
    val country: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
)

/** Текущая погода (из fact-словаря страницы). */
@Serializable
data class CurrentWeather(
    val temperatureC: Int,
    val feelsLikeC: Int? = null,
    val waterTempC: Int? = null,
    val windSpeedMs: Double? = null,
    val windGustMs: Double? = null,
    val windDirection: String? = null,
    val pressureMmHg: Int? = null,
    val humidity: Int? = null,
    val condition: String? = null,
    val daytime: Boolean? = null,
    val uvIndex: Int? = null,
    val visibilityM: Int? = null,
)

/** Ячейка почасовой ленты. */
@Serializable
data class HourForecast(
    val time: String,
    val temperatureC: Int? = null,
    val icon: Int? = null,
    val summary: String? = null,
)

/** Часть суток внутри дня: m = утро, d = день, e = вечер, n = ночь. */
@Serializable
data class DayPartForecast(
    val temperatureC: Int? = null,
    val feelsLikeC: Int? = null,
    val icon: Int? = null,
    val conditionText: String? = null,
    val windText: String? = null,
    val windDirection: String? = null,
)

/** Строка прогноза на день. parts ключ — код части суток. */
@Serializable
data class DayForecast(
    val epochDay: Long,
    val label: String? = null,
    val isRed: Boolean = false,
    val parts: Map<String, DayPartForecast> = emptyMap(),
)

/** Полный снимок данных одного города, сохраняется в кэш как есть. */
@Serializable
data class WeatherData(
    val place: GeoPlace? = null,
    val current: CurrentWeather? = null,
    val hourly: List<HourForecast> = emptyList(),
    val days: List<DayForecast> = emptyList(),
    val fetchedAt: Long = 0,
    val sourceUrl: String = "",
)
