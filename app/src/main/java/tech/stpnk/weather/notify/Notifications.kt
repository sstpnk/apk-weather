package tech.stpnk.weather.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import tech.stpnk.weather.MainActivity
import tech.stpnk.weather.R
import tech.stpnk.weather.data.prefs.WeatherProvider
import tech.stpnk.weather.data.repo.SourceChange
import tech.stpnk.weather.domain.model.WeatherData
import tech.stpnk.weather.ui.weatherIconRes
import tech.stpnk.weather.widget.WidgetRenderer

/** Каналы уведомлений + «виджет в шторке» (ongoing) + ежедневный прогноз. */
object Notifications {

    const val CHANNEL_SHADE = "weather_shade"
    const val CHANNEL_DAILY = "weather_daily"
    const val CHANNEL_SOURCE = "weather_source"
    private const val SHADE_ID = 1001
    private const val DAILY_ID = 1002
    private const val EVENING_ID = 1003
    private const val SOURCE_ID = 1004

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SHADE, "Виджет в шторке", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_DAILY, "Погода на сегодня", NotificationManager.IMPORTANCE_DEFAULT)
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SOURCE, "Источник погоды", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    /** Постоянное уведомление-виджет в шторке: обновляется воркером, без звука. */
    fun showShade(context: Context, data: WeatherData) {
        val intent = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            context, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_SHADE)
            .setSmallIcon(weatherIconRes(data.current?.condition, null, data.current?.daytime != false))
            .setContentTitle(WidgetRenderer.shadeTitle(data))
            .setContentText(WidgetRenderer.shadeText(data))
            .setStyle(NotificationCompat.BigTextStyle().bigText(WidgetRenderer.shadeText(data)))
            .setContentIntent(pending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
        notify(context, SHADE_ID, notification)
    }

    /** Уведомление утром — на текущий день. */
    fun showMorning(context: Context, data: WeatherData) {
        val intent = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            context, 2, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_DAILY)
            .setSmallIcon(weatherIconRes(data.current?.condition, null, true))
            .setContentTitle(WidgetRenderer.dailyTitle(data))
            .setContentText(WidgetRenderer.dailyText(data))
            .setStyle(NotificationCompat.BigTextStyle().bigText(WidgetRenderer.dailyText(data)))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        notify(context, DAILY_ID, notification)
    }

    /** Уведомление вечером — на завтра. */
    fun showEvening(context: Context, data: WeatherData) {
        val intent = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            context, 3, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_DAILY)
            .setSmallIcon(weatherIconRes(data.current?.condition, null, true))
            .setContentTitle(WidgetRenderer.eveningTitle(data))
            .setContentText(WidgetRenderer.eveningText(data))
            .setStyle(NotificationCompat.BigTextStyle().bigText(WidgetRenderer.eveningText(data)))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        notify(context, EVENING_ID, notification)
    }

    /** Отдельное уведомление: приложение сменило источник после серии повторов. */
    fun showSourceChanged(context: Context, change: SourceChange) {
        val intent = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            context, 4, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = "${providerName(change.from)} недоступен. Используется ${providerName(change.to)}."
        val notification = NotificationCompat.Builder(context, CHANNEL_SOURCE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Источник погоды изменён")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        notify(context, SOURCE_ID, notification)
    }

    private fun providerName(provider: WeatherProvider): String = when (provider) {
        WeatherProvider.YANDEX -> "Яндекс Погода"
        WeatherProvider.OPEN_METEO -> "Open-Meteo"
        WeatherProvider.MET_NORWAY -> "MET Norway"
    }

    private fun notify(context: Context, id: Int, notification: android.app.Notification) {
        runCatching {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.notify(id, notification)
        }
    }

    fun cancelShade(context: Context) {
        runCatching {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.cancel(SHADE_ID)
        }
    }
}
