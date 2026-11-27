package com.example.ui

import android.content.Intent
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.DataSourceMode
import com.example.data.ServerConfig
import com.example.domain.UnitConverter
import com.example.domain.WeatherExportPayload
import com.example.domain.WeatherReportFormatter
import com.example.model.PressureUnit
import com.example.model.UnitSystem
import com.example.model.WeatherAlertConfig
import com.example.model.WeatherAlertSeverity
import com.example.model.WindSpeedUnit
import com.example.ui.components.AirportSearchBar
import com.example.ui.components.CenterWeatherPanel
import com.example.ui.components.CrosswindCalculatorDialog
import com.example.ui.components.DualRunwayWindRosePanel
import com.example.ui.components.ExportWeatherDialog
import com.example.ui.components.LogViewerDialog
import com.example.ui.components.MetarTafFooter
import com.example.ui.components.MeteoTopToolbar
import com.example.ui.components.PressureConversionUtilityDialog
import com.example.ui.components.RunwayColumnPanel
import com.example.ui.components.StationAndMetarDialog
import com.example.ui.components.WeatherAlertsDialog
import com.example.ui.components.calculateRunwayWindComponents
import com.example.ui.theme.LocalMeteoPalette
import com.example.viewmodel.MeteoUiState
import com.example.viewmodel.MeteoViewModel
import java.util.Locale

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
    onSetNightMode = viewModel::setNightMode,
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
    onSelectUnitSystem = viewModel::applyUnitSystem,
    onToggleAutoRefresh = viewModel::setAutoRefreshEnabled,
    onSelectAutoRefreshInterval = viewModel::setAutoRefreshInterval,
    onToggleFavorite = viewModel::toggleFavorite,
    onAddFavorite = { icao -> viewModel.addFavorite(icao, selectAfterAdd = true) },
    onRemoveFavorite = viewModel::removeFavorite,
    onSelectSourceMode = { mode -> viewModel.setDataSourceMode(mode, fetchNow = true) },
    onSaveServerConfig = { cfg -> viewModel.updateServerConfig(cfg, fetchNow = true) },
    onSaveCurrentSetup = viewModel::saveCurrentSetup,
    onApplySavedPreset = viewModel::applySavedPreset,
    onDeleteSavedPreset = viewModel::deleteSavedPreset,
    onUpdateAlertConfig = viewModel::updateAlertConfig,
    onCheckFavoritesNow = viewModel::checkFavoritesWeatherNow,
    onOpenAlertStation = viewModel::openAlertStation,
    onMarkAllAlertsRead = viewModel::markAllAlertsRead,
    onClearAlerts = viewModel::clearWeatherAlerts,
    onDismissWeatherAlertBanner = viewModel::dismissLatestWeatherAlert,
    onDismissBanner = viewModel::dismissBanner,
    onRefreshMapWeather = viewModel::refreshMapWeather,
    onRefreshMapStation = viewModel::fetchStationWeatherForMap,
    onLoad24hHistory = viewModel::load24hHistory,
    onSelectD3TrendAirport = { icao -> viewModel.selectD3TrendAirport(icao) },
    onLoad24hHistoryForIcao = { icao -> viewModel.load24hHistoryForIcao(icao, force = true) },
    onDetectGpsLocation = viewModel::detectLocationAndUpdateWeather,
    onFetchByCoordinates = { lat, lon -> viewModel.fetchWeatherByCoordinates(lat, lon) },
    onToggleVfrMonitoredAirport = viewModel::toggleVfrMonitoredAirport,
    onSetVfrMonitorAllFavorites = viewModel::setVfrMonitorAllFavorites,
    onCheckVfrNow = viewModel::checkVfrMinimumsNow,
    onClearRecentSearches = viewModel::clearRecentSearches,
    modifier = modifier,
  )
}

@Composable
fun MeteoTabloContent(
  uiState: MeteoUiState,
  onRefresh: () -> Unit,
  onToggleNight: () -> Unit,
  onSetNightMode: (Boolean) -> Unit = {},
  onToggleFullscreen: () -> Unit,
  onSelectRunwayPair: (Int) -> Unit,
  onSelectStation: (String) -> Unit,
  onApplyCustomMetar: (String, String) -> Unit,
  onToggleWindUnit: () -> Unit,
  onSelectWindUnit: (WindSpeedUnit) -> Unit,
  onTogglePressureUnit: () -> Unit,
  onSelectPressureUnit: (PressureUnit) -> Unit,
  onSelectUnitSystem: (UnitSystem) -> Unit = {},
  onToggleAutoRefresh: (Boolean) -> Unit = {},
  onSelectAutoRefreshInterval: (Int) -> Unit = {},
  onToggleFavorite: (String) -> Unit,
  onAddFavorite: (String) -> Unit,
  onRemoveFavorite: (String) -> Unit,
  onSelectSourceMode: (DataSourceMode) -> Unit,
  onSaveServerConfig: (ServerConfig) -> Unit,
  onSaveCurrentSetup: (String?) -> Unit = {},
  onApplySavedPreset: (String) -> Unit = {},
  onDeleteSavedPreset: (String) -> Unit = {},
  onUpdateAlertConfig: (WeatherAlertConfig) -> Unit = {},
  onCheckFavoritesNow: () -> Unit = {},
  onOpenAlertStation: (String) -> Unit = {},
  onMarkAllAlertsRead: () -> Unit = {},
  onClearAlerts: () -> Unit = {},
  onDismissWeatherAlertBanner: () -> Unit = {},
  onDismissBanner: () -> Unit,
  onRefreshMapWeather: () -> Unit = {},
  onRefreshMapStation: (String) -> Unit = {},
  onLoad24hHistory: (Boolean) -> Unit = {},
  onSelectD3TrendAirport: (String) -> Unit = {},
  onLoad24hHistoryForIcao: (String) -> Unit = {},
  onDetectGpsLocation: () -> Unit = {},
  onFetchByCoordinates: (Double, Double) -> Unit = { _, _ -> },
  onToggleVfrMonitoredAirport: (String) -> Unit = {},
  onSetVfrMonitorAllFavorites: (Boolean) -> Unit = {},
  onCheckVfrNow: () -> Unit = {},
  onClearRecentSearches: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val palette = LocalMeteoPalette.current
  val context = LocalContext.current
  var showStationDialog by remember { mutableStateOf(false) }
  var stationDialogInitialTab by remember { mutableIntStateOf(0) }
  var showLogDialog by remember { mutableStateOf(false) }
  var showAlertsDialog by remember { mutableStateOf(false) }
  var showMetarTable by remember { mutableStateOf(false) }
  var showSettings by remember { mutableStateOf(false) }
  var showMapScreen by remember { mutableStateOf(false) }
  var showHistory24h by remember { mutableStateOf(false) }
  var showThresholdConfig by remember { mutableStateOf(false) }
  var showExportDialog by remember { mutableStateOf(false) }
  var showDecoderScreen by remember { mutableStateOf(false) }
  var showPressureUtility by remember { mutableStateOf(false) }
  var showDualWindRose by remember { mutableStateOf(false) }
  var showMyAirportsScreen by remember { mutableStateOf(false) }
  var showD3TrendsScreen by remember { mutableStateOf(false) }
  var showCrosswindCalculator by remember { mutableStateOf(false) }

  fun launchWeatherShareSheet() {
    val payload = WeatherExportPayload(
      station = uiState.station,
      weather = uiState.weather,
      primaryRunwayCalc = uiState.primaryRunwayCalc,
      reciprocalRunwayCalc = uiState.reciprocalRunwayCalc,
      windUnit = uiState.windUnit,
      pressureUnit = uiState.pressureUnit,
      windTrend = uiState.windTrend,
      qnhTrend24h = uiState.qnhTrend24h,
      weatherHistory24h = uiState.weatherHistory24h,
    )
    val summaryText = WeatherReportFormatter.buildTextSummary(payload)
    runCatching {
      val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "METEO ${uiState.station.icao} (${uiState.weather.observationTimeUtc})")
        putExtra(Intent.EXTRA_TEXT, summaryText)
      }
      context.startActivity(
        Intent.createChooser(shareIntent, "Поделиться сводкой ${uiState.station.icao}"),
      )
    }
  }

  if (showDecoderScreen) {
    MetarTafDecoderScreen(
      station = uiState.station,
      weather = uiState.weather,
      isFullscreen = uiState.isFullscreen,
      onBack = { showDecoderScreen = false },
      modifier = modifier,
    )
  } else if (showThresholdConfig) {
    ThresholdConfigScreen(
      station = uiState.station,
      weather = uiState.weather,
      windUnit = uiState.windUnit,
      alertConfig = uiState.alertConfig,
      isFullscreen = uiState.isFullscreen,
      onUpdateAlertConfig = onUpdateAlertConfig,
      onBack = { showThresholdConfig = false },
      modifier = modifier,
    )
  } else if (showHistory24h) {
    WeatherHistory24hScreen(
      station = uiState.station,
      entries = uiState.weatherHistory24h,
      qnhTrend = uiState.qnhTrend24h,
      windUnit = uiState.windUnit,
      pressureUnit = uiState.pressureUnit,
      alertConfig = uiState.alertConfig,
      isFullscreen = uiState.isFullscreen,
      isLoading = uiState.isLoadingHistory,
      statusMessage = uiState.historyStatusMessage,
      onBack = { showHistory24h = false },
      onRefresh24h = { onLoad24hHistory(true) },
      onToggleWindUnit = onToggleWindUnit,
      onTogglePressureUnit = onTogglePressureUnit,
      onSelectUnitSystem = onSelectUnitSystem,
      onOpenThresholdConfig = { showThresholdConfig = true },
      onOpenExportDialog = { showExportDialog = true },
      modifier = modifier,
    )
  } else if (showSettings) {
    SettingsScreen(
      station = uiState.station,
      weather = uiState.weather,
      primaryRunwayCalc = uiState.primaryRunwayCalc,
      windUnit = uiState.windUnit,
      pressureUnit = uiState.pressureUnit,
      isNightMode = uiState.isNightMode,
      isFullscreen = uiState.isFullscreen,
      alertConfig = uiState.alertConfig,
      autoRefreshEnabled = uiState.autoRefreshEnabled,
      autoRefreshIntervalMinutes = uiState.autoRefreshIntervalMinutes,
      onBack = { showSettings = false },
      onSelectUnitSystem = onSelectUnitSystem,
      onSelectWindUnit = onSelectWindUnit,
      onSelectPressureUnit = onSelectPressureUnit,
      onToggleNight = onToggleNight,
      onSetNightMode = onSetNightMode,
      onToggleFullscreen = onToggleFullscreen,
      onToggleAutoRefresh = onToggleAutoRefresh,
      onSelectAutoRefreshInterval = onSelectAutoRefreshInterval,
      onUpdateAlertConfig = onUpdateAlertConfig,
      onOpenServerSettings = {
        stationDialogInitialTab = 2
        showStationDialog = true
      },
      onOpenAlertsSettings = { showAlertsDialog = true },
      onOpenThresholdConfig = { showThresholdConfig = true },
      onOpenHistory24h = {
        showHistory24h = true
        onLoad24hHistory(false)
      },
      onOpenExportDialog = { showExportDialog = true },
      onOpenDecoder = { showDecoderScreen = true },
      onOpenPressureUtility = { showPressureUtility = true },
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
        selectedRunwayPairIndex = uiState.selectedRunwayPairIndex,
        savedPresets = uiState.savedPresets,
        onSaveCurrentSetup = onSaveCurrentSetup,
        onApplySavedPreset = onApplySavedPreset,
        onDeleteSavedPreset = onDeleteSavedPreset,
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
    if (showAlertsDialog) {
      WeatherAlertsDialog(
        favoriteIcaos = uiState.favoriteIcaos,
        alertConfig = uiState.alertConfig,
        alerts = uiState.weatherAlerts,
        isCheckingFavorites = uiState.isCheckingFavorites,
        onUpdateConfig = onUpdateAlertConfig,
        onCheckFavoritesNow = onCheckFavoritesNow,
        onOpenAlertStation = onOpenAlertStation,
        onMarkAllRead = onMarkAllAlertsRead,
        onClearAlerts = onClearAlerts,
        onDismiss = { showAlertsDialog = false },
        onToggleVfrMonitoredAirport = onToggleVfrMonitoredAirport,
        onSetVfrMonitorAllFavorites = onSetVfrMonitorAllFavorites,
        onCheckVfrNow = onCheckVfrNow,
      )
    }
  } else if (showMetarTable) {
    MetarTableScreen(
      station = uiState.station,
      weather = uiState.weather,
      windUnit = uiState.windUnit,
      pressureUnit = uiState.pressureUnit,
      favoriteIcaos = uiState.favoriteIcaos,
      isCurrentFavorite = uiState.isCurrentStationFavorite,
      isFullscreen = uiState.isFullscreen,
      windTrend = uiState.windTrend,
      activeRunway = uiState.primaryRunwayCalc,
      reciprocalRunway = uiState.reciprocalRunwayCalc,
      selectedRunwayPairIndex = uiState.selectedRunwayPairIndex,
      alertConfig = uiState.alertConfig,
      qnhTrend24h = uiState.qnhTrend24h,
      onBack = { showMetarTable = false },
      onEditRawMetar = { showStationDialog = true },
      onToggleWindUnit = onToggleWindUnit,
      onTogglePressureUnit = onTogglePressureUnit,
      onSelectUnitSystem = onSelectUnitSystem,
      onSelectRunwayPair = onSelectRunwayPair,
      onToggleFavorite = { onToggleFavorite(uiState.station.icao) },
      onSelectFavoriteStation = onSelectStation,
      onAddFavorite = onAddFavorite,
      onRemoveFavorite = onRemoveFavorite,
      onOpenThresholdConfig = { showThresholdConfig = true },
      onOpenHistory24h = {
        showHistory24h = true
        onLoad24hHistory(false)
      },
      onOpenExportDialog = { showExportDialog = true },
      onOpenDecoder = { showDecoderScreen = true },
      onOpenPressureUtility = { showPressureUtility = true },
      metarFetchSeq = uiState.metarFetchSeq,
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
        selectedRunwayPairIndex = uiState.selectedRunwayPairIndex,
        savedPresets = uiState.savedPresets,
        onSaveCurrentSetup = onSaveCurrentSetup,
        onApplySavedPreset = onApplySavedPreset,
        onDeleteSavedPreset = onDeleteSavedPreset,
        onSelectStation = onSelectStation,
        onApplyCustomMetar = onApplyCustomMetar,
        onSelectWindUnit = onSelectWindUnit,
        onSelectPressureUnit = onSelectPressureUnit,
        onToggleFavorite = onToggleFavorite,
        onSaveServerConfig = onSaveServerConfig,
        onDismiss = { showStationDialog = false },
      )
    }
  } else if (showMapScreen) {
    AirportMapScreen(
      stations = uiState.mapStations,
      weatherByIcao = uiState.mapWeatherByIcao,
      activeStationIcao = uiState.station.icao,
      favoriteIcaos = uiState.favoriteIcaos,
      windUnit = uiState.windUnit,
      pressureUnit = uiState.pressureUnit,
      isFullscreen = uiState.isFullscreen,
      isRefreshingMap = uiState.isRefreshingMap,
      loadingMapIcao = uiState.loadingMapIcao,
      isNightMode = uiState.isNightMode,
      onBack = { showMapScreen = false },
      onSelectStationOnBoard = { icao ->
        onSelectStation(icao)
        showMapScreen = false
      },
      onRefreshAll = onRefreshMapWeather,
      onRefreshStation = onRefreshMapStation,
      onToggleFavorite = onToggleFavorite,
      modifier = modifier,
    )
  } else if (showMyAirportsScreen) {
    MyAirportsScreen(
      currentStation = uiState.station,
      favoriteIcaos = uiState.favoriteIcaos,
      weatherByIcao = uiState.mapWeatherByIcao,
      windUnit = uiState.windUnit,
      pressureUnit = uiState.pressureUnit,
      isNightMode = uiState.isNightMode,
      isFullscreen = uiState.isFullscreen,
      onSelectStation = onSelectStation,
      onToggleCurrentAirport = { onToggleFavorite(uiState.station.icao) },
      onAddAirport = onAddFavorite,
      onRemoveAirport = onRemoveFavorite,
      onOpenMap = {
        showMyAirportsScreen = false
        showMapScreen = true
        onRefreshMapWeather()
      },
      onBack = { showMyAirportsScreen = false },
      modifier = modifier,
    )
  } else if (showD3TrendsScreen) {
    D3TrendsScreen(
      currentStation = uiState.station,
      currentWeather = uiState.weather,
      favoriteIcaos = uiState.favoriteIcaos,
      selectedD3Icao = uiState.selectedD3TrendIcao,
      weatherHistoryByIcao = uiState.weatherHistoryByIcaoMap,
      mapWeatherByIcao = uiState.mapWeatherByIcao,
      windUnit = uiState.windUnit,
      pressureUnit = uiState.pressureUnit,
      isNightMode = uiState.isNightMode,
      isFullscreen = uiState.isFullscreen,
      loadingHistoryIcao = uiState.loadingHistoryIcao,
      onSelectD3Airport = onSelectD3TrendAirport,
      onRefreshD3AirportHistory = onLoad24hHistoryForIcao,
      onOpenHistory24h = {
        showD3TrendsScreen = false
        showHistory24h = true
        onLoad24hHistory(false)
      },
      onOpenExportDialog = { showExportDialog = true },
      onBack = { showD3TrendsScreen = false },
      modifier = modifier,
    )
  } else {

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
          autoRefreshEnabled = uiState.autoRefreshEnabled,
          autoRefreshIntervalMinutes = uiState.autoRefreshIntervalMinutes,
          savedPresets = uiState.savedPresets,
          unreadAlertCount = uiState.unreadAlertCount,
          alertsEnabled = uiState.alertConfig.enabled,
          onSaveCurrentSetup = { onSaveCurrentSetup(null) },
          onApplySavedPreset = onApplySavedPreset,
          onOpenAlertsDialog = { showAlertsDialog = true },
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
          onOpenAboutDialog = {
            stationDialogInitialTab = 3
            showStationDialog = true
          },
          onOpenSettings = { showSettings = true },
          onOpenMap = {
            showMapScreen = true
            onRefreshMapWeather()
          },
          onOpenHistory24h = {
            showHistory24h = true
            onLoad24hHistory(false)
          },
          onOpenExportDialog = { showExportDialog = true },
          onOpenDecoder = { showDecoderScreen = true },
          onOpenPressureUtility = { showPressureUtility = true },
          onOpenMyAirports = { showMyAirportsScreen = true },
          onOpenD3Trends = {
            showD3TrendsScreen = true
            val trendIcao = uiState.selectedD3TrendIcao.ifBlank { uiState.station.icao }
            onLoad24hHistoryForIcao(trendIcao)
          },
          onOpenCrosswindCalculator = { showCrosswindCalculator = true },
          onShareWeather = { launchWeatherShareSheet() },
          favoriteIcaos = uiState.favoriteIcaos,
          showDualWindRose = showDualWindRose,
          onToggleDualWindRose = { showDualWindRose = !showDualWindRose },
          compactMode = isCompactHeight,
        )

        AirportSearchBar(
          currentIcao = uiState.station.icao,
          favoriteIcaos = uiState.favoriteIcaos,
          isLoading = uiState.isLoading,
          onSelectStation = onSelectStation,
          compactMode = isCompactHeight,
          isDetectingLocation = uiState.isDetectingLocation,
          detectedLocationSummary = uiState.detectedLocationSummary,
          onDetectGpsLocation = onDetectGpsLocation,
          onFetchByCoordinates = onFetchByCoordinates,
          recentSearches = uiState.recentSearches,
          onClearRecentSearches = onClearRecentSearches,
        )

        // Significant weather change alert banner for favorite stations
        val latestAlert = uiState.latestWeatherAlert
        AnimatedVisibility(visible = latestAlert != null) {
          if (latestAlert != null) {
            val accent = if (latestAlert.severity == WeatherAlertSeverity.CRITICAL) {
              palette.tailwindNegative
            } else {
              palette.metarAmber
            }
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("weather_alert_banner")
                .background(palette.fieldBackground, RoundedCornerShape(3.dp))
                .border(1.dp, accent, RoundedCornerShape(3.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
              Text(
                text = "⚠ ${latestAlert.icao} (${latestAlert.observationTimeUtc}): ${latestAlert.summary}",
                color = accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                  .weight(1f)
                  .clickable { onOpenAlertStation(latestAlert.icao) },
              )
              Text(
                text = "Открыть",
                color = palette.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                  .clickable { onOpenAlertStation(latestAlert.icao) }
                  .padding(horizontal = 8.dp, vertical = 4.dp),
              )
              Text(
                text = "OK",
                color = palette.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                  .clickable(onClick = onDismissWeatherAlertBanner)
                  .padding(horizontal = 8.dp, vertical = 4.dp),
              )
            }
          }
        }

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

        // Aircraft Maximum Crosswind Threshold Warning Banner
        val activeWindComponents = remember(uiState.primaryRunwayCalc.headingDeg, uiState.weather.wind) {
          calculateRunwayWindComponents(uiState.primaryRunwayCalc.headingDeg, uiState.weather.wind)
        }
        val crosswindExceeded = uiState.alertConfig.isCrosswindExceeded(
          crosswindKt = uiState.primaryRunwayCalc.crosswindKt,
          gustCrosswindKt = activeWindComponents.gustCrosswindKt,
        )
        val activeCrosswindLimitKt = if (uiState.alertConfig.crosswindWarningEnabled) {
          uiState.alertConfig.maxCrosswindKt
        } else {
          null
        }
        AnimatedVisibility(visible = crosswindExceeded) {
          val crossVal = UnitConverter.convertWindFromKnots(uiState.primaryRunwayCalc.crosswindKt, uiState.windUnit)
          val limitVal = UnitConverter.convertWindFromKnots(uiState.alertConfig.maxCrosswindKt.toDouble(), uiState.windUnit)
          val gustNote = activeWindComponents.gustCrosswindKt?.let { gKt ->
            if (gKt > uiState.alertConfig.maxCrosswindKt + 0.05) {
              String.format(Locale.US, " (порывы %.1f)", UnitConverter.convertWindFromKnots(gKt, uiState.windUnit))
            } else {
              ""
            }
          }.orEmpty()
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("crosswind_limit_warning_banner")
              .background(palette.tailwindNegative.copy(alpha = 0.14f), RoundedCornerShape(3.dp))
              .border(1.2.dp, palette.tailwindNegative, RoundedCornerShape(3.dp))
              .padding(horizontal = 10.dp, vertical = 6.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.Warning,
              contentDescription = "Превышение бокового ветра",
              tint = palette.tailwindNegative,
            )
            Text(
              text = String.format(
                Locale.US,
                "ПРЕВЫШЕНИЕ БОКОВОГО ВЕТРА (%s · ВПП %s): %.1f%s %s > лимита %.0f %s",
                uiState.alertConfig.resolvedAircraftName(),
                uiState.primaryRunwayCalc.designator,
                crossVal,
                gustNote,
                uiState.windUnit.label,
                limitVal,
                uiState.windUnit.label,
              ),
              color = palette.tailwindNegative,
              fontSize = 11.5.sp,
              fontWeight = FontWeight.ExtraBold,
              modifier = Modifier.weight(1f),
            )
            Text(
              text = "Лимиты",
              color = palette.textPrimary,
              fontSize = 11.5.sp,
              fontWeight = FontWeight.Bold,
              modifier = Modifier
                .clickable { showSettings = true }
                .padding(horizontal = 8.dp, vertical = 4.dp),
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
                maxCrosswindKt = activeCrosswindLimitKt,
                compactMode = isCompactHeight,
                metarFetchSeq = uiState.metarFetchSeq,
                modifier = Modifier.weight(1.05f),
              )

              CenterWeatherPanel(
                weather = uiState.weather,
                localClock = uiState.liveLocalClock,
                pressureUnit = uiState.pressureUnit,
                onTogglePressureUnit = onTogglePressureUnit,
                station = uiState.station,
                windTrend = uiState.windTrend,
                windUnit = uiState.windUnit,
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
                maxCrosswindKt = activeCrosswindLimitKt,
                compactMode = isCompactHeight,
                metarFetchSeq = uiState.metarFetchSeq,
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
              maxCrosswindKt = activeCrosswindLimitKt,
              metarFetchSeq = uiState.metarFetchSeq,
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
              maxCrosswindKt = activeCrosswindLimitKt,
              metarFetchSeq = uiState.metarFetchSeq,
            )

            CenterWeatherPanel(
              weather = uiState.weather,
              localClock = uiState.liveLocalClock,
              pressureUnit = uiState.pressureUnit,
              onTogglePressureUnit = onTogglePressureUnit,
              station = uiState.station,
              windTrend = uiState.windTrend,
              windUnit = uiState.windUnit,
            )
          }

          if (!isCompactHeight) {
            Spacer(modifier = Modifier.height(2.dp))
          }

          AnimatedVisibility(visible = showDualWindRose) {
            DualRunwayWindRosePanel(
              station = uiState.station,
              selectedRunwayPairIndex = uiState.selectedRunwayPairIndex,
              primaryCalc = uiState.primaryRunwayCalc,
              reciprocalCalc = uiState.reciprocalRunwayCalc,
              wind = uiState.weather.wind,
              windUnit = uiState.windUnit,
              onSelectRunwayPair = onSelectRunwayPair,
              onToggleUnit = onToggleWindUnit,
              alertConfig = uiState.alertConfig,
              metarFetchSeq = uiState.metarFetchSeq,
            )
          }

          // Bottom TAF + METAR + Status Footer
          MetarTafFooter(
            weather = uiState.weather,
            station = uiState.station,
            onOpenMetarTable = { showMetarTable = true },
            onOpenAboutDialog = {
              stationDialogInitialTab = 3
              showStationDialog = true
            },
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
      selectedRunwayPairIndex = uiState.selectedRunwayPairIndex,
      savedPresets = uiState.savedPresets,
      onSaveCurrentSetup = onSaveCurrentSetup,
      onApplySavedPreset = onApplySavedPreset,
      onDeleteSavedPreset = onDeleteSavedPreset,
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

  if (showAlertsDialog) {
    WeatherAlertsDialog(
      favoriteIcaos = uiState.favoriteIcaos,
      alertConfig = uiState.alertConfig,
      alerts = uiState.weatherAlerts,
      isCheckingFavorites = uiState.isCheckingFavorites,
      onUpdateConfig = onUpdateAlertConfig,
      onCheckFavoritesNow = onCheckFavoritesNow,
      onOpenAlertStation = onOpenAlertStation,
      onMarkAllRead = onMarkAllAlertsRead,
      onClearAlerts = onClearAlerts,
      onDismiss = { showAlertsDialog = false },
      onToggleVfrMonitoredAirport = onToggleVfrMonitoredAirport,
      onSetVfrMonitorAllFavorites = onSetVfrMonitorAllFavorites,
      onCheckVfrNow = onCheckVfrNow,
    )
  }
  }

  if (showExportDialog) {
    val exportPayload = remember(
      uiState.station,
      uiState.weather,
      uiState.primaryRunwayCalc,
      uiState.reciprocalRunwayCalc,
      uiState.windUnit,
      uiState.pressureUnit,
      uiState.windTrend,
      uiState.qnhTrend24h,
      uiState.weatherHistory24h,
    ) {
      WeatherExportPayload(
        station = uiState.station,
        weather = uiState.weather,
        primaryRunwayCalc = uiState.primaryRunwayCalc,
        reciprocalRunwayCalc = uiState.reciprocalRunwayCalc,
        windUnit = uiState.windUnit,
        pressureUnit = uiState.pressureUnit,
        windTrend = uiState.windTrend,
        qnhTrend24h = uiState.qnhTrend24h,
        weatherHistory24h = uiState.weatherHistory24h,
      )
    }
    ExportWeatherDialog(
      payload = exportPayload,
      onDismiss = { showExportDialog = false },
    )
  }

  if (showPressureUtility) {
    PressureConversionUtilityDialog(
      station = uiState.station,
      weather = uiState.weather,
      boardPressureUnit = uiState.pressureUnit,
      onSelectBoardPressureUnit = onSelectPressureUnit,
      onDismiss = { showPressureUtility = false },
    )
  }

  if (showCrosswindCalculator) {
    val activeLimit = if (uiState.alertConfig.crosswindWarningEnabled) uiState.alertConfig.maxCrosswindKt else null
    CrosswindCalculatorDialog(
      station = uiState.station,
      wind = uiState.weather.wind,
      defaultHeadingDeg = uiState.primaryRunwayCalc.headingDeg,
      windUnit = uiState.windUnit,
      onToggleUnit = onToggleWindUnit,
      maxCrosswindKt = activeLimit,
      aircraftLabel = uiState.alertConfig.resolvedAircraftName(),
      onDismiss = { showCrosswindCalculator = false },
    )
  }
}
