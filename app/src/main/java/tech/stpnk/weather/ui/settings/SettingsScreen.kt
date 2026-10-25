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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.stpnk.weather.R
import tech.stpnk.weather.data.prefs.AppSettings
import tech.stpnk.weather.data.prefs.PressureUnit
import tech.stpnk.weather.data.prefs.TempUnit
import tech.stpnk.weather.data.prefs.ThemeMode
import tech.stpnk.weather.data.prefs.WindUnit

@Composable
fun SettingsScreen(settings: AppSettings, onChange: (AppSettings) -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Назад",
                    tint = MaterialTheme.colorScheme.primary,
                )
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

        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.attribution),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
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
