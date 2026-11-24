package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AirportCatalog
import com.example.data.AutoRefreshInterval
import com.example.data.AviationWeatherRepository
import com.example.data.DataSourceMode
import com.example.data.FavoriteAirportsStorage
import com.example.data.InMemoryFavoriteStorage
import com.example.data.InMemoryServerConfigStorage
import com.example.data.InMemoryUnitPreferencesStorage
import com.example.data.InMemoryWeatherAlertStorage
import com.example.data.InMemoryWeatherHistoryStorage
import com.example.data.InMemoryWeatherPresetStorage
import com.example.data.InMemoryWindHistoryStorage
import com.example.data.ServerConfig
import com.example.data.ServerConfigStorage
import com.example.data.UnitPreferences
import com.example.data.UnitPreferencesStorage
import com.example.data.WeatherAlertStorage
import com.example.data.WeatherHistoryStorage
import com.example.data.WeatherPresetStorage
import com.example.data.WindHistoryStorage
import com.example.diagnostics.CrashReporter
import com.example.domain.AviationWeatherParser
import com.example.domain.UnitConverter
import com.example.domain.WeatherChangeDetector
import com.example.domain.WeatherHistoryAnalyzer
import com.example.domain.WindTrendAnalyzer
import com.example.model.AirportStation
import com.example.model.DecodedWeather
import com.example.model.FavoriteWeatherSnapshot
import com.example.model.LogEntry
import com.example.model.PressureUnit
import com.example.model.QnhTrendSummary
import com.example.model.RunwayCalculation
import com.example.model.RunwayPair
import com.example.model.SavedWeatherPreset
import com.example.model.UnitSystem
import com.example.model.WeatherAlertConfig
import com.example.model.WeatherChangeAlert
import com.example.model.WeatherHistoryEntry
import com.example.model.WindHistoryEntry
import com.example.model.WindSpeedUnit
import com.example.model.WindTrendSummary
import com.example.notifications.LocalWeatherNotifier
import com.example.notifications.NoOpWeatherNotifier
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MeteoUiState(
  val station: AirportStation,
  val selectedRunwayPairIndex: Int,
  val weather: DecodedWeather,
  val primaryRunwayCalc: RunwayCalculation,
  val reciprocalRunwayCalc: RunwayCalculation,
  val isNightMode: Boolean = false,
  val isFullscreen: Boolean = false,
  val windUnit: WindSpeedUnit = WindSpeedUnit.KT,
  val pressureUnit: PressureUnit = PressureUnit.HPA,
  val autoRefreshEnabled: Boolean = true,
  val autoRefreshIntervalMinutes: Int = AutoRefreshInterval.FIFTEEN_MIN.minutes,
  val favoriteIcaos: List<String> = emptyList(),
  val savedPresets: List<SavedWeatherPreset> = emptyList(),
  val serverConfig: ServerConfig = ServerConfig(),
  val alertConfig: WeatherAlertConfig = WeatherAlertConfig(),
  val weatherAlerts: List<WeatherChangeAlert> = emptyList(),
  val latestWeatherAlert: WeatherChangeAlert? = null,
  val windTrend: WindTrendSummary = WindTrendAnalyzer.analyze("UTAM", emptyList()),
  val weatherHistory24h: List<WeatherHistoryEntry> = emptyList(),
  val weatherHistoryByIcaoMap: Map<String, List<WeatherHistoryEntry>> = emptyMap(),
  val selectedD3TrendIcao: String = "UTAA",
  val qnhTrend24h: QnhTrendSummary = WeatherHistoryAnalyzer.buildQnhTrend("UTAM", emptyList()),
  val isLoadingHistory: Boolean = false,
  val loadingHistoryIcao: String? = null,
  val historyStatusMessage: String? = null,
  val isCheckingFavorites: Boolean = false,
  val isLoading: Boolean = false,
  val isRefreshingMap: Boolean = false,
  val loadingMapIcao: String? = null,
  val mapStations: List<AirportStation> = AirportCatalog.presets,
  val mapWeatherByIcao: Map<String, DecodedWeather> = emptyMap(),
  val statusBanner: String? = null,
  val liveLocalClock: String = "22:11",
  val logs: List<LogEntry> = emptyList(),
) {
  val activeRunwayPair: RunwayPair
    get() = station.runways.getOrElse(selectedRunwayPairIndex) { station.runways.first() }

  val isCurrentStationFavorite: Boolean
    get() = station.icao in favoriteIcaos

  val currentSetupId: String
    get() = SavedWeatherPreset.buildId(station.icao, selectedRunwayPairIndex, windUnit, pressureUnit)

  val isCurrentSetupSaved: Boolean
    get() = savedPresets.any { it.id == currentSetupId }

  val unreadAlertCount: Int
    get() = weatherAlerts.count { !it.isRead }
}

class MeteoViewModel(
  private val favoriteStorage: FavoriteAirportsStorage = InMemoryFavoriteStorage(),
  private val serverConfigStorage: ServerConfigStorage = InMemoryServerConfigStorage(),
  private val presetStorage: WeatherPresetStorage = InMemoryWeatherPresetStorage(),
  private val alertStorage: WeatherAlertStorage = InMemoryWeatherAlertStorage(),
  private val windHistoryStorage: WindHistoryStorage = InMemoryWindHistoryStorage(),
  private val unitPreferencesStorage: UnitPreferencesStorage = InMemoryUnitPreferencesStorage(),
  private val weatherNotifier: LocalWeatherNotifier = NoOpWeatherNotifier,
  private val autoFetch: Boolean = true,
  private val weatherHistoryStorage: WeatherHistoryStorage = InMemoryWeatherHistoryStorage(),
) : ViewModel() {

  private val repository = AviationWeatherRepository()
  private val weatherSnapshots: MutableMap<String, FavoriteWeatherSnapshot> =
    alertStorage.loadSnapshots().toMutableMap()
  private val windHistoryByIcao: MutableMap<String, List<WindHistoryEntry>> =
    windHistoryStorage.loadAll().toMutableMap()
  private val weatherHistoryByIcao: MutableMap<String, List<WeatherHistoryEntry>> =
    weatherHistoryStorage.loadAll().toMutableMap()
  private val resolvedStationsByIcao: MutableMap<String, AirportStation> =
    AirportCatalog.presets.associateBy { it.icao }.toMutableMap()
  private var activeFetchJob: Job? = null
  private var historyFetchJob: Job? = null
  private var autoRefreshJob: Job? = null

  private val _uiState: MutableStateFlow<MeteoUiState>
  val uiState: StateFlow<MeteoUiState>

  init {
    val defaultStation = AirportCatalog.findOrDefault("UTAM")
    val initialWeather = repository.createEmptyWeather(
      station = defaultStation,
      sourceLabel = if (autoFetch) "загрузка с сервера..." else "ожидание обновления",
    )
    val pair = defaultStation.runways.first()
    val (primaryCalc, reciprocalCalc) = computeRunwayCalcs(pair, initialWeather)

    val initialFavorites = favoriteStorage.loadFavorites()
    val initialPresets = presetStorage.loadPresets()
    val initialServerConfig = serverConfigStorage.loadConfig()
    val initialAlertConfig = alertStorage.loadConfig()
    val initialAlerts = alertStorage.loadAlerts()
    val initialUnitPrefs = unitPreferencesStorage.loadPreferences()
    val initialTrend = currentTrendFor(defaultStation.icao)
    val initialHistory24h = currentHistory24hFor(defaultStation.icao)
    val initialHistorySnapshot = snapshotAllHistory24h()
    val initialQnhTrend = WeatherHistoryAnalyzer.buildQnhTrend(defaultStation.icao, initialHistory24h)
    val initialD3Icao = initialFavorites.firstOrNull() ?: defaultStation.icao
    val initialVfrMonitored = initialAlertConfig.resolvedVfrMonitoredIcaos(initialFavorites)
    _uiState = MutableStateFlow(
      MeteoUiState(
        station = defaultStation,
        selectedRunwayPairIndex = 0,
        weather = initialWeather,
        primaryRunwayCalc = primaryCalc,
        reciprocalRunwayCalc = reciprocalCalc,
        isNightMode = initialUnitPrefs.isNightMode,
        windUnit = initialUnitPrefs.windUnit,
        pressureUnit = initialUnitPrefs.pressureUnit,
        autoRefreshEnabled = initialUnitPrefs.autoRefreshEnabled,
        autoRefreshIntervalMinutes = AutoRefreshInterval.fromMinutes(initialUnitPrefs.autoRefreshIntervalMinutes).minutes,
        favoriteIcaos = initialFavorites,
        savedPresets = initialPresets,
        serverConfig = initialServerConfig,
        alertConfig = initialAlertConfig,
        weatherAlerts = initialAlerts,
        windTrend = initialTrend,
        weatherHistory24h = initialHistory24h,
        weatherHistoryByIcaoMap = initialHistorySnapshot,
        selectedD3TrendIcao = initialD3Icao,
        qnhTrend24h = initialQnhTrend,
        mapStations = buildMapStationList(defaultStation, initialFavorites),
        liveLocalClock = initialWeather.receivedTimeLocal,
        logs = listOf(
          LogEntry(
            timestamp = initialWeather.receivedTimeLocal,
            level = "INFO",
            message = "Инициализация станции ${defaultStation.icao}",
          ),
        ),
      ),
    )
    uiState = _uiState.asStateFlow()

    weatherNotifier.syncBackgroundSchedule(
      enabled = initialAlertConfig.enabled,
      intervalMinutes = initialAlertConfig.checkIntervalMinutes,
      hasFavorites = initialFavorites.isNotEmpty() || initialVfrMonitored.isNotEmpty(),
    )
    syncCrashStationContext()

    startClockTicker()
    if (autoFetch) {
      fetchLiveForStation(defaultStation)
      scheduleAutoRefreshLoop()
      startFavoriteMonitorLoop()
    }
  }

  private fun startClockTicker() {
    viewModelScope.launch {
      while (true) {
        delay(15_000L)
        val current = _uiState.value
        val nowLocal = AviationWeatherParser.currentLocalTimeString(current.station.utcOffsetHours)
        _uiState.update { it.copy(liveLocalClock = nowLocal) }
      }
    }
  }

  private fun scheduleAutoRefreshLoop() {
    autoRefreshJob?.cancel()
    if (!autoFetch || !_uiState.value.autoRefreshEnabled) return
    autoRefreshJob = viewModelScope.launch {
      while (true) {
        val intervalMin = AutoRefreshInterval.fromMinutes(_uiState.value.autoRefreshIntervalMinutes).minutes
        delay(intervalMin * 60_000L)
        if (_uiState.value.autoRefreshEnabled) {
          fetchLiveForStation(_uiState.value.station)
        }
      }
    }
  }

  private fun startFavoriteMonitorLoop() {
    viewModelScope.launch {
      while (true) {
        val intervalMin = _uiState.value.alertConfig.checkIntervalMinutes.coerceIn(5, 120)
        delay(intervalMin * 60_000L)
        if (_uiState.value.alertConfig.enabled && _uiState.value.favoriteIcaos.isNotEmpty()) {
          checkFavoritesWeatherNow()
        }
      }
    }
  }

  fun toggleNightMode() {
    _uiState.update { state ->
      val next = !state.isNightMode
      persistPreferences(
        state.windUnit,
        state.pressureUnit,
        state.autoRefreshEnabled,
        state.autoRefreshIntervalMinutes,
        next,
      )
      state.copy(
        isNightMode = next,
        logs = appendLog(
          state.logs,
          "UI",
          if (next) "Включен ночной кокпит (Dark)" else "Включен дневной кокпит (Light)",
        ),
      )
    }
  }

  fun setNightMode(nightMode: Boolean) {
    _uiState.update { state ->
      if (state.isNightMode == nightMode) return@update state
      persistPreferences(
        state.windUnit,
        state.pressureUnit,
        state.autoRefreshEnabled,
        state.autoRefreshIntervalMinutes,
        nightMode,
      )
      state.copy(
        isNightMode = nightMode,
        logs = appendLog(
          state.logs,
          "UI",
          if (nightMode) "Включен ночной кокпит (Dark)" else "Включен дневной кокпит (Light)",
        ),
      )
    }
  }

  fun toggleFullscreen() {
    _uiState.update { state ->
      state.copy(isFullscreen = !state.isFullscreen)
    }
  }

  private fun persistPreferences(
    windUnit: WindSpeedUnit,
    pressureUnit: PressureUnit,
    autoRefreshEnabled: Boolean,
    autoRefreshIntervalMinutes: Int,
    isNightMode: Boolean = _uiState.value.isNightMode,
  ) {
    unitPreferencesStorage.savePreferences(
      UnitPreferences(
        windUnit = windUnit,
        pressureUnit = pressureUnit,
        autoRefreshEnabled = autoRefreshEnabled,
        autoRefreshIntervalMinutes = AutoRefreshInterval.fromMinutes(autoRefreshIntervalMinutes).minutes,
        isNightMode = isNightMode,
      ),
    )
  }

  fun toggleWindUnit() {
    _uiState.update { state ->
      val next = UnitConverter.cycleWindUnit(state.windUnit)
      persistPreferences(next, state.pressureUnit, state.autoRefreshEnabled, state.autoRefreshIntervalMinutes)
      state.copy(
        windUnit = next,
        logs = appendLog(state.logs, "UI", "Единицы ветра: ${next.label}"),
      )
    }
  }

  fun setWindUnit(unit: WindSpeedUnit) {
    _uiState.update { state ->
      if (state.windUnit == unit) return@update state
      persistPreferences(unit, state.pressureUnit, state.autoRefreshEnabled, state.autoRefreshIntervalMinutes)
      state.copy(
        windUnit = unit,
        logs = appendLog(state.logs, "UI", "Единицы ветра: ${unit.label}"),
      )
    }
  }

  fun togglePressureUnit() {
    _uiState.update { state ->
      val next = UnitConverter.cyclePressureUnit(state.pressureUnit)
      persistPreferences(state.windUnit, next, state.autoRefreshEnabled, state.autoRefreshIntervalMinutes)
      state.copy(
        pressureUnit = next,
        logs = appendLog(state.logs, "UI", "Единицы давления: ${next.label}"),
      )
    }
  }

  fun applyUnitSystem(system: UnitSystem) {
    _uiState.update { state ->
      if (state.windUnit == system.windUnit && state.pressureUnit == system.pressureUnit) {
        return@update state
      }
      persistPreferences(
        system.windUnit,
        system.pressureUnit,
        state.autoRefreshEnabled,
        state.autoRefreshIntervalMinutes,
      )
      CrashReporter.log("UnitSystem changed to ${system.name}")
      state.copy(
        windUnit = system.windUnit,
        pressureUnit = system.pressureUnit,
        logs = appendLog(state.logs, "UI", "Система единиц: ${system.displayName}"),
      )
    }
  }

  fun setAutoRefreshEnabled(enabled: Boolean) {
    _uiState.update { state ->
      if (state.autoRefreshEnabled == enabled) return@update state
      persistPreferences(
        state.windUnit,
        state.pressureUnit,
        enabled,
        state.autoRefreshIntervalMinutes,
      )
      val msg = if (enabled) {
        "Автообновление включено (${state.autoRefreshIntervalMinutes} мин)"
      } else {
        "Автообновление отключено"
      }
      state.copy(
        autoRefreshEnabled = enabled,
        logs = appendLog(state.logs, "NET", msg),
      )
    }
    scheduleAutoRefreshLoop()
  }

  fun setAutoRefreshInterval(minutes: Int) {
    val valid = AutoRefreshInterval.fromMinutes(minutes).minutes
    _uiState.update { state ->
      if (state.autoRefreshIntervalMinutes == valid) return@update state
      persistPreferences(
        state.windUnit,
        state.pressureUnit,
        state.autoRefreshEnabled,
        valid,
      )
      state.copy(
        autoRefreshIntervalMinutes = valid,
        logs = appendLog(state.logs, "NET", "Интервал автообновления: $valid мин"),
      )
    }
    scheduleAutoRefreshLoop()
  }

  fun toggleFavorite(icao: String = _uiState.value.station.icao) {
    val clean = icao.trim().uppercase()
    if (clean.length != 4) return
    _uiState.update { state ->
      val updated = if (clean in state.favoriteIcaos) {
        state.favoriteIcaos.filterNot { it == clean }
      } else {
        (state.favoriteIcaos + clean).distinct()
      }
      favoriteStorage.saveFavorites(updated)
      weatherNotifier.syncBackgroundSchedule(
        enabled = state.alertConfig.enabled,
        intervalMinutes = state.alertConfig.checkIntervalMinutes,
        hasFavorites = updated.isNotEmpty(),
      )
      val msg = if (clean in updated) "Добавлено в избранное: $clean" else "Удалено из избранного: $clean"
      state.copy(
        favoriteIcaos = updated,
        mapStations = buildMapStationList(state.station, updated),
        logs = appendLog(state.logs, "INFO", msg),
      )
    }
  }

  fun addFavorite(icao: String, selectAfterAdd: Boolean = false) {
    val clean = icao.trim().uppercase()
    if (clean.length != 4 || !clean.all { it.isLetter() }) return
    _uiState.update { state ->
      val updated = (state.favoriteIcaos + clean).distinct()
      favoriteStorage.saveFavorites(updated)
      weatherNotifier.syncBackgroundSchedule(
        enabled = state.alertConfig.enabled,
        intervalMinutes = state.alertConfig.checkIntervalMinutes,
        hasFavorites = updated.isNotEmpty(),
      )
      state.copy(
        favoriteIcaos = updated,
        mapStations = buildMapStationList(state.station, updated),
        logs = appendLog(state.logs, "INFO", "Аэропорт $clean сохранён в избранное"),
      )
    }
    if (selectAfterAdd) {
      selectStation(clean)
    }
  }

  fun removeFavorite(icao: String) {
    val clean = icao.trim().uppercase()
    _uiState.update { state ->
      val updated = state.favoriteIcaos.filterNot { it == clean }
      favoriteStorage.saveFavorites(updated)
      weatherNotifier.syncBackgroundSchedule(
        enabled = state.alertConfig.enabled,
        intervalMinutes = state.alertConfig.checkIntervalMinutes,
        hasFavorites = updated.isNotEmpty(),
      )
      state.copy(
        favoriteIcaos = updated,
        mapStations = buildMapStationList(state.station, updated),
        logs = appendLog(state.logs, "INFO", "Удалено из избранного: $clean"),
      )
    }
  }

  fun saveCurrentSetup(customName: String? = null) {
    _uiState.update { state ->
      val pair = state.activeRunwayPair
      val runwayLabel = "${pair.primaryDesignator}/${pair.reciprocalDesignator}"
      val id = SavedWeatherPreset.buildId(
        icao = state.station.icao,
        runwayPairIndex = state.selectedRunwayPairIndex,
        windUnit = state.windUnit,
        pressureUnit = state.pressureUnit,
      )
      val label = customName?.trim()?.takeIf { it.isNotBlank() }
        ?: SavedWeatherPreset.defaultLabel(state.station.icao, runwayLabel, state.windUnit, state.pressureUnit)
      val preset = SavedWeatherPreset(
        id = id,
        name = label,
        icao = state.station.icao,
        runwayPairIndex = state.selectedRunwayPairIndex,
        runwayLabel = runwayLabel,
        windUnit = state.windUnit,
        pressureUnit = state.pressureUnit,
        sourceMode = state.serverConfig.resolvedMode(),
      )
      val updated = (listOf(preset) + state.savedPresets.filterNot { it.id == id }).distinctBy { it.id }
      presetStorage.savePresets(updated)
      state.copy(
        savedPresets = updated,
        logs = appendLog(state.logs, "INFO", "Сохранён профиль: $label"),
      )
    }
  }

  fun applySavedPreset(presetId: String) {
    val target = _uiState.value.savedPresets.firstOrNull { it.id == presetId } ?: return
    val station = AirportCatalog.findOrDefault(target.icao)
    val safeRunwayIdx = target.runwayPairIndex.coerceIn(0, station.runways.lastIndex)
    val current = _uiState.value
    val nextServerConfig = target.sourceMode?.let { mode ->
      val cfg = current.serverConfig.withMode(mode)
      serverConfigStorage.saveConfig(cfg)
      cfg
    } ?: current.serverConfig

    val sameStation = current.station.icao.equals(station.icao, ignoreCase = true)
    val baseWeather = if (sameStation) {
      current.weather
    } else {
      repository.createEmptyWeather(
        station = station,
        sourceLabel = if (autoFetch) "загрузка с сервера..." else "ожидание обновления",
      )
    }
    val pair = station.runways[safeRunwayIdx]
    val (pCalc, rCalc) = computeRunwayCalcs(pair, baseWeather)

    val nextHistory = currentHistory24hFor(station.icao)
    _uiState.update { state ->
      state.copy(
        station = station,
        selectedRunwayPairIndex = safeRunwayIdx,
        windUnit = target.windUnit,
        pressureUnit = target.pressureUnit,
        serverConfig = nextServerConfig,
        weather = baseWeather,
        primaryRunwayCalc = pCalc,
        reciprocalRunwayCalc = rCalc,
        windTrend = currentTrendFor(station.icao),
        weatherHistory24h = nextHistory,
        qnhTrend24h = WeatherHistoryAnalyzer.buildQnhTrend(station.icao, nextHistory),
        historyStatusMessage = null,
        liveLocalClock = if (sameStation) state.liveLocalClock else baseWeather.receivedTimeLocal,
        statusBanner = null,
        logs = appendLog(state.logs, "INFO", "Загружен профиль: ${target.name}"),
      )
    }
    if (!sameStation) {
      activeFetchJob?.cancel()
    }
    syncCrashStationContext()
    if (autoFetch && !sameStation) {
      fetchLiveForStation(station)
    }
  }

  fun deleteSavedPreset(presetId: String) {
    _uiState.update { state ->
      val removed = state.savedPresets.firstOrNull { it.id == presetId }
      val updated = state.savedPresets.filterNot { it.id == presetId }
      presetStorage.savePresets(updated)
      state.copy(
        savedPresets = updated,
        logs = appendLog(state.logs, "INFO", "Удалён профиль: ${removed?.name ?: presetId}"),
      )
    }
  }

  fun setPressureUnit(unit: PressureUnit) {
    _uiState.update { state ->
      if (state.pressureUnit == unit) return@update state
      persistPreferences(state.windUnit, unit, state.autoRefreshEnabled, state.autoRefreshIntervalMinutes)
      state.copy(
        pressureUnit = unit,
        logs = appendLog(state.logs, "UI", "Единицы давления: ${unit.label}"),
      )
    }
  }

  fun setDataSourceMode(mode: DataSourceMode, fetchNow: Boolean = true) {
    val updated = _uiState.value.serverConfig.withMode(mode)
    serverConfigStorage.saveConfig(updated)
    _uiState.update { state ->
      state.copy(
        serverConfig = updated,
        logs = appendLog(state.logs, "NET", "Источник данных: ${mode.displayName}"),
      )
    }
    if (fetchNow) {
      fetchLiveForStation(_uiState.value.station)
    }
  }

  fun updateServerConfig(config: ServerConfig, fetchNow: Boolean = true) {
    val normalized = config.copy(
      url = ServerConfig.normalizeServerUrl(config.url),
      username = config.username.trim(),
    )
    serverConfigStorage.saveConfig(normalized)
    _uiState.update { state ->
      state.copy(
        serverConfig = normalized,
        logs = appendLog(state.logs, "NET", "Сохранены настройки сервера (${normalized.resolvedMode().displayName}): ${normalized.url}"),
      )
    }
    if (fetchNow) {
      fetchLiveForStation(_uiState.value.station)
    }
  }

  fun selectRunwayPair(index: Int) {
    _uiState.update { state ->
      val safeIdx = index.coerceIn(0, state.station.runways.lastIndex)
      val pair = state.station.runways[safeIdx]
      val (pCalc, rCalc) = computeRunwayCalcs(pair, state.weather)
      state.copy(
        selectedRunwayPairIndex = safeIdx,
        primaryRunwayCalc = pCalc,
        reciprocalRunwayCalc = rCalc,
        logs = appendLog(state.logs, "INFO", "Выбрана ВПП ${pair.primaryDesignator} / ${pair.reciprocalDesignator}"),
      )
    }
  }

  fun selectStation(icaoInput: String) {
    val clean = icaoInput.trim().uppercase()
    if (clean.length != 4) return
    val station = resolvedStationsByIcao[clean] ?: AirportCatalog.findOrDefault(clean)
    resolvedStationsByIcao.putIfAbsent(clean, station)
    val cachedWeather = _uiState.value.mapWeatherByIcao[clean]
    val initialStationWeather = cachedWeather ?: repository.createEmptyWeather(
      station = station,
      sourceLabel = if (autoFetch) "загрузка с сервера..." else "ожидание обновления",
    )
    val pair = station.runways.first()
    val (pCalc, rCalc) = computeRunwayCalcs(pair, initialStationWeather)
    val nextHistory = currentHistory24hFor(station.icao)
    _uiState.update { state ->
      state.copy(
        station = station,
        selectedRunwayPairIndex = 0,
        weather = initialStationWeather,
        primaryRunwayCalc = pCalc,
        reciprocalRunwayCalc = rCalc,
        windTrend = currentTrendFor(station.icao),
        weatherHistory24h = nextHistory,
        qnhTrend24h = WeatherHistoryAnalyzer.buildQnhTrend(station.icao, nextHistory),
        historyStatusMessage = null,
        mapStations = buildMapStationList(station, state.favoriteIcaos),
        liveLocalClock = initialStationWeather.receivedTimeLocal,
        statusBanner = null,
        logs = appendLog(state.logs, "INFO", "Выбран аэропорт ${station.icao} (${station.name})"),
      )
    }
    activeFetchJob?.cancel()
    historyFetchJob?.cancel()
    syncCrashStationContext()
    if (autoFetch) {
      fetchLiveForStation(station)
    }
  }

  fun refresh() {
    fetchLiveForStation(_uiState.value.station)
    scheduleAutoRefreshLoop()
  }

  fun applyCustomMetar(rawMetar: String, rawTaf: String) {
    val current = _uiState.value
    if (rawMetar.isBlank()) return
    activeFetchJob?.cancel()
    val parsed = AviationWeatherParser.parse(
      rawMetarInput = rawMetar,
      rawTafInput = rawTaf.ifBlank { current.weather.rawTaf },
      station = current.station,
      sourceLabel = "ручной ввод METAR",
    )
    val pair = current.activeRunwayPair
    val (pCalc, rCalc) = computeRunwayCalcs(pair, parsed)
    val nowLocal = AviationWeatherParser.currentLocalTimeString(current.station.utcOffsetHours)
    val nextTrend = recordWindObservation(current.station.icao, parsed)
    val nextHistory = recordWeatherHistory(current.station.icao, parsed)
    _uiState.update { state ->
      state.copy(
        weather = parsed,
        primaryRunwayCalc = pCalc,
        reciprocalRunwayCalc = rCalc,
        windTrend = nextTrend,
        weatherHistory24h = nextHistory,
        weatherHistoryByIcaoMap = snapshotAllHistory24h(),
        qnhTrend24h = WeatherHistoryAnalyzer.buildQnhTrend(current.station.icao, nextHistory),
        mapWeatherByIcao = state.mapWeatherByIcao + (current.station.icao to parsed),
        liveLocalClock = nowLocal,
        statusBanner = null,
        logs = appendLog(state.logs, "INFO", "Применён ручной METAR для ${current.station.icao}"),
      )
    }
    evaluateFavoriteObservation(current.station, parsed)
  }

  fun updateAlertConfig(config: WeatherAlertConfig) {
    val previousConfig = _uiState.value.alertConfig
    alertStorage.saveConfig(config)
    val vfrConfigChanged = config.vfrAlertsEnabled != previousConfig.vfrAlertsEnabled ||
      config.vfrMinVisibilityMeters != previousConfig.vfrMinVisibilityMeters ||
      config.vfrMinCeilingFeet != previousConfig.vfrMinCeilingFeet ||
      config.vfrMaxWindKt != previousConfig.vfrMaxWindKt ||
      config.vfrMonitorAllFavorites != previousConfig.vfrMonitorAllFavorites ||
      config.vfrMonitoredIcaos != previousConfig.vfrMonitoredIcaos

    _uiState.update { state ->
      val vfrAirports = config.resolvedVfrMonitoredIcaos(state.favoriteIcaos)
      weatherNotifier.syncBackgroundSchedule(
        enabled = config.enabled,
        intervalMinutes = config.checkIntervalMinutes,
        hasFavorites = state.favoriteIcaos.isNotEmpty() || vfrAirports.isNotEmpty(),
      )
      state.copy(
        alertConfig = config,
        logs = appendLog(
          state.logs,
          "INFO",
          if (config.enabled) "Уведомления о погоде и минимумах ПВП (VFR) обновлены"
          else "Уведомления о погоде отключены",
        ),
      )
    }
    if (vfrConfigChanged && config.enabled && config.vfrAlertsEnabled) {
      evaluateCachedAirportsAgainstVfr(config)
    }
  }

  fun toggleVfrMonitoredAirport(icaoInput: String) {
    val clean = icaoInput.trim().uppercase()
    if (clean.length != 4 || !clean.all(Char::isLetter)) return
    val current = _uiState.value
    val cfg = current.alertConfig
    val baseSet = cfg.resolvedVfrMonitoredIcaos(current.favoriteIcaos).toMutableSet()
    if (!baseSet.add(clean)) {
      baseSet.remove(clean)
    }
    updateAlertConfig(
      cfg.copy(
        vfrMonitorAllFavorites = false,
        vfrMonitoredIcaos = baseSet.toList(),
      ),
    )
  }

  fun setVfrMonitorAllFavorites(useAll: Boolean) {
    val cfg = _uiState.value.alertConfig
    updateAlertConfig(cfg.copy(vfrMonitorAllFavorites = useAll))
  }

  fun checkVfrMinimumsNow() {
    val state = _uiState.value
    evaluateCachedAirportsAgainstVfr(state.alertConfig)
    if (autoFetch) {
      checkFavoritesWeatherNow()
    }
  }

  private fun evaluateCachedAirportsAgainstVfr(config: WeatherAlertConfig) {
    if (!config.enabled || !config.vfrAlertsEnabled) return
    val stateSnapshot = _uiState.value
    val selectedAirports = config.resolvedVfrMonitoredIcaos(stateSnapshot.favoriteIcaos)
    if (selectedAirports.isEmpty()) return

    val newlyFiredAlerts = mutableListOf<WeatherChangeAlert>()
    selectedAirports.forEach { icao ->
      val station = resolvedStationsByIcao[icao] ?: AirportCatalog.findOrDefault(icao)
      val weather = when {
        stateSnapshot.station.icao.equals(icao, ignoreCase = true) -> stateSnapshot.weather
        else -> stateSnapshot.mapWeatherByIcao[icao]
      } ?: return@forEach

      val directAlert = WeatherChangeDetector.evaluateVfrBreachDirect(
        station = station,
        current = weather,
        config = config,
      ) ?: return@forEach

      var added = false
      _uiState.update { state ->
        if (state.weatherAlerts.any { it.id == directAlert.id }) return@update state
        added = true
        val updated = (listOf(directAlert) + state.weatherAlerts).take(40)
        alertStorage.saveAlerts(updated)
        state.copy(
          weatherAlerts = updated,
          latestWeatherAlert = directAlert,
          logs = appendLog(state.logs, "ALERT", "VFR ${directAlert.icao}: ${directAlert.summary}"),
        )
      }
      if (added) {
        newlyFiredAlerts.add(directAlert)
      }
    }
    newlyFiredAlerts.forEach { weatherNotifier.notify(it) }
  }

  fun checkFavoritesWeatherNow() {
    val stateSnapshot = _uiState.value
    val vfrSelected = stateSnapshot.alertConfig.resolvedVfrMonitoredIcaos(stateSnapshot.favoriteIcaos)
    val targets = (stateSnapshot.favoriteIcaos + vfrSelected).distinct()
    if (targets.isEmpty()) return
    evaluateCachedAirportsAgainstVfr(stateSnapshot.alertConfig)
    if (!autoFetch) return
    viewModelScope.launch {
      _uiState.update { it.copy(isCheckingFavorites = true) }
      var alertsTriggered = 0
      targets.forEach { icao ->
        val station = AirportCatalog.findOrDefault(icao)
        repository.fetchLiveWeather(station, _uiState.value.serverConfig).onSuccess { fetched ->
          resolvedStationsByIcao[fetched.resolvedStation.icao] = fetched.resolvedStation
          val favTrend = recordWindObservation(fetched.resolvedStation.icao, fetched.weather)
          val favHist = recordWeatherHistory(fetched.resolvedStation.icao, fetched.weather)
          val allHistSnapshot = snapshotAllHistory24h()
          _uiState.update { state ->
            val updatedCache = state.mapWeatherByIcao + (fetched.resolvedStation.icao to fetched.weather)
            val updatedStations = buildMapStationList(state.station, state.favoriteIcaos)
            if (state.station.icao.equals(fetched.resolvedStation.icao, ignoreCase = true)) {
              state.copy(
                windTrend = favTrend,
                weatherHistory24h = favHist,
                weatherHistoryByIcaoMap = allHistSnapshot,
                qnhTrend24h = WeatherHistoryAnalyzer.buildQnhTrend(fetched.resolvedStation.icao, favHist),
                mapWeatherByIcao = updatedCache,
                mapStations = updatedStations,
              )
            } else {
              state.copy(
                weatherHistoryByIcaoMap = allHistSnapshot,
                mapWeatherByIcao = updatedCache,
                mapStations = updatedStations,
              )
            }
          }
          val fired = evaluateFavoriteObservation(fetched.resolvedStation, fetched.weather)
          if (fired) alertsTriggered++
        }.onFailure { err ->
          CrashReporter.recordNonFatal(
            tag = "favorite_weather_check",
            throwable = err,
            extras = mapOf("icao" to icao),
          )
        }
      }
      _uiState.update { state ->
        val summaryMsg = if (alertsTriggered > 0) {
          "Проверка станций (${targets.size}): обнаружено предупреждений — $alertsTriggered"
        } else {
          "Проверка станций (${targets.size}): условия в норме"
        }
        state.copy(
          isCheckingFavorites = false,
          logs = appendLog(state.logs, "INFO", summaryMsg),
        )
      }
    }
  }

  fun markAllAlertsRead() {
    _uiState.update { state ->
      val updated = state.weatherAlerts.map { it.copy(isRead = true) }
      alertStorage.saveAlerts(updated)
      state.copy(
        weatherAlerts = updated,
        latestWeatherAlert = null,
      )
    }
  }

  fun clearWeatherAlerts() {
    _uiState.update { state ->
      alertStorage.saveAlerts(emptyList())
      state.copy(
        weatherAlerts = emptyList(),
        latestWeatherAlert = null,
        logs = appendLog(state.logs, "INFO", "История метеооповещений очищена"),
      )
    }
  }

  fun dismissLatestWeatherAlert() {
    _uiState.update { it.copy(latestWeatherAlert = null) }
  }

  fun openAlertStation(icao: String) {
    val clean = icao.trim().uppercase()
    _uiState.update { state ->
      val updated = state.weatherAlerts.map { alert ->
        if (alert.icao == clean) alert.copy(isRead = true) else alert
      }
      alertStorage.saveAlerts(updated)
      state.copy(
        weatherAlerts = updated,
        latestWeatherAlert = if (state.latestWeatherAlert?.icao == clean) null else state.latestWeatherAlert,
      )
    }
    selectStation(clean)
  }

  fun dismissBanner() {
    _uiState.update { it.copy(statusBanner = null) }
  }

  private fun evaluateFavoriteObservation(
    station: AirportStation,
    weather: DecodedWeather,
  ): Boolean {
    val cleanIcao = station.icao.trim().uppercase()
    val current = _uiState.value
    val vfrMonitored = current.alertConfig.resolvedVfrMonitoredIcaos(current.favoriteIcaos)
    val isFav = cleanIcao in current.favoriteIcaos
    val isVfr = cleanIcao in vfrMonitored
    if (!isFav && !isVfr) return false

    val result = WeatherChangeDetector.evaluate(
      station = station,
      previous = weatherSnapshots[cleanIcao],
      current = weather,
      config = current.alertConfig,
      isVfrMonitored = isVfr,
    )
    result.snapshot?.let { snap ->
      weatherSnapshots[cleanIcao] = snap
      alertStorage.saveSnapshots(weatherSnapshots.toMap())
    }
    val alert = result.alert ?: return false
    var recorded = false
    _uiState.update { state ->
      if (state.weatherAlerts.any { it.id == alert.id }) return@update state
      recorded = true
      val updatedAlerts = (listOf(alert) + state.weatherAlerts).take(40)
      alertStorage.saveAlerts(updatedAlerts)
      state.copy(
        weatherAlerts = updatedAlerts,
        latestWeatherAlert = alert,
        logs = appendLog(state.logs, "ALERT", "${alert.icao}: ${alert.summary}"),
      )
    }
    if (recorded) {
      weatherNotifier.notify(alert)
    }
    return recorded
  }

  private fun fetchLiveForStation(station: AirportStation) {
    activeFetchJob?.cancel()
    activeFetchJob = viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true, statusBanner = null) }
      val result = repository.fetchLiveWeather(station, _uiState.value.serverConfig)
      result.onSuccess { fetched ->
        val resolved = fetched.resolvedStation
        resolvedStationsByIcao[resolved.icao] = resolved
        val pair = resolved.runways.getOrElse(_uiState.value.selectedRunwayPairIndex) { resolved.runways.first() }
        val (pCalc, rCalc) = computeRunwayCalcs(pair, fetched.weather)
        val nowLocal = AviationWeatherParser.currentLocalTimeString(resolved.utcOffsetHours)
        val nextTrend = recordWindObservation(resolved.icao, fetched.weather)
        val nextHistory = recordWeatherHistory(resolved.icao, fetched.weather)
        _uiState.update { state ->
          var updatedLogs = state.logs
          fetched.logMessages.forEach { msg ->
            updatedLogs = appendLog(updatedLogs, "NET", msg)
          }
          state.copy(
            station = resolved,
            weather = fetched.weather,
            primaryRunwayCalc = pCalc,
            reciprocalRunwayCalc = rCalc,
            windTrend = nextTrend,
            weatherHistory24h = nextHistory,
            weatherHistoryByIcaoMap = snapshotAllHistory24h(),
            qnhTrend24h = WeatherHistoryAnalyzer.buildQnhTrend(resolved.icao, nextHistory),
            mapStations = buildMapStationList(resolved, state.favoriteIcaos),
            mapWeatherByIcao = state.mapWeatherByIcao + (resolved.icao to fetched.weather),
            isLoading = false,
            statusBanner = null,
            liveLocalClock = nowLocal,
            logs = updatedLogs,
          )
        }
        evaluateFavoriteObservation(resolved, fetched.weather)
      }.onFailure { err ->
        val msg = err.message ?: "Ошибка загрузки METAR"
        CrashReporter.recordNonFatal(
          tag = "metar_fetch",
          throwable = err,
          extras = mapOf(
            "icao" to station.icao,
            "source_mode" to _uiState.value.serverConfig.resolvedMode().name,
          ),
        )
        _uiState.update { state ->
          state.copy(
            isLoading = false,
            statusBanner = msg,
            logs = appendLog(state.logs, "ERR", msg),
          )
        }
      }
    }
  }

  private fun syncCrashStationContext() {
    val state = _uiState.value
    val pair = state.activeRunwayPair
    val runwayLabel = "${pair.primaryDesignator}/${pair.reciprocalDesignator}"
    CrashReporter.setStationContext(
      icao = state.station.icao,
      runwayLabel = runwayLabel,
      sourceMode = state.serverConfig.resolvedMode().name,
      favoriteCount = state.favoriteIcaos.size,
    )
  }

  private fun computeRunwayCalcs(
    pair: RunwayPair,
    weather: DecodedWeather,
  ): Pair<RunwayCalculation, RunwayCalculation> {
    val pCalc = AviationWeatherParser.calculateRunwayComponents(
      designator = pair.primaryDesignator,
      runwayHeadingDeg = pair.primaryHeadingDeg,
      wind = weather.wind,
      frictionCoeff = weather.frictionCoeff,
      rvrMeters = weather.rvrPrimaryMeters,
    )
    val rCalc = AviationWeatherParser.calculateRunwayComponents(
      designator = pair.reciprocalDesignator,
      runwayHeadingDeg = pair.reciprocalHeadingDeg,
      wind = weather.wind,
      frictionCoeff = weather.frictionCoeff,
      rvrMeters = weather.rvrReciprocalMeters,
    )
    return pCalc to rCalc
  }

  private fun currentTrendFor(icao: String): WindTrendSummary {
    val entries = synchronized(windHistoryByIcao) {
      windHistoryByIcao[icao.trim().uppercase()].orEmpty()
    }
    return WindTrendAnalyzer.analyze(icao, entries)
  }

  private fun recordWindObservation(
    icao: String,
    weather: DecodedWeather,
  ): WindTrendSummary {
    val clean = icao.trim().uppercase()
    val updatedEntries = synchronized(windHistoryByIcao) {
      val currentEntries = windHistoryByIcao[clean].orEmpty()
      val nextEntries = WindTrendAnalyzer.appendObservation(currentEntries, weather)
      if (nextEntries != currentEntries) {
        windHistoryByIcao[clean] = nextEntries
        windHistoryStorage.saveAll(windHistoryByIcao.toMap())
      }
      nextEntries
    }
    return WindTrendAnalyzer.analyze(clean, updatedEntries)
  }

  private fun snapshotAllHistory24h(): Map<String, List<WeatherHistoryEntry>> {
    val cutoff = System.currentTimeMillis() - WeatherHistoryAnalyzer.WINDOW_24H_MILLIS
    return synchronized(weatherHistoryByIcao) {
      weatherHistoryByIcao.mapValues { (_, list) ->
        list.filter { it.observedAtMillis >= cutoff }.sortedBy { it.observedAtMillis }
      }
    }
  }

  private fun currentHistory24hFor(icao: String): List<WeatherHistoryEntry> {
    val clean = icao.trim().uppercase()
    val cutoff = System.currentTimeMillis() - WeatherHistoryAnalyzer.WINDOW_24H_MILLIS
    return synchronized(weatherHistoryByIcao) {
      weatherHistoryByIcao[clean]
        .orEmpty()
        .filter { it.observedAtMillis >= cutoff }
        .sortedBy { it.observedAtMillis }
    }
  }

  private fun recordWeatherHistory(
    icao: String,
    weather: DecodedWeather,
  ): List<WeatherHistoryEntry> {
    val clean = icao.trim().uppercase()
    return synchronized(weatherHistoryByIcao) {
      val currentEntries = weatherHistoryByIcao[clean].orEmpty()
      val nextEntries = WeatherHistoryAnalyzer.appendObservation(currentEntries, weather)
      if (nextEntries != currentEntries) {
        weatherHistoryByIcao[clean] = nextEntries
        weatherHistoryStorage.saveAll(weatherHistoryByIcao.toMap())
      }
      nextEntries
    }
  }

  fun mergeExternal24hHistory(
    icao: String,
    entries: List<WeatherHistoryEntry>,
  ) {
    val clean = icao.trim().uppercase()
    if (clean.length != 4 || entries.isEmpty()) return
    val merged = synchronized(weatherHistoryByIcao) {
      val currentEntries = weatherHistoryByIcao[clean].orEmpty()
      val nextEntries = WeatherHistoryAnalyzer.mergeHistory(currentEntries, entries)
      weatherHistoryByIcao[clean] = nextEntries
      weatherHistoryStorage.saveAll(weatherHistoryByIcao.toMap())
      nextEntries
    }
    val allSnapshot = snapshotAllHistory24h()
    _uiState.update { state ->
      val isActive = state.station.icao.equals(clean, ignoreCase = true)
      state.copy(
        weatherHistory24h = if (isActive) merged else state.weatherHistory24h,
        weatherHistoryByIcaoMap = allSnapshot,
        qnhTrend24h = if (isActive) WeatherHistoryAnalyzer.buildQnhTrend(clean, merged) else state.qnhTrend24h,
      )
    }
  }

  fun selectD3TrendAirport(icaoInput: String, fetchIfMissing: Boolean = true) {
    val clean = icaoInput.trim().uppercase()
    if (clean.length != 4) return
    _uiState.update { it.copy(selectedD3TrendIcao = clean) }
    val existing = currentHistory24hFor(clean)
    if (fetchIfMissing && existing.size < 2) {
      load24hHistoryForIcao(clean, force = false)
    }
  }

  fun load24hHistoryForIcao(icaoInput: String, force: Boolean = false) {
    val clean = icaoInput.trim().uppercase()
    if (clean.length != 4) return
    if (!autoFetch && !force) return
    val targetStation = resolvedStationsByIcao[clean] ?: AirportCatalog.findOrDefault(clean)
    viewModelScope.launch {
      _uiState.update { it.copy(loadingHistoryIcao = clean) }
      val result = repository.fetch24hHistory(targetStation, _uiState.value.serverConfig)
      result.onSuccess { fetched ->
        val merged = synchronized(weatherHistoryByIcao) {
          val currentEntries = weatherHistoryByIcao[clean].orEmpty()
          val nextEntries = WeatherHistoryAnalyzer.mergeHistory(currentEntries, fetched.entries)
          weatherHistoryByIcao[clean] = nextEntries
          weatherHistoryStorage.saveAll(weatherHistoryByIcao.toMap())
          nextEntries
        }
        val allSnapshot = snapshotAllHistory24h()
        _uiState.update { state ->
          val isCurrent = state.station.icao.equals(clean, ignoreCase = true)
          state.copy(
            weatherHistory24h = if (isCurrent) merged else state.weatherHistory24h,
            weatherHistoryByIcaoMap = allSnapshot,
            qnhTrend24h = if (isCurrent) WeatherHistoryAnalyzer.buildQnhTrend(clean, merged) else state.qnhTrend24h,
            loadingHistoryIcao = if (state.loadingHistoryIcao == clean) null else state.loadingHistoryIcao,
            logs = appendLog(state.logs, "HIST", "D3 24ч: загружено ${merged.size} зап. для $clean"),
          )
        }
      }.onFailure { err ->
        _uiState.update { state ->
          state.copy(
            loadingHistoryIcao = if (state.loadingHistoryIcao == clean) null else state.loadingHistoryIcao,
            logs = appendLog(state.logs, "HIST", "D3 24ч ($clean): ${err.message ?: "нет данных"}"),
          )
        }
      }
    }
  }

  fun load24hHistory(force: Boolean = false) {
    val station = _uiState.value.station
    if (!autoFetch && !force) return
    historyFetchJob?.cancel()
    historyFetchJob = viewModelScope.launch {
      _uiState.update { it.copy(isLoadingHistory = true, historyStatusMessage = null) }
      val result = repository.fetch24hHistory(station, _uiState.value.serverConfig)
      result.onSuccess { fetched ->
        val merged = synchronized(weatherHistoryByIcao) {
          val currentEntries = weatherHistoryByIcao[station.icao].orEmpty()
          val nextEntries = WeatherHistoryAnalyzer.mergeHistory(currentEntries, fetched.entries)
          weatherHistoryByIcao[station.icao] = nextEntries
          weatherHistoryStorage.saveAll(weatherHistoryByIcao.toMap())
          nextEntries
        }
        val allSnapshot = snapshotAllHistory24h()
        _uiState.update { state ->
          var updatedLogs = state.logs
          fetched.logMessages.forEach { msg ->
            updatedLogs = appendLog(updatedLogs, "HIST", msg)
          }
          if (state.station.icao.equals(station.icao, ignoreCase = true)) {
            state.copy(
              weatherHistory24h = merged,
              weatherHistoryByIcaoMap = allSnapshot,
              qnhTrend24h = WeatherHistoryAnalyzer.buildQnhTrend(station.icao, merged),
              isLoadingHistory = false,
              historyStatusMessage = "Загружено записей за 24ч: ${merged.size}",
              logs = updatedLogs,
            )
          } else {
            state.copy(
              weatherHistoryByIcaoMap = allSnapshot,
              isLoadingHistory = false,
              logs = updatedLogs,
            )
          }
        }
      }.onFailure { err ->
        val localCount = currentHistory24hFor(station.icao).size
        _uiState.update { state ->
          state.copy(
            isLoadingHistory = false,
            historyStatusMessage = if (localCount > 0) {
              "Показан локальный кэш ($localCount зап.): ${err.message ?: "сервер недоступен"}"
            } else {
              err.message ?: "Нет данных истории за 24ч"
            },
            logs = appendLog(state.logs, "HIST", err.message ?: "Ошибка загрузки 24ч истории"),
          )
        }
      }
    }
  }

  fun fetchStationWeatherForMap(icaoInput: String) {
    val clean = icaoInput.trim().uppercase()
    if (clean.length != 4 || !autoFetch) return
    val target = resolvedStationsByIcao[clean] ?: AirportCatalog.findOrDefault(clean)
    viewModelScope.launch {
      _uiState.update { it.copy(loadingMapIcao = clean) }
      repository.fetchLiveWeather(target, _uiState.value.serverConfig).onSuccess { fetched ->
        val resolved = fetched.resolvedStation
        resolvedStationsByIcao[resolved.icao] = resolved
        val nextTrend = recordWindObservation(resolved.icao, fetched.weather)
        val nextHist = recordWeatherHistory(resolved.icao, fetched.weather)
        _uiState.update { state ->
          val isCurrent = state.station.icao.equals(resolved.icao, ignoreCase = true)
          state.copy(
            station = if (isCurrent) resolved else state.station,
            weather = if (isCurrent) fetched.weather else state.weather,
            windTrend = if (isCurrent) nextTrend else state.windTrend,
            weatherHistory24h = if (isCurrent) nextHist else state.weatherHistory24h,
            qnhTrend24h = if (isCurrent) WeatherHistoryAnalyzer.buildQnhTrend(resolved.icao, nextHist) else state.qnhTrend24h,
            mapStations = buildMapStationList(if (isCurrent) resolved else state.station, state.favoriteIcaos),
            mapWeatherByIcao = state.mapWeatherByIcao + (resolved.icao to fetched.weather),
            loadingMapIcao = if (state.loadingMapIcao == clean) null else state.loadingMapIcao,
          )
        }
        evaluateFavoriteObservation(resolved, fetched.weather)
      }.onFailure { err ->
        CrashReporter.recordNonFatal(
          tag = "map_station_preview_fetch",
          throwable = err,
          extras = mapOf("icao" to clean),
        )
        _uiState.update { state ->
          state.copy(
            loadingMapIcao = if (state.loadingMapIcao == clean) null else state.loadingMapIcao,
            logs = appendLog(state.logs, "MAP", "Нет ответа METAR для $clean на карте"),
          )
        }
      }
    }
  }

  fun refreshMapWeather() {
    if (!autoFetch) return
    val stations = _uiState.value.mapStations
    if (stations.isEmpty()) return
    viewModelScope.launch {
      _uiState.update { it.copy(isRefreshingMap = true) }
      val serverCfg = _uiState.value.serverConfig
      val results = coroutineScope {
        stations.map { st ->
          async {
            st.icao to repository.fetchLiveWeather(st, serverCfg)
          }
        }.awaitAll()
      }
      var loadedCount = 0
      val updatedWeather = _uiState.value.mapWeatherByIcao.toMutableMap()
      results.forEach { (icao, res) ->
        res.onSuccess { fetched ->
          loadedCount++
          resolvedStationsByIcao[icao] = fetched.resolvedStation
          updatedWeather[icao] = fetched.weather
          recordWindObservation(icao, fetched.weather)
          recordWeatherHistory(icao, fetched.weather)
          evaluateFavoriteObservation(fetched.resolvedStation, fetched.weather)
        }
      }
      _uiState.update { state ->
        state.copy(
          isRefreshingMap = false,
          mapStations = buildMapStationList(state.station, state.favoriteIcaos),
          mapWeatherByIcao = updatedWeather.toMap(),
          logs = appendLog(state.logs, "MAP", "Карта: загружена погода для $loadedCount из ${stations.size} аэродромов"),
        )
      }
    }
  }

  private fun buildMapStationList(
    activeStation: AirportStation,
    favoriteIcaos: List<String>,
  ): List<AirportStation> {
    val combined = LinkedHashMap<String, AirportStation>()
    AirportCatalog.presets.forEach { preset ->
      combined[preset.icao] = resolvedStationsByIcao[preset.icao] ?: preset
    }
    favoriteIcaos.forEach { raw ->
      val code = raw.trim().uppercase()
      if (code.length == 4 && code !in combined) {
        combined[code] = resolvedStationsByIcao[code] ?: AirportCatalog.findOrDefault(code)
      }
    }
    combined[activeStation.icao] = resolvedStationsByIcao[activeStation.icao] ?: activeStation
    return combined.values.toList()
  }

  private fun appendLog(
    existing: List<LogEntry>,
    level: String,
    message: String,
  ): List<LogEntry> {
    val ts = AviationWeatherParser.currentLocalTimeString(_uiState.value.station.utcOffsetHours)
    return (listOf(LogEntry(ts, level, message)) + existing).take(50)
  }
}
