package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.DataSourceMode
import com.example.data.ServerConfig
import com.example.model.PressureUnit
import com.example.model.WindSpeedUnit
import com.example.ui.components.CenterWeatherPanel
import com.example.ui.components.LogViewerDialog
import com.example.ui.components.MetarTafFooter
import com.example.ui.components.MeteoTopToolbar
import com.example.ui.components.RunwayColumnPanel
import com.example.ui.components.StationAndMetarDialog
import com.example.ui.theme.LocalMeteoPalette
import com.example.viewmodel.MeteoUiState
import com.example.viewmodel.MeteoViewModel

@Composable
fun MeteoTabloScreen(
  viewModel: MeteoViewModel,
  onFullscreenChanged: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  MeteoTabloContent(
    uiState = uiState,
    onRefresh = viewModel::refresh,
    onToggleNight = viewModel::toggleNightMode,
    onToggleFullscreen = {
      val next = !uiState.isFullscreen
      viewModel.toggleFullscreen()
      onFullscreenChanged(next)
    },
    onSelectRunwayPair = viewModel::selectRunwayPair,
    onSelectStation = viewModel::selectStation,
    onApplyCustomMetar = viewModel::applyCustomMetar,
    onToggleWindUnit = viewModel::toggleWindUnit,
    onSelectWindUnit = viewModel::setWindUnit,
    onTogglePressureUnit = viewModel::togglePressureUnit,
    onSelectPressureUnit = viewModel::setPressureUnit,
    onToggleFavorite = viewModel::toggleFavorite,
    onAddFavorite = { icao -> viewModel.addFavorite(icao, selectAfterAdd = true) },
    onRemoveFavorite = viewModel::removeFavorite,
    onSelectSourceMode = { mode -> viewModel.setDataSourceMode(mode, fetchNow = true) },
    onSaveServerConfig = { cfg -> viewModel.updateServerConfig(cfg, fetchNow = true) },
    onDismissBanner = viewModel::dismissBanner,
    modifier = modifier,
  )
}

@Composable
fun MeteoTabloContent(
  uiState: MeteoUiState,
  onRefresh: () -> Unit,
  onToggleNight: () -> Unit,
  onToggleFullscreen: () -> Unit,
  onSelectRunwayPair: (Int) -> Unit,
  onSelectStation: (String) -> Unit,
  onApplyCustomMetar: (String, String) -> Unit,
  onToggleWindUnit: () -> Unit,
  onSelectWindUnit: (WindSpeedUnit) -> Unit,
  onTogglePressureUnit: () -> Unit,
  onSelectPressureUnit: (PressureUnit) -> Unit,
  onToggleFavorite: (String) -> Unit,
  onAddFavorite: (String) -> Unit,
  onRemoveFavorite: (String) -> Unit,
  onSelectSourceMode: (DataSourceMode) -> Unit,
  onSaveServerConfig: (ServerConfig) -> Unit,
  onDismissBanner: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalMeteoPalette.current
  var showStationDialog by remember { mutableStateOf(false) }
  var stationDialogInitialTab by remember { mutableIntStateOf(0) }
  var showLogDialog by remember { mutableStateOf(false) }
  var showMetarTable by remember { mutableStateOf(false) }

  if (showMetarTable) {
    MetarTableScreen(
      station = uiState.station,
      weather = uiState.weather,
      windUnit = uiState.windUnit,
      pressureUnit = uiState.pressureUnit,
      favoriteIcaos = uiState.favoriteIcaos,
      isCurrentFavorite = uiState.isCurrentStationFavorite,
      isFullscreen = uiState.isFullscreen,
      onBack = { showMetarTable = false },
      onEditRawMetar = { showStationDialog = true },
      onToggleWindUnit = onToggleWindUnit,
      onTogglePressureUnit = onTogglePressureUnit,
      onToggleFavorite = { onToggleFavorite(uiState.station.icao) },
      onSelectFavoriteStation = onSelectStation,
      onAddFavorite = onAddFavorite,
      onRemoveFavorite = onRemoveFavorite,
      modifier = modifier,
    )
    if (showStationDialog) {
      StationAndMetarDialog(
        currentStation = uiState.station,
        currentMetar = uiState.weather.rawMetar,
        currentTaf = uiState.weather.rawTaf,
        windUnit = uiState.windUnit,
        pressureUnit = uiState.pressureUnit,
        favoriteIcaos = uiState.favoriteIcaos,
        serverConfig = uiState.serverConfig,
        onSelectStation = onSelectStation,
        onApplyCustomMetar = onApplyCustomMetar,
        onSelectWindUnit = onSelectWindUnit,
        onSelectPressureUnit = onSelectPressureUnit,
        onToggleFavorite = onToggleFavorite,
        onSaveServerConfig = onSaveServerConfig,
        onDismiss = { showStationDialog = false },
      )
    }
    return
  }

  val safeModifier = if (uiState.isFullscreen) {
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
    BoxWithConstraints(
      modifier = Modifier
        .fillMaxSize()
        .widthIn(max = 1280.dp)
        .align(Alignment.TopCenter),
    ) {
      val isWideLayout = maxWidth >= 560.dp || maxWidth > maxHeight
      val isCompactHeight = isWideLayout && maxHeight < 520.dp

      Column(
        verticalArrangement = Arrangement.spacedBy(if (isCompactHeight) 4.dp else 8.dp),
        modifier = Modifier
          .fillMaxSize()
          .verticalScroll(rememberScrollState()),
      ) {
        // Top Toolbar
        MeteoTopToolbar(
          station = uiState.station,
          metarObsUtc = uiState.weather.observationTimeUtc,
          isNightMode = uiState.isNightMode,
          isFullscreen = uiState.isFullscreen,
          isLoading = uiState.isLoading,
          windUnit = uiState.windUnit,
          pressureUnit = uiState.pressureUnit,
          isFavorite = uiState.isCurrentStationFavorite,
          serverConfig = uiState.serverConfig,
          onRefresh = onRefresh,
          onToggleNight = onToggleNight,
          onOpenLog = { showLogDialog = true },
          onOpenMetarTable = { showMetarTable = true },
          onToggleFullscreen = onToggleFullscreen,
          onToggleWindUnit = onToggleWindUnit,
          onTogglePressureUnit = onTogglePressureUnit,
          onToggleFavorite = { onToggleFavorite(uiState.station.icao) },
          onSelectStation = onSelectStation,
          onSelectSourceMode = onSelectSourceMode,
          onOpenStationDialog = {
            stationDialogInitialTab = 0
            showStationDialog = true
          },
          onOpenServerDialog = {
            stationDialogInitialTab = 2
            showStationDialog = true
          },
          compactMode = isCompactHeight,
        )

        // Optional transient network warning banner
        AnimatedVisibility(visible = uiState.statusBanner != null) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
              .fillMaxWidth()
              .background(palette.fieldBackground, RoundedCornerShape(3.dp))
              .border(1.dp, palette.metarAmber, RoundedCornerShape(3.dp))
              .padding(horizontal = 10.dp, vertical = 6.dp),
          ) {
            Text(
              text = uiState.statusBanner.orEmpty(),
              color = palette.metarAmber,
              fontSize = 12.sp,
              fontWeight = FontWeight.Medium,
              modifier = Modifier.weight(1f),
            )
            Text(
              text = "OK",
              color = palette.textPrimary,
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
              modifier = Modifier
                .clickable(onClick = onDismissBanner)
                .padding(horizontal = 8.dp, vertical = 2.dp),
            )
          }
        }

        // Main Instrument Board Container
        Column(
          verticalArrangement = Arrangement.spacedBy(if (isCompactHeight) 5.dp else 10.dp),
          modifier = Modifier
            .fillMaxWidth()
            .background(palette.panelSurface)
            .border(1.dp, palette.border)
            .padding(if (isCompactHeight) 6.dp else 10.dp),
        ) {
          if (isWideLayout) {
            // 3-Column Classic Meteotablo (Landscape / Tablet)
            Row(
              horizontalArrangement = Arrangement.spacedBy(if (isCompactHeight) 10.dp else 14.dp),
              verticalAlignment = Alignment.Top,
              modifier = Modifier.fillMaxWidth(),
            ) {
              RunwayColumnPanel(
                station = uiState.station,
                selectedRunwayIndex = uiState.selectedRunwayPairIndex,
                calculation = uiState.primaryRunwayCalc,
                weather = uiState.weather,
                windUnit = uiState.windUnit,
                pressureUnit = uiState.pressureUnit,
                isPrimarySelector = true,
                onSelectRunwayPair = onSelectRunwayPair,
                onSelectStation = onSelectStation,
                onOpenStationDialog = { showStationDialog = true },
                onToggleUnit = onToggleWindUnit,
                onTogglePressureUnit = onTogglePressureUnit,
                compactMode = isCompactHeight,
                modifier = Modifier.weight(1.05f),
              )

              CenterWeatherPanel(
                weather = uiState.weather,
                localClock = uiState.liveLocalClock,
                pressureUnit = uiState.pressureUnit,
                onTogglePressureUnit = onTogglePressureUnit,
                compactMode = isCompactHeight,
                modifier = Modifier.weight(0.95f),
              )

              RunwayColumnPanel(
                station = uiState.station,
                selectedRunwayIndex = uiState.selectedRunwayPairIndex,
                calculation = uiState.reciprocalRunwayCalc,
                weather = uiState.weather,
                windUnit = uiState.windUnit,
                pressureUnit = uiState.pressureUnit,
                isPrimarySelector = false,
                onSelectRunwayPair = onSelectRunwayPair,
                onSelectStation = onSelectStation,
                onOpenStationDialog = { showStationDialog = true },
                onToggleUnit = onToggleWindUnit,
                onTogglePressureUnit = onTogglePressureUnit,
                compactMode = isCompactHeight,
                modifier = Modifier.weight(1.05f),
              )
            }
          } else {
            // Compact Portrait Phone Layout: Both Runways + Center Weather stacked cleanly
            RunwayColumnPanel(
              station = uiState.station,
              selectedRunwayIndex = uiState.selectedRunwayPairIndex,
              calculation = uiState.primaryRunwayCalc,
              weather = uiState.weather,
              windUnit = uiState.windUnit,
              pressureUnit = uiState.pressureUnit,
              isPrimarySelector = true,
              onSelectRunwayPair = onSelectRunwayPair,
              onSelectStation = onSelectStation,
              onOpenStationDialog = { showStationDialog = true },
              onToggleUnit = onToggleWindUnit,
              onTogglePressureUnit = onTogglePressureUnit,
            )

            RunwayColumnPanel(
              station = uiState.station,
              selectedRunwayIndex = uiState.selectedRunwayPairIndex,
              calculation = uiState.reciprocalRunwayCalc,
              weather = uiState.weather,
              windUnit = uiState.windUnit,
              pressureUnit = uiState.pressureUnit,
              isPrimarySelector = false,
              onSelectRunwayPair = onSelectRunwayPair,
              onSelectStation = onSelectStation,
              onOpenStationDialog = { showStationDialog = true },
              onToggleUnit = onToggleWindUnit,
              onTogglePressureUnit = onTogglePressureUnit,
            )

            CenterWeatherPanel(
              weather = uiState.weather,
              localClock = uiState.liveLocalClock,
              pressureUnit = uiState.pressureUnit,
              onTogglePressureUnit = onTogglePressureUnit,
            )
          }

          if (!isCompactHeight) {
            Spacer(modifier = Modifier.height(2.dp))
          }

          // Bottom TAF + METAR + Status Footer
          MetarTafFooter(
            weather = uiState.weather,
            station = uiState.station,
            onOpenMetarTable = { showMetarTable = true },
            compactMode = isCompactHeight,
          )
        }
      }
    }
  }

  if (showStationDialog) {
    StationAndMetarDialog(
      currentStation = uiState.station,
      currentMetar = uiState.weather.rawMetar,
      currentTaf = uiState.weather.rawTaf,
      windUnit = uiState.windUnit,
      pressureUnit = uiState.pressureUnit,
      favoriteIcaos = uiState.favoriteIcaos,
      serverConfig = uiState.serverConfig,
      onSelectStation = onSelectStation,
      onApplyCustomMetar = onApplyCustomMetar,
      onSelectWindUnit = onSelectWindUnit,
      onSelectPressureUnit = onSelectPressureUnit,
      onToggleFavorite = onToggleFavorite,
      onSaveServerConfig = onSaveServerConfig,
      onDismiss = { showStationDialog = false },
      initialTab = stationDialogInitialTab,
    )
  }

  if (showLogDialog) {
    LogViewerDialog(
      logs = uiState.logs,
      onDismiss = { showLogDialog = false },
    )
  }
}
