package tech.stpnk.weather.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import tech.stpnk.weather.Graph
import tech.stpnk.weather.data.repo.LoadOutcome

private fun refreshAndRender(context: Context, receiver: AppWidgetProvider) {
    val pending = receiver.goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        try {
            val city = Graph.cities.selected()
            val provider = Graph.settings.load().weatherProvider
            val key = "${city.url}|$provider"
            val cached = Graph.cache.read(key)
            val data = cached ?: when (val outcome = Graph.repository.load(city, provider, force = true)) {
                is LoadOutcome.Fresh -> outcome.data
                is LoadOutcome.Stale -> outcome.data
                is LoadOutcome.Failed -> null
            }
            if (data != null) WidgetRenderer.render(context, data) else WidgetRenderer.renderEmpty(context)
        } finally {
            pending.finish()
        }
    }
}

/** 2×1: часы, иконка, температура, минимум/максимум. */
class SmallWeatherWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        refreshAndRender(context, this)
    }
}

/** 4×1: инфо — температура, ощущается, ветер, давление. */
class WideWeatherWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        refreshAndRender(context, this)
    }
}

/** 4×1: часы — крупное время, дата/город, день/ночь. */
class ClockWeatherWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        refreshAndRender(context, this)
    }
}
