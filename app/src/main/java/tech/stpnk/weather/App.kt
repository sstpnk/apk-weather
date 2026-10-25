package tech.stpnk.weather

import android.app.Application
import android.content.Context
import okhttp3.OkHttpClient
import tech.stpnk.weather.bg.BackgroundScheduler
import tech.stpnk.weather.data.cache.WeatherCache
import tech.stpnk.weather.data.cities.CitiesStore
import tech.stpnk.weather.data.geo.GeoSearchSource
import tech.stpnk.weather.data.geo.LocationHelper
import tech.stpnk.weather.data.prefs.KeyValueStore
import tech.stpnk.weather.data.prefs.SettingsStore
import tech.stpnk.weather.data.prefs.SharedPrefsStore
import tech.stpnk.weather.data.repo.WeatherRepository
import tech.stpnk.weather.data.yandex.YandexWebSource
import tech.stpnk.weather.notify.Notifications
import java.io.File
import java.util.concurrent.TimeUnit

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
        Notifications.ensure(this)
        BackgroundScheduler.schedule(this)
    }
}

/** Ручной DI-контейнер: минимум кода, ноль рефлексии. */
object Graph {

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun ctx(): Context = requireNotNull(appContext) { "Graph.init не вызван" }

    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val source: YandexWebSource by lazy { YandexWebSource(http) }

    val cache: WeatherCache by lazy { WeatherCache(File(ctx().filesDir, "weather-cache")) }

    val repository: WeatherRepository by lazy { WeatherRepository(source, cache) }

    val kv: KeyValueStore by lazy { SharedPrefsStore(ctx()) }

    val settings: SettingsStore by lazy { SettingsStore(kv) }

    val cities: CitiesStore by lazy { CitiesStore(kv) }

    val geo: GeoSearchSource by lazy { GeoSearchSource(http) }

    val location: LocationHelper by lazy { LocationHelper(ctx()) }
}
