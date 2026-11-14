package tech.stpnk.weather.widget

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import tech.stpnk.weather.Graph
import tech.stpnk.weather.MainActivity
import tech.stpnk.weather.R
import tech.stpnk.weather.domain.model.WeatherData
import tech.stpnk.weather.ui.conditionTextRu
import tech.stpnk.weather.ui.formatTemp
import tech.stpnk.weather.ui.weatherIconRes
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Отрисовка виджетов через RemoteViews и построители текстов уведомлений.
 * Тексты — чистые функции (тестируются), вид зависит от настроек (прозрачность, ч/б иконки).
 */
object WidgetRenderer {

    // ---------- чистые построители текстов ----------

    fun bigDateCity(data: WeatherData, now: java.time.LocalDate = java.time.LocalDate.now()): String {
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
        val wind = tech.stpnk.weather.ui.formatWind(data.current?.windSpeedMs, tech.stpnk.weather.data.prefs.WindUnit.METERS_PER_SECOND)
        return listOf(range, cond ?: "—", "Ветер $wind").joinToString(" · ")
    }

    fun shadeTitle(data: WeatherData): String =
        "${formatTemp(data.current?.temperatureC)} · ${conditionTextRu(data.current?.condition) ?: "—"}"

    fun shadeText(data: WeatherData): String {
        val feels = "Ощущается как: ${formatTemp(data.current?.feelsLikeC)}"
        val wind = tech.stpnk.weather.ui.formatWind(data.current?.windSpeedMs, tech.stpnk.weather.data.prefs.WindUnit.METERS_PER_SECOND) +
            (tech.stpnk.weather.ui.windDirectionRu(data.current?.windDirection)?.let { ", $it" } ?: "")
        return "$feels · Ветер $wind · ${data.place?.city ?: "—"}"
    }

    // ---------- отрисовка ----------

    fun render(context: Context, data: WeatherData) {
        val settings = Graph.settings.load()
        val manager = android.appwidget.AppWidgetManager.getInstance(context)
        val intent = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val icon = iconBitmap(context, weatherIconRes(data.current?.condition, null, data.current?.daytime != false), settings.widgetMonochrome)

        val small = RemoteViews(context.packageName, R.layout.widget_small).apply {
            setOnClickPendingIntent(R.id.widget_root, pending)
            setInt(R.id.widget_root, "setBackgroundResource", backgroundRes(settings.widgetTransparency))
            setTextViewText(R.id.widget_temp, formatTemp(data.current?.temperatureC, settings.tempUnit))
            setTextViewText(R.id.widget_city, data.place?.city ?: "—")
            if (icon != null) setImageViewBitmap(R.id.widget_icon, icon.bitmap)
        }
        manager.updateAppWidget(ComponentName(context, SmallWeatherWidgetProvider::class.java), small)

        val big = RemoteViews(context.packageName, R.layout.widget_big).apply {
            setOnClickPendingIntent(R.id.widget_root, pending)
            setInt(R.id.widget_root, "setBackgroundResource", backgroundRes(settings.widgetTransparency))
            setTextViewText(R.id.widget_temp, formatTemp(data.current?.temperatureC, settings.tempUnit))
            setTextViewText(R.id.widget_datecity, bigDateCity(data))
            setTextViewText(R.id.widget_summary, bigSummary(data))
            if (icon != null) setImageViewBitmap(R.id.widget_icon, icon.bitmap)
        }
        manager.updateAppWidget(ComponentName(context, BigWeatherWidgetProvider::class.java), big)
    }

    /** Отрисовка из кэша (onUpdate провайдеров — без сети); без кэша — заглушка. */
    fun renderFromCache(context: Context) {
        val data = Graph.cache.read(Graph.cities.selected().url)
        if (data != null) render(context, data) else renderEmpty(context)
    }

    /** Заглушка «нет данных»: показывается, пока воркер ни разу не отработал. */
    fun renderEmpty(context: Context) {
        val intent = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val small = RemoteViews(context.packageName, R.layout.widget_small).apply {
            setOnClickPendingIntent(R.id.widget_root, pending)
            setTextViewText(R.id.widget_temp, "—")
            setTextViewText(R.id.widget_city, "Нет данных")
        }
        val manager = android.appwidget.AppWidgetManager.getInstance(context)
        manager.updateAppWidget(ComponentName(context, SmallWeatherWidgetProvider::class.java), small)
        val big = RemoteViews(context.packageName, R.layout.widget_big).apply {
            setOnClickPendingIntent(R.id.widget_root, pending)
            setTextViewText(R.id.widget_temp, "—")
            setTextViewText(R.id.widget_datecity, "Нет данных")
            setTextViewText(R.id.widget_summary, "Откройте приложение")
        }
        manager.updateAppWidget(ComponentName(context, BigWeatherWidgetProvider::class.java), big)
    }

    // ---------- служебное ----------

    /** Фон виджета: ведро прозрачности 0..100 → заготовленный drawable (скругления сохраняются). */
    private fun backgroundRes(transparency: Int): Int = when {
        transparency <= 20 -> R.drawable.widget_bg_90
        transparency <= 40 -> R.drawable.widget_bg_70
        transparency <= 60 -> R.drawable.widget_bg_50
        transparency <= 80 -> R.drawable.widget_bg_30
        else -> R.drawable.widget_bg_10
    }

    private fun iconBitmap(context: Context, resId: Int, monochrome: Boolean): BitmapDrawable? = runCatching {
        val drawable = ContextCompat.getDrawable(context, resId) ?: return@runCatching null
        val size = (40 * context.resources.displayMetrics.density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        if (monochrome) {
            canvas.drawColor(android.graphics.Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN)
        }
        BitmapDrawable(context.resources, bitmap)
    }.getOrNull()
}
