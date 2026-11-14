package tech.stpnk.weather.bg

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import tech.stpnk.weather.Graph
import tech.stpnk.weather.data.repo.LoadOutcome
import tech.stpnk.weather.notify.Notifications
import tech.stpnk.weather.widget.WidgetRenderer
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** Периодическое обновление виджетов и шторки. Интервал и toggles — из настроек. */
class WidgetUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = Graph.settings.load()
        val url = Graph.cities.selected().url
        when (val outcome = Graph.repository.load(url)) {
            is LoadOutcome.Fresh -> WidgetRenderer.render(applicationContext, outcome.data)
            is LoadOutcome.Stale -> WidgetRenderer.render(applicationContext, outcome.data)
            is LoadOutcome.Failed -> Unit // сети нет — оставляем последний вид
        }
        if (settings.shadeEnabled) {
            Graph.cache.read(url)?.let { Notifications.showShade(applicationContext, it) }
        } else {
            Notifications.cancelShade(applicationContext)
        }
        return Result.success()
    }
}

/** Ежедневное уведомление «Погода на сегодня» в заданный час. */
class DailyForecastWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = Graph.settings.load()
        if (!settings.dailyEnabled) return Result.success()
        val url = Graph.cities.selected().url
        when (val outcome = Graph.repository.load(url, force = true)) {
            is LoadOutcome.Fresh -> Notifications.showDaily(applicationContext, outcome.data)
            is LoadOutcome.Stale -> Notifications.showDaily(applicationContext, outcome.data)
            is LoadOutcome.Failed -> Unit // без сети уведомление не показываем
        }
        return Result.success()
    }
}

object BackgroundScheduler {

    /** (Пере)планирование по настройкам: интервал виджетов, час ежедневного уведомления. */
    fun schedule(context: Context) {
        val settings = Graph.settings.load()
        val wm = WorkManager.getInstance(context)

        wm.enqueueUniquePeriodicWork(
            "widget-update",
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<WidgetUpdateWorker>(
                settings.widgetRefreshMinutes.coerceAtLeast(15).toLong(), TimeUnit.MINUTES
            ).build(),
        )

        val now = ZonedDateTime.now()
        var next = now.withHour(settings.dailyHour.coerceIn(0, 23)).withMinute(0).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delay = Duration.between(now, next)

        wm.enqueueUniquePeriodicWork(
            "daily-forecast",
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<DailyForecastWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
                .build(),
        )
    }
}
