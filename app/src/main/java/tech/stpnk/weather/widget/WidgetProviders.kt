package tech.stpnk.weather.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

/** 2×1: часы, иконка, температура, минимум/максимум. */
class SmallWeatherWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WidgetRenderer.renderFromCache(context)
    }
}

/** 4×1: инфо — температура, ощущается, ветер, давление. */
class WideWeatherWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WidgetRenderer.renderFromCache(context)
    }
}

/** 4×1: часы — крупное время, дата/город, день/ночь. */
class ClockWeatherWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WidgetRenderer.renderFromCache(context)
    }
}
