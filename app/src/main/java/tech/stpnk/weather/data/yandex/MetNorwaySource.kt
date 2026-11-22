package tech.stpnk.weather.data.yandex

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
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
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.math.roundToInt

/** Второй безключевой резерв — официальный Locationforecast от MET Norway. */
class MetNorwaySource(private val client: OkHttpClient) : WeatherSource {
    override suspend fun fetch(city: CityEntry): WeatherData = withContext(Dispatchers.IO) {
        val lat = requireNotNull(city.lat) { "Нет координат для резервного источника" }
        val lon = requireNotNull(city.lon) { "Нет координат для резервного источника" }
        val url = "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=$lat&lon=$lon"
        val body = client.newCall(Request.Builder().url(url).header("User-Agent", USER_AGENT).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("MET Norway: HTTP ${response.code}")
            response.body?.string() ?: throw IOException("MET Norway: пустой ответ")
        }
        parse(city, body)
    }

    internal fun parse(city: CityEntry, body: String): WeatherData {
        val rows = Json.parseToJsonElement(body).jsonObject["properties"]?.jsonObject?.get("timeseries")?.jsonArray
            ?: error("MET Norway: нет timeseries")
        val points = rows.mapNotNull { element ->
            val objectValue = element as? JsonObject ?: return@mapNotNull null
            point(objectValue)
        }
        val first = points.firstOrNull() ?: error("MET Norway: пустой timeseries")
        val hourly = points.take(48).map { point ->
            HourForecast(
                time = "%02d:%02d".format(point.time.hour, point.time.minute),
                temperatureC = point.temperature.roundToInt(),
                summary = conditionText(point.code),
            )
        }
        val days = points.groupBy { it.time.withOffsetSameInstant(ZoneOffset.UTC).toLocalDate().toEpochDay() }
            .entries.take(10).map { (epochDay, day) ->
                val dayPoint = day.minByOrNull { kotlin.math.abs(it.time.hour - 13) } ?: day.first()
                val nightPoint = day.minByOrNull { kotlin.math.abs(it.time.hour - 2) } ?: day.first()
                DayForecast(
                    epochDay = epochDay,
                    label = if (epochDay == points.first().time.toLocalDate().toEpochDay()) "Сегодня" else null,
                    parts = mapOf(
                        "d" to DayPartForecast(temperatureC = dayPoint.temperature.roundToInt(), conditionText = conditionText(dayPoint.code)),
                        "n" to DayPartForecast(temperatureC = nightPoint.temperature.roundToInt(), conditionText = conditionText(nightPoint.code)),
                    ),
                    uvText = dayPoint.uv?.let(::uvText),
                )
            }
        return WeatherData(
            place = GeoPlace(city = city.name, province = city.region, lat = city.lat, lon = city.lon),
            current = CurrentWeather(
                temperatureC = first.temperature.roundToInt(),
                windSpeedMs = first.windSpeed,
                windDirection = direction(first.windDirection),
                pressureMmHg = first.pressure?.div(1.33322)?.roundToInt(),
                humidity = first.humidity?.roundToInt(),
                condition = condition(first.code),
                daytime = first.time.hour in 6..18,
                uvIndex = first.uv?.roundToInt(),
            ),
            hourly = hourly,
            days = days,
            fetchedAt = System.currentTimeMillis(),
            sourceUrl = "https://api.met.no/weatherapi/locationforecast/",
        )
    }

    private fun point(row: JsonObject): Point? {
        val time = row.string("time")?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() } ?: return null
        val data = row["data"]?.jsonObject ?: return null
        val instant = data["instant"]?.jsonObject?.get("details")?.jsonObject ?: return null
        val next = sequenceOf("next_1_hours", "next_6_hours", "next_12_hours")
            .mapNotNull { data[it]?.jsonObject }.firstOrNull()
        val code = next?.get("summary")?.jsonObject?.string("symbol_code")?.symbolCode()
        return Point(
            time = time,
            temperature = instant.double("air_temperature") ?: return null,
            humidity = instant.double("relative_humidity"),
            pressure = instant.double("air_pressure_at_sea_level"),
            windSpeed = instant.double("wind_speed"),
            windDirection = instant.double("wind_from_direction"),
            uv = instant.double("ultraviolet_index_clear_sky"),
            code = code,
        )
    }

    private data class Point(
        val time: OffsetDateTime, val temperature: Double, val humidity: Double?, val pressure: Double?,
        val windSpeed: Double?, val windDirection: Double?, val uv: Double?, val code: Int?,
    )

    private companion object {
        const val USER_AGENT = "apk-weather/0.1 github.com/sstpnk/apk-weather"
    }
}

private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.content
private fun JsonObject.double(name: String): Double? = (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull()
private fun String.symbolCode(): Int = when {
    contains("thunder") -> 95; contains("snow") -> 75; contains("rain") || contains("sleet") || contains("drizzle") -> 63
    contains("fog") -> 45; contains("clearsky") -> 0; contains("partlycloudy") -> 1; contains("cloudy") -> 3; else -> 2
}
