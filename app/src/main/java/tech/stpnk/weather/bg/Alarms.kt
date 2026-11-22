package tech.stpnk.weather.bg

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import tech.stpnk.weather.Graph
import tech.stpnk.weather.data.prefs.AppSettings
import tech.stpnk.weather.data.repo.LoadOutcome
import tech.stpnk.weather.domain.model.WeatherData
import tech.stpnk.weather.notify.Notifications
import tech.stpnk.weather.widget.WidgetRenderer
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Планировщик на AlarmManager: постоянное обновление (шторка+виджеты) с настраиваемым
 * интервалом и два ежедневных уведомления (утро/вечер). WorkManager не используется —
 * он не умеет интервалы 5/10 минут.
 */
object AlarmScheduler {

    private const val RC_SHADE = 100
    private const val RC_MORNING = 101
    private const val RC_EVENING = 102

    fun scheduleAll(context: Context) {
        scheduleShade(context)
        subscribeDaily(context)
    }

    fun scheduleShade(context: Context) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pending = pending(context, ShadeAlarmReceiver::class.java, RC_SHADE)
        manager.cancel(pending)
        val settings = Graph.settings.load()
        val interval = settings.shadeIntervalMinutes
        val nextAt = if (interval == DAILY_AT_SIX) {
            nextHour(6)
        } else {
            System.currentTimeMillis() + interval.coerceAtLeast(5) * 60_000L
        }
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextAt, pending)
    }

    fun subscribeDaily(context: Context) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val settings = Graph.settings.load()
        scheduleOne(manager, context, MorningAlarmReceiver::class.java, RC_MORNING, settings.morningEnabled, settings.morningHour)
        scheduleOne(manager, context, EveningAlarmReceiver::class.java, RC_EVENING, settings.eveningEnabled, settings.eveningHour)
    }

    private fun scheduleOne(
        manager: AlarmManager,
        context: Context,
        cls: Class<*>,
        rc: Int,
        enabled: Boolean,
        hour: Int,
    ) {
        val pending = pending(context, cls, rc)
        manager.cancel(pending)
        if (!enabled) return
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextHour(hour), pending)
    }

    private fun nextHour(hour: Int): Long {
        val now = LocalDateTime.now()
        var next = now.withHour(hour.coerceIn(0, 23)).withMinute(0).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun pending(context: Context, cls: Class<*>, rc: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, rc, Intent(context, cls),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** -1 — «раз в сутки в 6:00 по локальному времени». */
    const val DAILY_AT_SIX = -1
}

private suspend fun loadWeather(settings: AppSettings, force: Boolean, maxAgeMinutes: Int): WeatherData? {
    val url = Graph.cities.selected().url
    val maxAge = maxAgeMinutes * 60_000L
    val outcome = Graph.repository.load(url, force = force, maxAgeMs = maxAge)
    return when (outcome) {
        is LoadOutcome.Fresh -> outcome.data
        is LoadOutcome.Stale -> outcome.data
        is LoadOutcome.Failed -> null
    }
}

/** Каждые N минут: обновить виджеты и (если включено) виджет в шторке. */
class ShadeAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val settings = Graph.settings.load()
                val maxAge = if (settings.shadeIntervalMinutes in 1..30) settings.shadeIntervalMinutes else 30
                val data = loadWeather(settings, force = false, maxAgeMinutes = maxAge)
                if (data != null) {
                    WidgetRenderer.render(context, data)
                    if (settings.shadeEnabled) Notifications.showShade(context, data)
                    else Notifications.cancelShade(context)
                }
                AlarmScheduler.scheduleShade(context)
            } finally {
                result.finish()
            }
        }
    }
}

/** Утреннее уведомление — прогноз на сегодня. */
class MorningAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val settings = Graph.settings.load()
                if (settings.morningEnabled) {
                    loadWeather(settings, force = true, maxAgeMinutes = 0)?.let {
                        Notifications.showMorning(context, it)
                    }
                }
                AlarmScheduler.subscribeDaily(context)
            } finally {
                result.finish()
            }
        }
    }
}

/** Вечернее уведомление — прогноз на завтра. */
class EveningAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val settings = Graph.settings.load()
                if (settings.eveningEnabled) {
                    loadWeather(settings, force = true, maxAgeMinutes = 0)?.let {
                        Notifications.showEvening(context, it)
                    }
                }
                AlarmScheduler.subscribeDaily(context)
            } finally {
                result.finish()
            }
        }
    }
}

/** После перезагрузки телефона будильники теряются — планируем заново. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            AlarmScheduler.scheduleAll(context)
        }
    }
}
