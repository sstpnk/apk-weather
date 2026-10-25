package tech.stpnk.weather

import android.app.Application
import android.content.Context
import okhttp3.OkHttpClient
import tech.stpnk.weather.data.cache.WeatherCache
import tech.stpnk.weather.data.repo.WeatherRepository
import tech.stpnk.weather.data.yandex.YandexWebSource
import java.io.File
import java.util.concurrent.TimeUnit

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
    }
}

/** Ручной DI-контейнер: минимум кода, ноль рефлексии. */
object Graph {

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val source: YandexWebSource by lazy { YandexWebSource(http) }

    val cache: WeatherCache by lazy {
        WeatherCache(File(requireNotNull(appContext).filesDir, "weather-cache"))
    }

    val repository: WeatherRepository by lazy { WeatherRepository(source, cache) }

    /** Москва — стартовый город до появления списка городов (этап 6). */
    const val DEFAULT_CITY_URL = "https://yandex.ru/pogoda/ru/moscow"
}
