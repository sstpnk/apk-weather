package tech.stpnk.weather.data.yandex

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.stpnk.weather.data.cities.CityEntry

class OpenMeteoSourceTest {
    private val city = CityEntry("Москва", "https://yandex.ru/pogoda/ru/moscow", 55.75, 37.62)

    @Test
    fun `open meteo даёт текущую погоду часы и дни`() {
        val body = """
            {
              "current":{"temperature_2m":4.6,"apparent_temperature":1.1,"relative_humidity_2m":87,"surface_pressure":1000.0,"weather_code":61,"wind_speed_10m":18.0,"wind_direction_10m":225.0,"is_day":1,"visibility":9000.0},
              "hourly":{"time":["2026-11-22T10:00","2026-11-22T11:00"],"temperature_2m":[4.6,5.1],"apparent_temperature":[1.1,1.7],"weather_code":[61,3],"wind_speed_10m":[18.0,12.0],"wind_direction_10m":[225.0,200.0]},
              "daily":{"time":["2026-11-22","2026-11-23"],"weather_code":[61,3],"temperature_2m_max":[6.0,7.0],"temperature_2m_min":[1.0,0.0],"sunrise":["2026-11-22T08:15","2026-11-23T08:17"],"sunset":["2026-11-22T16:15","2026-11-23T16:13"],"uv_index_max":[1.0,2.0]}
            }
        """.trimIndent()

        val data = OpenMeteoSource(OkHttpClient()).parse(city, body)

        assertEquals(5, data.current?.temperatureC)
        assertEquals("SOUTHWEST", data.current?.windDirection)
        assertEquals(2, data.hourly.size)
        assertEquals("10:00", data.hourly.first().time)
        assertEquals(2, data.days.size)
        assertEquals("08:15", data.days.first().sunrise)
        assertTrue(data.days.first().parts.containsKey("d"))
    }
}
