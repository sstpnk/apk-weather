package tech.stpnk.weather.ui.cities

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.stpnk.weather.data.cities.CityEntry
import tech.stpnk.weather.data.geo.GeoHit

@Composable
fun CitiesScreen(viewModel: CitiesViewModel, onRequestLocation: () -> Unit, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = MaterialTheme.colorScheme.primary)
            }
            Text("Города", fontSize = 20.sp)
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.weight(1f),
            ) {
                Row(
                    Modifier.clickable(onClick = onRequestLocation).padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (state.locationWorking) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("Моё местоположение", fontSize = 15.sp)
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("Поиск города") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onQueryChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Очистить")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
        )

        state.error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(Modifier.fillMaxSize()) {
            if (state.results.isNotEmpty() || state.searching) {
                item {
                    Text(
                        "Результаты поиска",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
                if (state.searching) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(strokeWidth = 2.dp)
                        }
                    }
                }
                items(state.results) { hit ->
                    ResultRow(hit, enabled = !state.adding) { viewModel.add(hit) }
                }
                item { HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
            }

            item {
                Text(
                    "Мои города",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            items(state.cities) { city ->
                CityRow(city, city.url == state.selectedUrl, onSelect = { viewModel.select(city.url) }, onRemove = { viewModel.remove(city.url) })
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ResultRow(hit: GeoHit, enabled: Boolean, onAdd: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onAdd).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(hit.name, fontSize = 16.sp)
            listOfNotNull(hit.region, hit.country).joinToString(", ").takeIf { it.isNotBlank() }?.let {
                Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
            }
        }
        if (!enabled) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
    }
}

@Composable
private fun CityRow(city: CityEntry, selected: Boolean, onSelect: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onSelect).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(city.name, fontSize = 16.sp)
            city.region?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)) }
        }
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = "Выбран", tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
        }
        IconButton(onClick = onRemove, enabled = !selected) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Удалить",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (selected) 0.25f else 0.55f),
            )
        }
    }
}
