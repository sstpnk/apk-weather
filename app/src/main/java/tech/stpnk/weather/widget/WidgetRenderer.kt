package tech.stpnk.weather.widget

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import tech.stpnk.weather.MainActivity
import tech.stpnk.weather.R
import tech.stpnk.weather.data.prefs.WindUnit
import tech.stpnk.weather.domain.model.WeatherData
import tech.stpnk.weather.ui.conditionTextRu
import tech.stpnk.weather.ui.formatTemp
import tech.stpnk.weather.ui.weatherIconRes
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Отрисовка виджетов через RemoteViews и уведомлений.
 * Тексты строит чистые функции — тестируются без Android.
 */
object WidgetRenderer {

    // ---------- чистые построители текстов ----------

    fun bigDateCity(data: WeatherData, now: LocalDate = LocalDate.now()): String {
        val date = now.format(DateTimeFormatter.ofPattern("EE, d MMM", Locale.forLanguageTag("ru")))
        return "$date · ${data.place?.city ?: "—"}"
    }

    fun bigSummary(data: WeatherData): String {
        val cond = conditionTextRu(data.current?.condition) ?: "—"
        val day = data.days.firstOrNull()
        val dTemp = day?.parts?.get("d")?.temperatureC ?: data.current?.temperatureC
        val nTemp = day?.parts?.get("n")?.temperatureC
            ?: data.days.getOrNull(1)?.parts?.get("n")?.temperatureC
        return "$cond · Днём ${formatTemp(dTemp)} · Ночью ${formatTemp(nTemp)}"
    }

    fun dailyTitle(data: WeatherData): String = "Погода на сегодня · ${data.place?.city ?: "—"}"

    fun formatShadeTitle(data: WeatherData): String =
        "${formatTemp(data.current?.temperatureC)} · ${conditionTextRu(data.current?.condition) ?: "—"}"

    fun dailyText(data: WeatherData): String {
        val day = data.days.firstOrNull()
        val d = day?.parts?.get("d")?.temperatureC
        val n = day?.parts?.get("n")?.temperatureC
        val range = when {
            d != null && n != null -> "${formatTemp(minOf(d, n))}…${formatTemp(maxOf(d, n))}"
            d != null -> formatTemp(d)
            n != null -> formatTemp(n)
            else -> {
                // у «сегодня» на странице нет частей день/ночь — диапазон считаем по часам
                val temps = data.hourly.mapNotNull { it.temperatureC }
                if (temps.isNotEmpty()) "${formatTemp(temps.min())}…${formatTemp(temps.max())}"
                else formatTemp(data.current?.temperatureC)
            }
        }
        val cond = conditionTextRu(data.current?.condition) ?: day?.parts?.get("m")?.conditionText
        val wind = tech.stpnk.weather.ui.formatWind(data.current?.windSpeedMs, WindUnit.METERS_PER_SECOND)
        return listOf(range, cond ?: "—", "Ветер $wind").joinToString(" · ")
    }

    fun shadeText(data: WeatherData): String {
        val feels = "Ощущается как: ${formatTemp(data.current?.feelsLikeC)}"
        val wind = tech.stpnk.weather.ui.formatWind(data.current?.windSpeedMs, WindUnit.METERS_PER_SECOND) +
            (tech.stpnk.weather.ui.windDirectionRu(data.current?.windDirection)?.let { ", $it" } ?: "")
        return "$feels · Ветер $wind · ${data.place?.city ?: "—"}"
    }

    // ---------- отрисовка ----------

    fun render(context: Context, data: WeatherData) {
        val manager = android.appwidget.AppWidgetManager.getInstance(context)
        val intent = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val small = RemoteViews(context.packageName, R.layout.widget_small).apply {
            setOnClickPendingIntent(R.id.widget_root, pending)
            setTextViewText(R.id.widget_temp, formatTemp(data.current?.temperatureC))
            setTextViewText(R.id.widget_city, data.place?.city ?: "—")
            setImageViewResource(R.id.widget_icon, weatherIconRes(data.current?.condition, null, data.current?.daytime != false))
        }
        manager.updateAppWidget(ComponentName(context, SmallWeatherWidgetProvider::class.java), small)

        val big = RemoteViews(context.packageName, R.layout.widget_big).apply {
            setOnClickPendingIntent(R.id.widget_root, pending)
            setTextViewText(R.id.widget_temp, formatTemp(data.current?.temperatureC))
            setTextViewText(R.id.widget_datecity, bigDateCity(data))
            setTextViewText(R.id.widget_summary, bigSummary(data))
            setImageViewResource(R.id.widget_icon, weatherIconRes(data.current?.condition, null, data.current?.daytime != false))
        }
        manager.updateAppWidget(ComponentName(context, BigWeatherWidgetProvider::class.java), big)
    }

    /** Быстрая отрисовка из кэша (onUpdate провайдеров — без сети). */
    fun renderFromCache(context: Context) {
        val data = tech.stpnk.weather.Graph.cache.read(tech.stpnk.weather.Graph.cities.selected().url)
        if (data != null) render(context, data)
    }
}
