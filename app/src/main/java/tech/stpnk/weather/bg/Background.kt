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

/** Периодическое обновление виджетов и шторки (TTL кэша внутри репозитория бережёт Яндекс). */
class WidgetUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val url = Graph.cities.selected().url
        when (val outcome = Graph.repository.load(url)) {
            is LoadOutcome.Fresh -> WidgetRenderer.render(applicationContext, outcome.data)
            is LoadOutcome.Stale -> WidgetRenderer.render(applicationContext, outcome.data)
            is LoadOutcome.Failed -> Unit // сети нет — оставляем последний вид
        }
        return Result.success()
    }
}

/** Ежедневное уведомление «Погода на сегодня» в 08:00 локального времени. */
class DailyForecastWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
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

    fun schedule(context: Context) {
        val wm = WorkManager.getInstance(context)

        wm.enqueueUniquePeriodicWork(
            "widget-update",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<WidgetUpdateWorker>(60, TimeUnit.MINUTES).build(),
        )

        val now = ZonedDateTime.now()
        var next8 = now.withHour(8).withMinute(0).withSecond(0).withNano(0)
        if (!next8.isAfter(now)) next8 = next8.plusDays(1)
        val delay = Duration.between(now, next8)

        wm.enqueueUniquePeriodicWork(
            "daily-forecast",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DailyForecastWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
                .build(),
        )
    }
}
