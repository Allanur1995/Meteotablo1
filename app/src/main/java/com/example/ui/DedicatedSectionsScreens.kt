package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AirportStation
import com.example.model.DecodedWeather
import com.example.model.PressureUnit
import com.example.model.WeatherHistoryEntry
import com.example.model.WindSpeedUnit
import com.example.ui.components.D3SavedAirportTrendsDashboard
import com.example.ui.components.MyAirportsBar
import com.example.ui.theme.LocalMeteoPalette

@Composable
fun MyAirportsScreen(
  currentStation: AirportStation,
  favoriteIcaos: List<String>,
  weatherByIcao: Map<String, DecodedWeather>,
  windUnit: WindSpeedUnit,
  pressureUnit: PressureUnit,
  isNightMode: Boolean,
  isFullscreen: Boolean,
  onSelectStation: (String) -> Unit,
  onToggleCurrentAirport: () -> Unit,
  onAddAirport: (String) -> Unit,
  onRemoveAirport: (String) -> Unit,
  onOpenMap: () -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  BackHandler(onBack = onBack)

  val palette = LocalMeteoPalette.current
  val safeModifier = if (isFullscreen) {
    Modifier
  } else {
    Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(palette.bezelSurface)
      .then(safeModifier)
      .padding(horizontal = 8.dp, vertical = 6.dp),
  ) {
    Column(
      verticalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier
        .fillMaxSize()
        .widthIn(max = 980.dp)
        .align(Alignment.TopCenter)
        .verticalScroll(rememberScrollState())
        .testTag("my_airports_screen"),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
          .fillMaxWidth()
          .background(palette.panelSurface)
          .border(1.dp, palette.border)
          .padding(horizontal = 6.dp, vertical = 4.dp),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.weight(1f),
        ) {
          IconButton(
            onClick = onBack,
            modifier = Modifier
              .size(48.dp)
              .testTag("my_airports_back_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Назад к табло",
              tint = palette.textPrimary,
            )
          }
          Column {
            Text(
              text = "Мои аэропорты · ${favoriteIcaos.size}",
              color = palette.textPrimary,
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = "Активный аэродром: ${currentStation.icao} (${currentStation.city})",
              color = palette.textSecondary,
              fontSize = 11.5.sp,
            )
          }
        }
        OutlinedButton(
          onClick = onOpenMap,
          modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag("my_airports_open_map_button"),
        ) {
          Icon(
            imageVector = Icons.Filled.Map,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Text(" Карта", fontSize = 12.sp)
        }
      }

      MyAirportsBar(
        currentIcao = currentStation.icao,
        myAirports = favoriteIcaos,
        weatherByIcao = weatherByIcao,
        windUnit = windUnit,
        pressureUnit = pressureUnit,
        onSelectStation = onSelectStation,
        onToggleCurrentAirport = onToggleCurrentAirport,
        onAddAirport = onAddAirport,
        onRemoveAirport = onRemoveAirport,
        isNightMode = isNightMode,
        compactMode = false,
      )
    }
  }
}

@Composable
fun D3TrendsScreen(
  currentStation: AirportStation,
  currentWeather: DecodedWeather,
  favoriteIcaos: List<String>,
  selectedD3Icao: String,
  weatherHistoryByIcao: Map<String, List<WeatherHistoryEntry>>,
  mapWeatherByIcao: Map<String, DecodedWeather>,
  windUnit: WindSpeedUnit,
  pressureUnit: PressureUnit,
  isNightMode: Boolean,
  isFullscreen: Boolean,
  loadingHistoryIcao: String?,
  onSelectD3Airport: (String) -> Unit,
  onRefreshD3AirportHistory: (String) -> Unit,
  onOpenHistory24h: () -> Unit,
  onOpenExportDialog: () -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  BackHandler(onBack = onBack)

  val palette = LocalMeteoPalette.current
  val safeModifier = if (isFullscreen) {
    Modifier
  } else {
    Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(palette.bezelSurface)
      .then(safeModifier)
      .padding(horizontal = 8.dp, vertical = 6.dp),
  ) {
    Column(
      verticalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier
        .fillMaxSize()
        .widthIn(max = 980.dp)
        .align(Alignment.TopCenter)
        .verticalScroll(rememberScrollState())
        .testTag("d3_trends_screen"),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
          .fillMaxWidth()
          .background(palette.panelSurface)
          .border(1.dp, palette.border)
          .padding(horizontal = 6.dp, vertical = 4.dp),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.weight(1f),
        ) {
          IconButton(
            onClick = onBack,
            modifier = Modifier
              .size(48.dp)
              .testTag("d3_trends_back_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Назад к табло",
              tint = palette.textPrimary,
            )
          }
          Column {
            Text(
              text = "D3.js · Давление и ветер (24ч)",
              color = palette.textPrimary,
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = "График для сохранённых аэродромов (${windUnit.label} / ${pressureUnit.label})",
              color = palette.textSecondary,
              fontSize = 11.5.sp,
            )
          }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          OutlinedButton(
            onClick = onOpenHistory24h,
            modifier = Modifier
              .heightIn(min = 48.dp)
              .testTag("d3_trends_open_history_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.History,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
            Text(" 24ч", fontSize = 12.sp)
          }
          OutlinedButton(
            onClick = onOpenExportDialog,
            modifier = Modifier
              .heightIn(min = 48.dp)
              .testTag("d3_trends_open_export_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.PictureAsPdf,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
            Text(" Экспорт", fontSize = 12.sp)
          }
        }
      }

      D3SavedAirportTrendsDashboard(
        currentStation = currentStation,
        currentWeather = currentWeather,
        favoriteIcaos = favoriteIcaos,
        selectedD3Icao = selectedD3Icao,
        weatherHistoryByIcao = weatherHistoryByIcao,
        mapWeatherByIcao = mapWeatherByIcao,
        windUnit = windUnit,
        pressureUnit = pressureUnit,
        isNightMode = isNightMode,
        loadingHistoryIcao = loadingHistoryIcao,
        onSelectD3Airport = onSelectD3Airport,
        onRefreshD3AirportHistory = onRefreshD3AirportHistory,
        compactMode = false,
      )
    }
  }
}
