package tech.stpnk.weather.ui.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import tech.stpnk.weather.Graph
import tech.stpnk.weather.bg.AlarmScheduler
import tech.stpnk.weather.data.prefs.ThemeMode
import tech.stpnk.weather.data.repo.LoadOutcome
import tech.stpnk.weather.notify.Notifications
import tech.stpnk.weather.ui.theme.WeatherTheme
import tech.stpnk.weather.widget.WidgetRenderer

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = Graph.settings
        enableEdgeToEdge()
        setContent {
            var settings by remember { mutableStateOf(store.load()) }
            val dark = when (settings.theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            WeatherTheme(darkTheme = dark) {
                SettingsScreen(
                    settings = settings,
                    onChange = { new ->
                        val old = settings
                        val themeChanged = new.theme != old.theme
                        store.save(new)
                        settings = new
                        // расписание будильников (интервал, время уведомлений) могло измениться
                        AlarmScheduler.scheduleAll(this)
                        // виджеты перерисовываем сразу (прозрачность/ч-б/смена источника)
                        WidgetRenderer.renderFromCache(this)
                        // «Уведомление в шторке»: включили — показываем сразу; выключили — снимаем
                        if (!old.shadeEnabled && new.shadeEnabled) {
                            val city = Graph.cities.selected()
                            val cached = Graph.cache.read("${city.url}|${new.weatherProvider}")
                                ?: Graph.cache.read("${city.url}|${old.weatherProvider}")
                            if (cached != null) {
                                Notifications.showShade(this, cached)
                            } else {
                                lifecycleScope.launch {
                                    val outcome = Graph.repository.load(city, new.weatherProvider, force = true, maxAgeMs = 0)
                                    val data = when (outcome) {
                                        is LoadOutcome.Fresh -> outcome.data
                                        is LoadOutcome.Stale -> outcome.data
                                        else -> null
                                    }
                                    if (data != null) Notifications.showShade(this@SettingsActivity, data)
                                }
                            }
                        }
                        if (old.shadeEnabled && !new.shadeEnabled) Notifications.cancelShade(this)
                        if (themeChanged) recreate()
                    },
                    onBack = { finish() },
                )
            }
        }
    }
}
