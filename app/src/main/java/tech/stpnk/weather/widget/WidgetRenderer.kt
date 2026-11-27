package tech.stpnk.weather.widget

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import tech.stpnk.weather.Graph
import tech.stpnk.weather.MainActivity
import tech.stpnk.weather.R
import tech.stpnk.weather.data.prefs.AppSettings
import tech.stpnk.weather.data.prefs.WindUnit
import tech.stpnk.weather.domain.model.WeatherData
import tech.stpnk.weather.ui.conditionTextRu
import tech.stpnk.weather.ui.formatPressure
import tech.stpnk.weather.ui.formatTemp
import tech.stpnk.weather.ui.formatWind
import tech.stpnk.weather.ui.weatherIconRes
import tech.stpnk.weather.ui.windDirectionRu
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Виджеты 2×1, 4×1 (инфо), 4×1 (часы): RemoteViews, светлая/тёмная тема по системе,
 * прозрачность и ч/б иконки из настроек. Тексты — чистые функции (тестируются).
 */
object WidgetRenderer {

    // ---------- текстовые построители ----------

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

    /** «+3° +1°» — максимум и минимум дня (для компактных виджетов). */
    fun minMax(data: WeatherData, settings: AppSettings): String {
        val day = data.days.firstOrNull()
        val d = day?.parts?.get("d")?.temperatureC ?: data.current?.temperatureC
        val n = day?.parts?.get("n")?.temperatureC
            ?: data.days.getOrNull(1)?.parts?.get("n")?.temperatureC
            ?: data.current?.temperatureC
        val max = listOfNotNull(d, n).maxOrNull()
        val min = listOfNotNull(d, n).minOrNull()
        return "${formatTemp(max, settings.tempUnit)} ${formatTemp(min, settings.tempUnit)}"
    }

    /** «Днём +11°\nНочью +4°» для виджета с часами. */
    fun dayNight(data: WeatherData, settings: AppSettings): String {
        val day = data.days.firstOrNull()
        val d = day?.parts?.get("d")?.temperatureC ?: data.current?.temperatureC
        val n = day?.parts?.get("n")?.temperatureC ?: data.days.getOrNull(1)?.parts?.get("n")?.temperatureC
        return "Днём ${formatTemp(d, settings.tempUnit)}\nНочью ${formatTemp(n, settings.tempUnit)}"
    }

    fun dailyTitle(data: WeatherData): String = "Погода на сегодня · ${data.place?.city ?: "—"}"

    fun dailyText(data: WeatherData): String = rangeText(data)

    fun eveningTitle(data: WeatherData): String = "Погода завтра · ${data.place?.city ?: "—"}"

    fun eveningText(data: WeatherData): String {
        val tomorrow = data.days.getOrNull(1)
        val d = tomorrow?.parts?.get("d")?.temperatureC
        val n = tomorrow?.parts?.get("n")?.temperatureC
        val min = listOfNotNull(d, n).minOrNull()
        val max = listOfNotNull(d, n).maxOrNull()
        val range = if (min != null && max != null) "${formatTemp(min)}…${formatTemp(max)}"
        else formatTemp(data.current?.temperatureC)
        val cond = tomorrow?.parts?.get("d")?.conditionText
            ?: tomorrow?.parts?.get("m")?.conditionText
            ?: conditionTextRu(data.current?.condition)
        val wind = formatWind(data.current?.windSpeedMs, WindUnit.METERS_PER_SECOND)
        return listOf(range, cond ?: "—", "Ветер $wind").joinToString(" · ")
    }

    private fun rangeText(data: WeatherData): String {
        val day = data.days.firstOrNull()
        val d = day?.parts?.get("d")?.temperatureC
        val n = day?.parts?.get("n")?.temperatureC
        val range = when {
            d != null && n != null -> "${formatTemp(minOf(d, n))}…${formatTemp(maxOf(d, n))}"
            d != null -> formatTemp(d)
            n != null -> formatTemp(n)
            else -> {
                val temps = data.hourly.mapNotNull { it.temperatureC }
                if (temps.isNotEmpty()) "${formatTemp(temps.min())}…${formatTemp(temps.max())}"
                else formatTemp(data.current?.temperatureC)
            }
        }
        val cond = conditionTextRu(data.current?.condition) ?: day?.parts?.get("m")?.conditionText
        val wind = formatWind(data.current?.windSpeedMs, WindUnit.METERS_PER_SECOND)
        return listOf(range, cond ?: "—", "Ветер $wind").joinToString(" · ")
    }

    fun shadeTitle(data: WeatherData): String =
        "${formatTemp(data.current?.temperatureC)} · ${conditionTextRu(data.current?.condition) ?: "—"}"

    fun shadeText(data: WeatherData): String {
        val feels = "Ощущается как: ${formatTemp(data.current?.feelsLikeC)}"
        val wind = formatWind(data.current?.windSpeedMs, WindUnit.METERS_PER_SECOND) +
            (windDirectionRu(data.current?.windDirection)?.let { ", $it" } ?: "")
        return "$feels · Ветер $wind · ${data.place?.city ?: "—"}"
    }

    // ---------- отрисовка ----------

    fun render(context: Context, data: WeatherData) {
        val settings = Graph.settings.load()
        val night = isNight(context)
        val main = if (night) 0xFFFFFFFF.toInt() else 0xFF14181D.toInt()
        val secondary = withAlpha(main, 0.65f)
        val manager = android.appwidget.AppWidgetManager.getInstance(context)
        val pending = tapIntent(context)
        val icon = iconBitmap(context, weatherIconRes(data.current?.condition, null, data.current?.daytime != false), settings.widgetMonochrome, night)
        val bg = backgroundRes(settings.widgetTransparency, night)

        val small = RemoteViews(context.packageName, R.layout.widget_small).apply {
            setOnClickPendingIntent(R.id.widget_root, pending)
            setInt(R.id.widget_root, "setBackgroundResource", bg)
            setTextColor(R.id.widget_clock, main)
            setTextColor(R.id.widget_city, secondary)
            setTextColor(R.id.widget_temp, main)
            setTextColor(R.id.widget_minmax, secondary)
            setTextViewText(R.id.widget_city, ", ${data.place?.city ?: "—"}")
            setTextViewText(R.id.widget_temp, formatTemp(data.current?.temperatureC, settings.tempUnit))
            setTextViewText(R.id.widget_minmax, minMax(data, settings))
            if (icon != null) setImageViewBitmap(R.id.widget_icon, icon.bitmap)
        }
        manager.updateAppWidget(ComponentName(context, SmallWeatherWidgetProvider::class.java), small)

        val wide = RemoteViews(context.packageName, R.layout.widget_wide).apply {
            setOnClickPendingIntent(R.id.widget_root, pending)
            setInt(R.id.widget_root, "setBackgroundResource", bg)
            listOf(R.id.widget_clock, R.id.widget_city, R.id.widget_temp, R.id.widget_minmax,
                R.id.widget_feels, R.id.widget_wind, R.id.widget_pressure).forEach { setTextColor(it, main) }
            setTextViewText(R.id.widget_city, ", ${data.place?.city ?: "—"}")
            setTextViewText(R.id.widget_temp, formatTemp(data.current?.temperatureC, settings.tempUnit))
            setTextViewText(R.id.widget_minmax, minMax(data, settings))
            setTextViewText(R.id.widget_feels, "Ощущается как: ${formatTemp(data.current?.feelsLikeC, settings.tempUnit)}")
            setTextViewText(
                R.id.widget_wind,
                "Ветер: ${formatWind(data.current?.windSpeedMs, settings.windUnit)}" +
                    (windDirectionRu(data.current?.windDirection)?.let { ", $it" } ?: ""),
            )
            setTextViewText(R.id.widget_pressure, "Давление: ${formatPressure(data.current?.pressureMmHg, settings.pressureUnit)}")
            if (icon != null) setImageViewBitmap(R.id.widget_icon, icon.bitmap)
        }
        manager.updateAppWidget(ComponentName(context, WideWeatherWidgetProvider::class.java), wide)

        // 4×1 с частями суток: Вечер / Ночь / Утро (завтра)
        val day0 = data.days.firstOrNull()
        val day1 = data.days.getOrNull(1)
        val parts = listOf(
            Triple("Вечер", day0?.parts?.get("e")?.temperatureC, day0?.parts?.get("e")?.conditionText),
            Triple("Ночь", day0?.parts?.get("n")?.temperatureC, day0?.parts?.get("n")?.conditionText),
            Triple("Утро", day1?.parts?.get("m")?.temperatureC, day1?.parts?.get("m")?.conditionText),
        )
        val labelIds = listOf(R.id.widget_p1_label, R.id.widget_p2_label, R.id.widget_p3_label)
        val iconIds = listOf(R.id.widget_p1_icon, R.id.widget_p2_icon, R.id.widget_p3_icon)
        val tempIds = listOf(R.id.widget_p1_temp, R.id.widget_p2_temp, R.id.widget_p3_temp)

        val dayparts = RemoteViews(context.packageName, R.layout.widget_dayparts).apply {
            setOnClickPendingIntent(R.id.widget_root, pending)
            setInt(R.id.widget_root, "setBackgroundResource", bg)
            listOf(R.id.widget_clock, R.id.widget_city, R.id.widget_temp, R.id.widget_minmax,
                R.id.widget_p1_label, R.id.widget_p1_temp,
                R.id.widget_p2_label, R.id.widget_p2_temp,
                R.id.widget_p3_label, R.id.widget_p3_temp).forEach { setTextColor(it, main) }
            setTextViewText(R.id.widget_city, ", ${data.place?.city ?: "—"}")
            setTextViewText(R.id.widget_temp, formatTemp(data.current?.temperatureC, settings.tempUnit))
            setTextViewText(R.id.widget_minmax, minMax(data, settings))
            parts.forEachIndexed { index, (label, temp, cond) ->
                setTextViewText(labelIds[index], label)
                setTextViewText(tempIds[index], formatTemp(temp, settings.tempUnit))
                setImageViewResource(iconIds[index], weatherIconRes(null, cond, true))
            }
            if (icon != null) setImageViewBitmap(R.id.widget_icon, icon.bitmap)
        }
        manager.updateAppWidget(ComponentName(context, DayPartsWeatherWidgetProvider::class.java), dayparts)

        val clock = RemoteViews(context.packageName, R.layout.widget_clock).apply {
            setOnClickPendingIntent(R.id.widget_root, pending)
            setInt(R.id.widget_root, "setBackgroundResource", bg)
            listOf(R.id.widget_clock, R.id.widget_temp, R.id.widget_daynight, R.id.widget_datecity)
                .forEach { setTextColor(it, main) }
            setTextViewText(R.id.widget_temp, formatTemp(data.current?.temperatureC, settings.tempUnit))
            setTextViewText(R.id.widget_daynight, dayNight(data, settings))
            setTextViewText(R.id.widget_datecity, bigDateCity(data))
            if (icon != null) setImageViewBitmap(R.id.widget_icon, icon.bitmap)
        }
        manager.updateAppWidget(ComponentName(context, ClockWeatherWidgetProvider::class.java), clock)
    }

    fun renderFromCache(context: Context) {
        val city = Graph.cities.selected()
        val data = Graph.repository.cached(city, Graph.settings.load().weatherProvider)
        if (data != null) render(context, data) else renderEmpty(context)
    }

    fun renderEmpty(context: Context) {
        val night = isNight(context)
        val main = if (night) 0xFFFFFFFF.toInt() else 0xFF14181D.toInt()
        val pending = tapIntent(context)
        val manager = android.appwidget.AppWidgetManager.getInstance(context)
        val small = RemoteViews(context.packageName, R.layout.widget_small).apply {
            setOnClickPendingIntent(R.id.widget_root, pending)
            setTextViewText(R.id.widget_temp, "—")
            setTextViewText(R.id.widget_city, "Нет данных")
            setTextColor(R.id.widget_temp, main)
        }
        manager.updateAppWidget(ComponentName(context, SmallWeatherWidgetProvider::class.java), small)
        val wide = RemoteViews(context.packageName, R.layout.widget_wide).apply {
            setOnClickPendingIntent(R.id.widget_root, pending)
            setTextViewText(R.id.widget_temp, "—")
            setTextViewText(R.id.widget_feels, "Нет данных")
            setTextColor(R.id.widget_temp, main)
        }
        manager.updateAppWidget(ComponentName(context, WideWeatherWidgetProvider::class.java), wide)
        val dayparts = RemoteViews(context.packageName, R.layout.widget_dayparts).apply {
            setOnClickPendingIntent(R.id.widget_root, pending)
            setTextViewText(R.id.widget_temp, "—")
            setTextViewText(R.id.widget_city, "Нет данных")
            setTextColor(R.id.widget_temp, main)
        }
        manager.updateAppWidget(ComponentName(context, DayPartsWeatherWidgetProvider::class.java), dayparts)
        val clock = RemoteViews(context.packageName, R.layout.widget_clock).apply {
            setOnClickPendingIntent(R.id.widget_root, pending)
            setTextViewText(R.id.widget_temp, "—")
            setTextViewText(R.id.widget_datecity, "Нет данных")
            setTextColor(R.id.widget_temp, main)
        }
        manager.updateAppWidget(ComponentName(context, ClockWeatherWidgetProvider::class.java), clock)
    }

    // ---------- служебное ----------

    private fun tapIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun isNight(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private fun withAlpha(color: Int, factor: Float): Int {
        val alpha = ((color ushr 24) * factor).toInt().coerceIn(0, 255)
        return (alpha shl 24) or (color and 0x00FFFFFF)
    }

    /** Прозрачность 0..100 → заготовленный фон под текущую тему. */
    private fun backgroundRes(transparency: Int, night: Boolean): Int {
        val bucket = when {
            transparency <= 20 -> 90
            transparency <= 40 -> 70
            transparency <= 60 -> 50
            transparency <= 80 -> 30
            else -> 10
        }
        return when (bucket) {
            90 -> if (night) R.drawable.widget_bg_90 else R.drawable.widget_bg_light_90
            70 -> if (night) R.drawable.widget_bg_70 else R.drawable.widget_bg_light_70
            50 -> if (night) R.drawable.widget_bg_50 else R.drawable.widget_bg_light_50
            30 -> if (night) R.drawable.widget_bg_30 else R.drawable.widget_bg_light_30
            else -> if (night) R.drawable.widget_bg_10 else R.drawable.widget_bg_light_10
        }
    }

    private fun iconBitmap(context: Context, resId: Int, monochrome: Boolean, night: Boolean): BitmapDrawable? = runCatching {
        val drawable = ContextCompat.getDrawable(context, resId) ?: return@runCatching null
        val size = (40 * context.resources.displayMetrics.density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        if (monochrome) {
            val tint = if (night) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#14181D")
            canvas.drawColor(tint, android.graphics.PorterDuff.Mode.SRC_IN)
        }
        BitmapDrawable(context.resources, bitmap)
    }.getOrNull()
}
