package tech.stpnk.weather.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.stpnk.weather.data.prefs.AppSettings
import tech.stpnk.weather.data.prefs.PressureUnit
import tech.stpnk.weather.data.prefs.TempUnit
import tech.stpnk.weather.data.prefs.ThemeMode
import tech.stpnk.weather.data.prefs.WeatherProvider
import tech.stpnk.weather.data.prefs.WindUnit

private val INTERVALS = listOf(
    "5 минут" to 5,
    "10 минут" to 10,
    "15 минут" to 15,
    "30 минут" to 30,
    "1 час" to 60,
    "2 часа" to 120,
    "3 часа" to 180,
    "6 часов" to 360,
    "12 часов" to 720,
    "Раз в сутки (6:00)" to -1,
)

@Composable
fun SettingsScreen(settings: AppSettings, onChange: (AppSettings) -> Unit, onBack: () -> Unit) {
    var intervalDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = MaterialTheme.colorScheme.primary)
            }
            Text("Настройки", fontSize = 20.sp, modifier = Modifier.padding(start = 4.dp))
        }

        SectionLabel("Единицы измерения")
        SettingsGroup {
            SegmentedRow("Температура", listOf("°C", "°F"), if (settings.tempUnit == TempUnit.CELSIUS) 0 else 1) { i ->
                onChange(settings.copy(tempUnit = if (i == 0) TempUnit.CELSIUS else TempUnit.FAHRENHEIT))
            }
            SegmentedRow("Сила ветра", listOf("м/с", "км/ч"), if (settings.windUnit == WindUnit.METERS_PER_SECOND) 0 else 1) { i ->
                onChange(settings.copy(windUnit = if (i == 0) WindUnit.METERS_PER_SECOND else WindUnit.KM_PER_HOUR))
            }
            SegmentedRow("Давление", listOf("мм рт. ст.", "гПа"), if (settings.pressureUnit == PressureUnit.MMHG) 0 else 1) { i ->
                onChange(settings.copy(pressureUnit = if (i == 0) PressureUnit.MMHG else PressureUnit.HPA))
            }
        }

        SectionLabel("Тема оформления")
        SettingsGroup {
            RadioRow("Светлая", settings.theme == ThemeMode.LIGHT) { onChange(settings.copy(theme = ThemeMode.LIGHT)) }
            RadioRow("Тёмная", settings.theme == ThemeMode.DARK) { onChange(settings.copy(theme = ThemeMode.DARK)) }
            RadioRow("Как в системе", settings.theme == ThemeMode.SYSTEM) { onChange(settings.copy(theme = ThemeMode.SYSTEM)) }
        }

        SectionLabel("Источник погоды")
        SettingsGroup {
            RadioRow("Яндекс Погода", settings.weatherProvider == WeatherProvider.YANDEX) {
                onChange(settings.copy(weatherProvider = WeatherProvider.YANDEX))
            }
            RadioRow("Open-Meteo", settings.weatherProvider == WeatherProvider.OPEN_METEO) {
                onChange(settings.copy(weatherProvider = WeatherProvider.OPEN_METEO))
            }
            RadioRow("MET Norway", settings.weatherProvider == WeatherProvider.MET_NORWAY) {
                onChange(settings.copy(weatherProvider = WeatherProvider.MET_NORWAY))
            }
        }

        SectionLabel("Уведомления")
        SettingsGroup {
            SwitchRow("Постоянное (шторка и виджеты)", settings.shadeEnabled) {
                onChange(settings.copy(shadeEnabled = it))
            }
            if (settings.shadeEnabled) {
                ValueRow("Интервал", INTERVALS.firstOrNull { it.second == settings.shadeIntervalMinutes }?.first ?: "${settings.shadeIntervalMinutes} мин") {
                    intervalDialog = true
                }
            }
            SwitchRow("Утром — на сегодня", settings.morningEnabled) {
                onChange(settings.copy(morningEnabled = it))
            }
            if (settings.morningEnabled) {
                StepperRow("Время", "${settings.morningHour}:00") { delta ->
                    onChange(settings.copy(morningHour = (settings.morningHour + delta + 24) % 24))
                }
            }
            SwitchRow("Вечером — на завтра", settings.eveningEnabled) {
                onChange(settings.copy(eveningEnabled = it))
            }
            if (settings.eveningEnabled) {
                StepperRow("Время", "${settings.eveningHour}:00") { delta ->
                    onChange(settings.copy(eveningHour = (settings.eveningHour + delta + 24) % 24))
                }
            }
        }

        SectionLabel("Виджеты")
        SettingsGroup {
            SliderRow("Прозрачность", settings.widgetTransparency) { value ->
                onChange(settings.copy(widgetTransparency = value))
            }
            SwitchRow("Чёрно-белые иконки", settings.widgetMonochrome) {
                onChange(settings.copy(widgetMonochrome = it))
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    if (intervalDialog) {
        AlertDialog(
            onDismissRequest = { intervalDialog = false },
            confirmButton = {
                TextButton(onClick = { intervalDialog = false }) { Text("Закрыть") }
            },
            title = { Text("Интервал обновления") },
            text = {
                Column {
                    INTERVALS.forEach { (label, minutes) ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                onChange(settings.copy(shadeIntervalMinutes = minutes))
                                intervalDialog = false
                            }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = settings.shadeIntervalMinutes == minutes,
                                onClick = {
                                    onChange(settings.copy(shadeIntervalMinutes = minutes))
                                    intervalDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary),
                            )
                            Text(label, fontSize = 15.sp)
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Column { content() }
    }
}

@Composable
private fun SegmentedRow(label: String, options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Row(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
        ) {
            options.forEachIndexed { i, option ->
                val selected = i == selectedIndex
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { onSelect(i) }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text(
                        option,
                        fontSize = 13.sp,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    )
                }
            }
        }
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onSelect).padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 16.sp, modifier = Modifier.weight(1f))
        RadioButton(selected = selected, onClick = onSelect, colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary))
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun ValueRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 16.sp)
        Text(value, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}

@Composable
private fun SliderRow(label: String, initialPercent: Int, onValue: (Int) -> Unit) {
    var value by remember { mutableFloatStateOf(initialPercent / 100f) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 16.sp)
            Text("${(value * 100).toInt()}%", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
        Slider(
            value = value,
            onValueChange = { v ->
                value = v
                onValue((v * 100).toInt())
            },
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(activeTrackColor = MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun StepperRow(label: String, value: String, onStep: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 16.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                    .clickable { onStep(-1) }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) { Text("−", fontSize = 16.sp) }
            Text(value, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 14.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                    .clickable { onStep(+1) }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) { Text("+", fontSize = 16.sp) }
        }
    }
}
