package com.example.windows

import com.example.data.AirportCatalog
import com.example.data.AutoRefreshInterval
import com.example.data.AviationWeatherRepository
import com.example.data.CustomMeteoServerClient
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
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

data class DesktopUiState(
  val station: AirportStation,
  val selectedRunwayPairIndex: Int,
  val weather: DecodedWeather,
  val primaryRunwayCalc: RunwayCalculation,
  val reciprocalRunwayCalc: RunwayCalculation,
  val isNightMode: Boolean = false,
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
  val detectedLocationSummary: String? = null,
  val isCheckingFavorites: Boolean = false,
  val isLoading: Boolean = false,
  val isRefreshingMap: Boolean = false,
  val loadingMapIcao: String? = null,
  val mapStations: List<AirportStation> = AirportCatalog.presets,
  val mapWeatherByIcao: Map<String, DecodedWeather> = emptyMap(),
  val statusBanner: String? = null,
  val liveLocalClock: String = "--:--",
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

  val palette: DesktopPalette
    get() = if (isNightMode) NightDesktopPalette else DayDesktopPalette
}

class DesktopController(
  private val favoriteStorage: FavoriteAirportsStorage = InMemoryFavoriteStorage(),
  private val serverConfigStorage: ServerConfigStorage = InMemoryServerConfigStorage(),
  private val presetStorage: WeatherPresetStorage = InMemoryWeatherPresetStorage(),
  private val alertStorage: WeatherAlertStorage = InMemoryWeatherAlertStorage(),
  private val windHistoryStorage: WindHistoryStorage = InMemoryWindHistoryStorage(),
  private val weatherNotifier: DesktopWeatherNotifier = NoOpDesktopWeatherNotifier,
  private val autoFetch: Boolean = true,
  private val unitPreferencesStorage: UnitPreferencesStorage = InMemoryUnitPreferencesStorage(),
  private val weatherHistoryStorage: WeatherHistoryStorage = InMemoryWeatherHistoryStorage(),
) {
  private val repository = AviationWeatherRepository()
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val scheduler = Executors.newSingleThreadScheduledExecutor { r ->
    Thread(r, "MeteotabloClock").apply { isDaemon = true }
  }
  private val listeners = CopyOnWriteArrayList<(DesktopUiState) -> Unit>()
  private val weatherSnapshots: MutableMap<String, FavoriteWeatherSnapshot> =
    alertStorage.loadSnapshots().toMutableMap()
  private val windHistoryByIcao: MutableMap<String, List<WindHistoryEntry>> =
    windHistoryStorage.loadAll().toMutableMap()
  private val weatherHistoryByIcao: MutableMap<String, List<WeatherHistoryEntry>> =
    weatherHistoryStorage.loadAll().toMutableMap()
  private val resolvedStationsByIcao: MutableMap<String, AirportStation> =
    AirportCatalog.presets.associateBy { it.icao }.toMutableMap()
  private var autoRefreshFuture: ScheduledFuture<*>? = null

  @Volatile
  var state: DesktopUiState
    private set

  init {
    val defaultStation = AirportCatalog.findOrDefault("UTAM")
    val initialWeather = repository.createEmptyWeather(
      station = defaultStation,
      sourceLabel = if (autoFetch) "загрузка с сервера..." else "ожидание обновления",
    )
    val pair = defaultStation.runways.first()
    val (pCalc, rCalc) = computeRunwayCalcs(pair, initialWeather)
    val initialFavorites = favoriteStorage.loadFavorites()
    val initialUnitPrefs = unitPreferencesStorage.loadPreferences()
    val initialHistory24h = currentHistory24hFor(defaultStation.icao)
    val initialAllHist = snapshotAllHistory24h()
    val initialD3Icao = initialFavorites.firstOrNull() ?: defaultStation.icao

    state = DesktopUiState(
      station = defaultStation,
      selectedRunwayPairIndex = 0,
      weather = initialWeather,
      primaryRunwayCalc = pCalc,
      reciprocalRunwayCalc = rCalc,
      isNightMode = initialUnitPrefs.isNightMode,
      windUnit = initialUnitPrefs.windUnit,
      pressureUnit = initialUnitPrefs.pressureUnit,
      autoRefreshEnabled = initialUnitPrefs.autoRefreshEnabled,
      autoRefreshIntervalMinutes = AutoRefreshInterval.fromMinutes(initialUnitPrefs.autoRefreshIntervalMinutes).minutes,
      favoriteIcaos = initialFavorites,
      savedPresets = presetStorage.loadPresets(),
      serverConfig = serverConfigStorage.loadConfig(),
      alertConfig = alertStorage.loadConfig(),
      weatherAlerts = alertStorage.loadAlerts(),
      windTrend = currentTrendFor(defaultStation.icao),
      weatherHistory24h = initialHistory24h,
      weatherHistoryByIcaoMap = initialAllHist,
      selectedD3TrendIcao = initialD3Icao,
      qnhTrend24h = WeatherHistoryAnalyzer.buildQnhTrend(defaultStation.icao, initialHistory24h),
      mapStations = buildMapStationList(defaultStation, initialFavorites),
      liveLocalClock = initialWeather.receivedTimeLocal,
      logs = listOf(
        LogEntry(
          timestamp = initialWeather.receivedTimeLocal,
          level = "INFO",
          message = "Инициализация станции ${defaultStation.icao} (Windows Desktop)",
        ),
      ),
    )

    scheduler.scheduleAtFixedRate(
      {
        val nowLocal = AviationWeatherParser.currentLocalTimeString(state.station.utcOffsetHours)
        updateState { it.copy(liveLocalClock = nowLocal) }
      },
      15,
      15,
      TimeUnit.SECONDS,
    )

    if (autoFetch) {
      rescheduleAutoRefreshLoop()
      fetchLiveForStation(defaultStation)
    }
  }

  private fun rescheduleAutoRefreshLoop() {
    autoRefreshFuture?.cancel(false)
    autoRefreshFuture = null
    if (!autoFetch || !state.autoRefreshEnabled) return
    val intervalSec = AutoRefreshInterval.fromMinutes(state.autoRefreshIntervalMinutes).minutes * 60L
    autoRefreshFuture = scheduler.scheduleAtFixedRate(
      {
        if (state.autoRefreshEnabled) {
          fetchLiveForStation(state.station)
          if (state.alertConfig.enabled &&
            (state.favoriteIcaos.isNotEmpty() || state.alertConfig.resolvedVfrMonitoredIcaos(state.favoriteIcaos).isNotEmpty())
          ) {
            checkFavoritesWeatherNow()
          }
        }
      },
      intervalSec,
      intervalSec,
      TimeUnit.SECONDS,
    )
  }

  fun addListener(listener: (DesktopUiState) -> Unit) {
    listeners.add(listener)
    listener(state)
  }

  fun removeListener(listener: (DesktopUiState) -> Unit) {
    listeners.remove(listener)
  }

  fun shutdown() {
    autoRefreshFuture?.cancel(true)
    scheduler.shutdownNow()
    weatherNotifier.dispose()
  }

  private fun persistUnitPreferences(
    windUnit: WindSpeedUnit,
    pressureUnit: PressureUnit,
    autoRefreshEnabled: Boolean,
    autoRefreshIntervalMinutes: Int,
    isNightMode: Boolean,
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

  fun toggleNightMode() {
    setNightMode(!state.isNightMode)
  }

  fun setNightMode(nightMode: Boolean) {
    updateState { cur ->
      if (cur.isNightMode == nightMode) return@updateState cur
      persistUnitPreferences(
        cur.windUnit,
        cur.pressureUnit,
        cur.autoRefreshEnabled,
        cur.autoRefreshIntervalMinutes,
        nightMode,
      )
      cur.copy(
        isNightMode = nightMode,
        logs = appendLog(
          cur.logs,
          "UI",
          if (nightMode) "Включен ночной кокпит (Dark)" else "Включен дневной кокпит (Light)",
          cur.station,
        ),
      )
    }
  }

  fun toggleWindUnit() {
    updateState { cur ->
      val next = UnitConverter.cycleWindUnit(cur.windUnit)
      persistUnitPreferences(next, cur.pressureUnit, cur.autoRefreshEnabled, cur.autoRefreshIntervalMinutes, cur.isNightMode)
      cur.copy(
        windUnit = next,
        logs = appendLog(cur.logs, "UI", "Единицы ветра: ${next.label}", cur.station),
      )
    }
  }

  fun setWindUnit(unit: WindSpeedUnit) {
    updateState { cur ->
      if (cur.windUnit == unit) return@updateState cur
      persistUnitPreferences(unit, cur.pressureUnit, cur.autoRefreshEnabled, cur.autoRefreshIntervalMinutes, cur.isNightMode)
      cur.copy(
        windUnit = unit,
        logs = appendLog(cur.logs, "UI", "Единицы ветра: ${unit.label}", cur.station),
      )
    }
  }

  fun togglePressureUnit() {
    updateState { cur ->
      val next = UnitConverter.cyclePressureUnit(cur.pressureUnit)
      persistUnitPreferences(cur.windUnit, next, cur.autoRefreshEnabled, cur.autoRefreshIntervalMinutes, cur.isNightMode)
      cur.copy(
        pressureUnit = next,
        logs = appendLog(cur.logs, "UI", "Единицы давления: ${next.label}", cur.station),
      )
    }
  }

  fun setPressureUnit(unit: PressureUnit) {
    updateState { cur ->
      if (cur.pressureUnit == unit) return@updateState cur
      persistUnitPreferences(cur.windUnit, unit, cur.autoRefreshEnabled, cur.autoRefreshIntervalMinutes, cur.isNightMode)
      cur.copy(
        pressureUnit = unit,
        logs = appendLog(cur.logs, "UI", "Единицы давления: ${unit.label}", cur.station),
      )
    }
  }

  fun applyUnitSystem(system: UnitSystem) {
    updateState { cur ->
      if (cur.windUnit == system.windUnit && cur.pressureUnit == system.pressureUnit) {
        return@updateState cur
      }
      persistUnitPreferences(
        system.windUnit,
        system.pressureUnit,
        cur.autoRefreshEnabled,
        cur.autoRefreshIntervalMinutes,
        cur.isNightMode,
      )
      cur.copy(
        windUnit = system.windUnit,
        pressureUnit = system.pressureUnit,
        logs = appendLog(cur.logs, "UI", "Система единиц: ${system.displayName}", cur.station),
      )
    }
  }

  fun setAutoRefreshEnabled(enabled: Boolean) {
    updateState { cur ->
      if (cur.autoRefreshEnabled == enabled) return@updateState cur
      persistUnitPreferences(cur.windUnit, cur.pressureUnit, enabled, cur.autoRefreshIntervalMinutes, cur.isNightMode)
      val msg = if (enabled) {
        "Автообновление включено (${cur.autoRefreshIntervalMinutes} мин)"
      } else {
        "Автообновление отключено"
      }
      cur.copy(
        autoRefreshEnabled = enabled,
        logs = appendLog(cur.logs, "NET", msg, cur.station),
      )
    }
    rescheduleAutoRefreshLoop()
  }

  fun setAutoRefreshInterval(minutes: Int) {
    val valid = AutoRefreshInterval.fromMinutes(minutes).minutes
    updateState { cur ->
      if (cur.autoRefreshIntervalMinutes == valid) return@updateState cur
      persistUnitPreferences(cur.windUnit, cur.pressureUnit, cur.autoRefreshEnabled, valid, cur.isNightMode)
      cur.copy(
        autoRefreshIntervalMinutes = valid,
        logs = appendLog(cur.logs, "NET", "Интервал автообновления: $valid мин", cur.station),
      )
    }
    rescheduleAutoRefreshLoop()
  }

  fun selectRunwayPair(index: Int) {
    updateState { cur ->
      val safeIdx = index.coerceIn(0, cur.station.runways.lastIndex)
      val pair = cur.station.runways[safeIdx]
      val (pCalc, rCalc) = computeRunwayCalcs(pair, cur.weather)
      cur.copy(
        selectedRunwayPairIndex = safeIdx,
        primaryRunwayCalc = pCalc,
        reciprocalRunwayCalc = rCalc,
        logs = appendLog(cur.logs, "INFO", "Выбрана ВПП ${pair.primaryDesignator} / ${pair.reciprocalDesignator}", cur.station),
      )
    }
  }

  fun selectStation(icaoInput: String) {
    val clean = icaoInput.trim().uppercase()
    if (clean.length != 4) return
    val station = resolvedStationsByIcao[clean] ?: AirportCatalog.findOrDefault(clean)
    resolvedStationsByIcao.putIfAbsent(clean, station)
    val cachedWeather = state.mapWeatherByIcao[station.icao]
    val emptyWeather = cachedWeather ?: repository.createEmptyWeather(
      station = station,
      sourceLabel = if (autoFetch) "загрузка с сервера..." else "ожидание обновления",
    )
    val pair = station.runways.first()
    val (pCalc, rCalc) = computeRunwayCalcs(pair, emptyWeather)
    val nextHistory = currentHistory24hFor(station.icao)
    updateState { cur ->
      cur.copy(
        station = station,
        selectedRunwayPairIndex = 0,
        weather = emptyWeather,
        primaryRunwayCalc = pCalc,
        reciprocalRunwayCalc = rCalc,
        windTrend = currentTrendFor(station.icao),
        weatherHistory24h = nextHistory,
        qnhTrend24h = WeatherHistoryAnalyzer.buildQnhTrend(station.icao, nextHistory),
        historyStatusMessage = null,
        mapStations = buildMapStationList(station, cur.favoriteIcaos),
        liveLocalClock = AviationWeatherParser.currentLocalTimeString(station.utcOffsetHours),
        statusBanner = null,
        logs = appendLog(cur.logs, "INFO", "Выбран аэропорт ${station.icao} (${station.name})", station),
      )
    }
    if (autoFetch) {
      fetchLiveForStation(station)
    }
  }

  fun refresh() {
    fetchLiveForStation(state.station)
  }

  fun fetchWeatherByCoordinates(
    latitude: Double,
    longitude: Double,
    locationLabel: String? = null,
  ) {
    if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
      updateState { cur ->
        cur.copy(
          statusBanner = "Некорректные координаты (широта -90..90, долгота -180..180)",
          logs = appendLog(cur.logs, "ERR", "Некорректные координаты: $latitude, $longitude", cur.station),
        )
      }
      return
    }
    scope.launch {
      val coordText = String.format(Locale.US, "%.4f, %.4f", latitude, longitude)
      val summary = locationLabel?.takeIf { it.isNotBlank() }?.let { "$it ($coordText)" } ?: coordText
      updateState { it.copy(isLoading = true, statusBanner = null, detectedLocationSummary = summary) }
      val result = repository.fetchWeatherByCoordinates(
        latitude = latitude,
        longitude = longitude,
        locationLabel = locationLabel,
        serverConfig = state.serverConfig,
      )
      result.onSuccess { fetched ->
        val resolved = fetched.resolvedStation
        resolvedStationsByIcao[resolved.icao] = resolved
        val nextTrend = recordWindObservation(resolved.icao, fetched.weather)
        val nextHist = recordWeatherHistory(resolved.icao, fetched.weather)
        val allHist = snapshotAllHistory24h()
        updateState { cur ->
          val pair = resolved.runways.first()
          val (pCalc, rCalc) = computeRunwayCalcs(pair, fetched.weather)
          val nowLocal = AviationWeatherParser.currentLocalTimeString(resolved.utcOffsetHours)
          var updatedLogs = cur.logs
          fetched.logMessages.forEach { msg ->
            updatedLogs = appendLog(updatedLogs, "GEO", msg, resolved)
          }
          cur.copy(
            station = resolved,
            selectedRunwayPairIndex = 0,
            weather = fetched.weather,
            primaryRunwayCalc = pCalc,
            reciprocalRunwayCalc = rCalc,
            windTrend = nextTrend,
            weatherHistory24h = nextHist,
            weatherHistoryByIcaoMap = allHist,
            selectedD3TrendIcao = resolved.icao,
            qnhTrend24h = WeatherHistoryAnalyzer.buildQnhTrend(resolved.icao, nextHist),
            mapStations = buildMapStationList(resolved, cur.favoriteIcaos),
            mapWeatherByIcao = cur.mapWeatherByIcao + (resolved.icao to fetched.weather),
            isLoading = false,
            statusBanner = null,
            liveLocalClock = nowLocal,
            logs = updatedLogs,
          )
        }
        evaluateFavoriteObservation(resolved, fetched.weather)
      }.onFailure { err ->
        val msg = err.message ?: "Ошибка загрузки погоды по координатам"
        updateState { cur ->
          cur.copy(
            isLoading = false,
            statusBanner = msg,
            logs = appendLog(cur.logs, "ERR", msg, cur.station),
          )
        }
      }
    }
  }

  fun toggleFavorite(icao: String = state.station.icao) {
    val clean = icao.trim().uppercase()
    if (clean.length != 4) return
    updateState { cur ->
      val updated = if (clean in cur.favoriteIcaos) {
        cur.favoriteIcaos.filterNot { it == clean }
      } else {
        (cur.favoriteIcaos + clean).distinct()
      }
      favoriteStorage.saveFavorites(updated)
      val msg = if (clean in updated) "Добавлено в избранное: $clean" else "Удалено из избранного: $clean"
      cur.copy(
        favoriteIcaos = updated,
        mapStations = buildMapStationList(cur.station, updated),
        logs = appendLog(cur.logs, "INFO", msg, cur.station),
      )
    }
  }

  fun addFavorite(icao: String, selectAfterAdd: Boolean = false) {
    val clean = icao.trim().uppercase()
    if (clean.length != 4 || !clean.all { it.isLetter() }) return
    updateState { cur ->
      val updated = (cur.favoriteIcaos + clean).distinct()
      favoriteStorage.saveFavorites(updated)
      cur.copy(
        favoriteIcaos = updated,
        mapStations = buildMapStationList(cur.station, updated),
        logs = appendLog(cur.logs, "INFO", "Аэропорт $clean сохранён в избранное", cur.station),
      )
    }
    if (selectAfterAdd) {
      selectStation(clean)
    }
  }

  fun removeFavorite(icao: String) {
    val clean = icao.trim().uppercase()
    updateState { cur ->
      val updated = cur.favoriteIcaos.filterNot { it == clean }
      favoriteStorage.saveFavorites(updated)
      cur.copy(
        favoriteIcaos = updated,
        mapStations = buildMapStationList(cur.station, updated),
        logs = appendLog(cur.logs, "INFO", "Удалено из избранного: $clean", cur.station),
      )
    }
  }

  fun saveCurrentSetup(customName: String? = null) {
    updateState { cur ->
      val pair = cur.activeRunwayPair
      val runwayLabel = "${pair.primaryDesignator}/${pair.reciprocalDesignator}"
      val id = SavedWeatherPreset.buildId(
        icao = cur.station.icao,
        runwayPairIndex = cur.selectedRunwayPairIndex,
        windUnit = cur.windUnit,
        pressureUnit = cur.pressureUnit,
      )
      val label = customName?.trim()?.takeIf { it.isNotBlank() }
        ?: SavedWeatherPreset.defaultLabel(cur.station.icao, runwayLabel, cur.windUnit, cur.pressureUnit)
      val preset = SavedWeatherPreset(
        id = id,
        name = label,
        icao = cur.station.icao,
        runwayPairIndex = cur.selectedRunwayPairIndex,
        runwayLabel = runwayLabel,
        windUnit = cur.windUnit,
        pressureUnit = cur.pressureUnit,
        sourceMode = cur.serverConfig.resolvedMode(),
      )
      val updated = (listOf(preset) + cur.savedPresets.filterNot { it.id == id }).distinctBy { it.id }
      presetStorage.savePresets(updated)
      cur.copy(
        savedPresets = updated,
        logs = appendLog(cur.logs, "INFO", "Сохранён профиль: $label", cur.station),
      )
    }
  }

  fun applySavedPreset(presetId: String) {
    val target = state.savedPresets.firstOrNull { it.id == presetId } ?: return
    val station = AirportCatalog.findOrDefault(target.icao)
    val safeRunwayIdx = target.runwayPairIndex.coerceIn(0, station.runways.lastIndex)
    val sameStation = state.station.icao.equals(station.icao, ignoreCase = true)
    val nextServerConfig = target.sourceMode?.let { mode ->
      val cfg = state.serverConfig.withMode(mode)
      serverConfigStorage.saveConfig(cfg)
      cfg
    } ?: state.serverConfig
    val baseWeather = if (sameStation) {
      state.weather
    } else {
      repository.createEmptyWeather(
        station = station,
        sourceLabel = if (autoFetch) "загрузка с сервера..." else "ожидание обновления",
      )
    }
    val pair = station.runways[safeRunwayIdx]
    val (pCalc, rCalc) = computeRunwayCalcs(pair, baseWeather)
    val nextHistory = currentHistory24hFor(station.icao)

    updateState { cur ->
      cur.copy(
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
        liveLocalClock = if (sameStation) cur.liveLocalClock else baseWeather.receivedTimeLocal,
        statusBanner = null,
        logs = appendLog(cur.logs, "INFO", "Загружен профиль: ${target.name}", station),
      )
    }
    if (autoFetch && !sameStation) {
      fetchLiveForStation(station)
    }
  }

  fun deleteSavedPreset(presetId: String) {
    updateState { cur ->
      val removed = cur.savedPresets.firstOrNull { it.id == presetId }
      val updated = cur.savedPresets.filterNot { it.id == presetId }
      presetStorage.savePresets(updated)
      cur.copy(
        savedPresets = updated,
        logs = appendLog(cur.logs, "INFO", "Удалён профиль: ${removed?.name ?: presetId}", cur.station),
      )
    }
  }

  fun applyCustomMetar(rawMetar: String, rawTaf: String) {
    val cur = state
    if (rawMetar.isBlank()) return
    val parsed = AviationWeatherParser.parse(
      rawMetarInput = rawMetar,
      rawTafInput = rawTaf.ifBlank { cur.weather.rawTaf },
      station = cur.station,
      sourceLabel = "ручной ввод METAR",
    )
    val (pCalc, rCalc) = computeRunwayCalcs(cur.activeRunwayPair, parsed)
    val nowLocal = AviationWeatherParser.currentLocalTimeString(cur.station.utcOffsetHours)
    val nextTrend = recordWindObservation(cur.station.icao, parsed)
    val nextHist = recordWeatherHistory(cur.station.icao, parsed)
    val allHist = snapshotAllHistory24h()
    updateState { s ->
      s.copy(
        weather = parsed,
        primaryRunwayCalc = pCalc,
        reciprocalRunwayCalc = rCalc,
        windTrend = nextTrend,
        weatherHistory24h = nextHist,
        weatherHistoryByIcaoMap = allHist,
        qnhTrend24h = WeatherHistoryAnalyzer.buildQnhTrend(cur.station.icao, nextHist),
        mapStations = buildMapStationList(s.station, s.favoriteIcaos),
        mapWeatherByIcao = s.mapWeatherByIcao + (s.station.icao to parsed),
        liveLocalClock = nowLocal,
        statusBanner = null,
        logs = appendLog(s.logs, "INFO", "Применён ручной METAR для ${cur.station.icao}", cur.station),
      )
    }
    evaluateFavoriteObservation(cur.station, parsed)
  }

  fun updateAlertConfig(config: WeatherAlertConfig) {
    val previousConfig = state.alertConfig
    alertStorage.saveConfig(config)
    val vfrChanged = config.vfrAlertsEnabled != previousConfig.vfrAlertsEnabled ||
      config.vfrMinVisibilityMeters != previousConfig.vfrMinVisibilityMeters ||
      config.vfrMinCeilingFeet != previousConfig.vfrMinCeilingFeet ||
      config.vfrMaxWindKt != previousConfig.vfrMaxWindKt ||
      config.vfrMonitorAllFavorites != previousConfig.vfrMonitorAllFavorites ||
      config.vfrMonitoredIcaos != previousConfig.vfrMonitoredIcaos
    updateState { cur ->
      cur.copy(
        alertConfig = config,
        logs = appendLog(
          cur.logs,
          "INFO",
          if (config.enabled) "Уведомления и пороги VFR обновлены"
          else "Уведомления о погоде отключены",
          cur.station,
        ),
      )
    }
    if (vfrChanged && config.enabled && config.vfrAlertsEnabled) {
      evaluateCachedAirportsAgainstVfr(config)
    }
  }

  fun toggleVfrMonitoredAirport(icaoInput: String) {
    val clean = icaoInput.trim().uppercase()
    if (clean.length != 4 || !clean.all(Char::isLetter)) return
    val cfg = state.alertConfig
    val baseSet = cfg.resolvedVfrMonitoredIcaos(state.favoriteIcaos).toMutableSet()
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
    updateAlertConfig(state.alertConfig.copy(vfrMonitorAllFavorites = useAll))
  }

  fun checkVfrMinimumsNow() {
    evaluateCachedAirportsAgainstVfr(state.alertConfig)
    if (autoFetch) {
      checkFavoritesWeatherNow()
    }
  }

  private fun evaluateCachedAirportsAgainstVfr(config: WeatherAlertConfig) {
    if (!config.enabled || !config.vfrAlertsEnabled) return
    val snapshot = state
    val selectedAirports = config.resolvedVfrMonitoredIcaos(snapshot.favoriteIcaos)
    if (selectedAirports.isEmpty()) return

    val fired = mutableListOf<WeatherChangeAlert>()
    selectedAirports.forEach { icao ->
      val station = resolvedStationsByIcao[icao] ?: AirportCatalog.findOrDefault(icao)
      val weather = when {
        snapshot.station.icao.equals(icao, ignoreCase = true) -> snapshot.weather
        else -> snapshot.mapWeatherByIcao[icao]
      } ?: return@forEach

      val directAlert = WeatherChangeDetector.evaluateVfrBreachDirect(
        station = station,
        current = weather,
        config = config,
      ) ?: return@forEach

      var added = false
      updateState { cur ->
        if (cur.weatherAlerts.any { it.id == directAlert.id }) return@updateState cur
        added = true
        val updated = (listOf(directAlert) + cur.weatherAlerts).take(40)
        alertStorage.saveAlerts(updated)
        cur.copy(
          weatherAlerts = updated,
          latestWeatherAlert = directAlert,
          logs = appendLog(cur.logs, "ALERT", "VFR ${directAlert.icao}: ${directAlert.summary}", station),
        )
      }
      if (added) fired.add(directAlert)
    }
    fired.forEach { weatherNotifier.notify(it) }
  }

  fun checkFavoritesWeatherNow() {
    val snapshot = state
    val vfrSelected = snapshot.alertConfig.resolvedVfrMonitoredIcaos(snapshot.favoriteIcaos)
    val targets = (snapshot.favoriteIcaos + vfrSelected).distinct()
    if (targets.isEmpty()) return
    evaluateCachedAirportsAgainstVfr(snapshot.alertConfig)
    if (!autoFetch) return
    scope.launch {
      updateState { it.copy(isCheckingFavorites = true) }
      var alertsTriggered = 0
      targets.forEach { icao ->
        val station = AirportCatalog.findOrDefault(icao)
        repository.fetchLiveWeather(station, state.serverConfig).onSuccess { fetched ->
          val resolved = fetched.resolvedStation
          resolvedStationsByIcao[resolved.icao] = resolved
          val favTrend = recordWindObservation(resolved.icao, fetched.weather)
          val favHist = recordWeatherHistory(resolved.icao, fetched.weather)
          val allHist = snapshotAllHistory24h()
          updateState { cur ->
            val updatedMap = cur.mapWeatherByIcao + (resolved.icao to fetched.weather)
            if (cur.station.icao.equals(resolved.icao, ignoreCase = true)) {
              cur.copy(
                windTrend = favTrend,
                weatherHistory24h = favHist,
                weatherHistoryByIcaoMap = allHist,
                qnhTrend24h = WeatherHistoryAnalyzer.buildQnhTrend(resolved.icao, favHist),
                mapStations = buildMapStationList(cur.station, cur.favoriteIcaos),
                mapWeatherByIcao = updatedMap,
              )
            } else {
              cur.copy(
                weatherHistoryByIcaoMap = allHist,
                mapStations = buildMapStationList(cur.station, cur.favoriteIcaos),
                mapWeatherByIcao = updatedMap,
              )
            }
          }
          if (evaluateFavoriteObservation(resolved, fetched.weather)) {
            alertsTriggered++
          }
        }
      }
      updateState { cur ->
        val msg = if (alertsTriggered > 0) {
          "Проверка станций (${targets.size}): предупреждений — $alertsTriggered"
        } else {
          "Проверка станций (${targets.size}): значимых изменений не выявлено"
        }
        cur.copy(
          isCheckingFavorites = false,
          logs = appendLog(cur.logs, "INFO", msg, cur.station),
        )
      }
    }
  }

  fun markAllAlertsRead() {
    updateState { cur ->
      val updated = cur.weatherAlerts.map { it.copy(isRead = true) }
      alertStorage.saveAlerts(updated)
      cur.copy(weatherAlerts = updated, latestWeatherAlert = null)
    }
  }

  fun clearWeatherAlerts() {
    updateState { cur ->
      alertStorage.saveAlerts(emptyList())
      cur.copy(
        weatherAlerts = emptyList(),
        latestWeatherAlert = null,
        logs = appendLog(cur.logs, "INFO", "История метеооповещений очищена", cur.station),
      )
    }
  }

  fun dismissLatestWeatherAlert() {
    updateState { it.copy(latestWeatherAlert = null) }
  }

  fun openAlertStation(icao: String) {
    val clean = icao.trim().uppercase()
    updateState { cur ->
      val updated = cur.weatherAlerts.map { alert ->
        if (alert.icao == clean) alert.copy(isRead = true) else alert
      }
      alertStorage.saveAlerts(updated)
      cur.copy(
        weatherAlerts = updated,
        latestWeatherAlert = if (cur.latestWeatherAlert?.icao == clean) null else cur.latestWeatherAlert,
      )
    }
    selectStation(clean)
  }

  fun setDataSourceMode(mode: DataSourceMode, fetchNow: Boolean = true) {
    val updated = state.serverConfig.withMode(mode)
    serverConfigStorage.saveConfig(updated)
    updateState { cur ->
      cur.copy(
        serverConfig = updated,
        logs = appendLog(cur.logs, "NET", "Источник данных: ${mode.displayName}", cur.station),
      )
    }
    if (fetchNow) {
      fetchLiveForStation(state.station)
    }
  }

  fun updateServerConfig(config: ServerConfig, fetchNow: Boolean = true) {
    val normalized = config.copy(
      url = ServerConfig.normalizeServerUrl(config.url),
      username = config.username.trim(),
    )
    serverConfigStorage.saveConfig(normalized)
    updateState { cur ->
      cur.copy(
        serverConfig = normalized,
        logs = appendLog(cur.logs, "NET", "Сохранены настройки сервера (${normalized.resolvedMode().displayName}): ${normalized.url}", cur.station),
      )
    }
    if (fetchNow) {
      fetchLiveForStation(state.station)
    }
  }

  fun dismissBanner() {
    updateState { it.copy(statusBanner = null) }
  }

  private fun evaluateFavoriteObservation(
    station: AirportStation,
    weather: DecodedWeather,
  ): Boolean {
    val cleanIcao = station.icao.trim().uppercase()
    val current = state
    val vfrMonitored = current.alertConfig.resolvedVfrMonitoredIcaos(current.favoriteIcaos)
    val isFav = cleanIcao in current.favoriteIcaos
    val isVfr = cleanIcao in vfrMonitored
    if (!isFav && !isVfr) return false

    val result = synchronized(weatherSnapshots) {
      val eval = WeatherChangeDetector.evaluate(
        station = station,
        previous = weatherSnapshots[cleanIcao],
        current = weather,
        config = current.alertConfig,
        isVfrMonitored = isVfr,
      )
      eval.snapshot?.let { snap ->
        weatherSnapshots[cleanIcao] = snap
        alertStorage.saveSnapshots(weatherSnapshots.toMap())
      }
      eval
    }
    val alert = result.alert ?: return false
    var recorded = false
    updateState { cur ->
      if (cur.weatherAlerts.any { it.id == alert.id }) return@updateState cur
      recorded = true
      val updatedAlerts = (listOf(alert) + cur.weatherAlerts).take(40)
      alertStorage.saveAlerts(updatedAlerts)
      cur.copy(
        weatherAlerts = updatedAlerts,
        latestWeatherAlert = alert,
        logs = appendLog(cur.logs, "ALERT", "${alert.icao}: ${alert.summary}", station),
      )
    }
    if (recorded) {
      weatherNotifier.notify(alert)
    }
    return recorded
  }

  private fun fetchLiveForStation(station: AirportStation) {
    scope.launch {
      updateState { it.copy(isLoading = true, statusBanner = null) }
      val result = repository.fetchLiveWeather(station, state.serverConfig)
      result.onSuccess { fetched ->
        val resolved = fetched.resolvedStation
        resolvedStationsByIcao[resolved.icao] = resolved
        val nextTrend = recordWindObservation(resolved.icao, fetched.weather)
        val nextHist = recordWeatherHistory(resolved.icao, fetched.weather)
        val allHist = snapshotAllHistory24h()
        updateState { cur ->
          val pair = resolved.runways.getOrElse(cur.selectedRunwayPairIndex) { resolved.runways.first() }
          val (pCalc, rCalc) = computeRunwayCalcs(pair, fetched.weather)
          val nowLocal = AviationWeatherParser.currentLocalTimeString(resolved.utcOffsetHours)
          var updatedLogs = cur.logs
          fetched.logMessages.forEach { msg ->
            updatedLogs = appendLog(updatedLogs, "NET", msg, resolved)
          }
          cur.copy(
            station = resolved,
            weather = fetched.weather,
            primaryRunwayCalc = pCalc,
            reciprocalRunwayCalc = rCalc,
            windTrend = nextTrend,
            weatherHistory24h = nextHist,
            weatherHistoryByIcaoMap = allHist,
            qnhTrend24h = WeatherHistoryAnalyzer.buildQnhTrend(resolved.icao, nextHist),
            mapStations = buildMapStationList(resolved, cur.favoriteIcaos),
            mapWeatherByIcao = cur.mapWeatherByIcao + (resolved.icao to fetched.weather),
            isLoading = false,
            statusBanner = null,
            liveLocalClock = nowLocal,
            logs = updatedLogs,
          )
        }
        evaluateFavoriteObservation(resolved, fetched.weather)
      }.onFailure { err ->
        val msg = err.message ?: "Ошибка загрузки METAR"
        updateState { cur ->
          cur.copy(
            isLoading = false,
            statusBanner = msg,
            logs = appendLog(cur.logs, "ERR", msg, cur.station),
          )
        }
      }
    }
  }

  fun selectD3TrendAirport(icaoInput: String, fetchIfMissing: Boolean = true) {
    val clean = icaoInput.trim().uppercase()
    if (clean.length != 4) return
    updateState { it.copy(selectedD3TrendIcao = clean) }
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
    scope.launch {
      updateState { it.copy(loadingHistoryIcao = clean) }
      val result = repository.fetch24hHistory(targetStation, state.serverConfig)
      result.onSuccess { fetched ->
        val merged = synchronized(weatherHistoryByIcao) {
          val curEntries = weatherHistoryByIcao[clean].orEmpty()
          val nextEntries = WeatherHistoryAnalyzer.mergeHistory(curEntries, fetched.entries)
          weatherHistoryByIcao[clean] = nextEntries
          weatherHistoryStorage.saveAll(weatherHistoryByIcao.toMap())
          nextEntries
        }
        val allSnap = snapshotAllHistory24h()
        updateState { cur ->
          val isCur = cur.station.icao.equals(clean, ignoreCase = true)
          cur.copy(
            weatherHistory24h = if (isCur) merged else cur.weatherHistory24h,
            weatherHistoryByIcaoMap = allSnap,
            qnhTrend24h = if (isCur) WeatherHistoryAnalyzer.buildQnhTrend(clean, merged) else cur.qnhTrend24h,
            loadingHistoryIcao = if (cur.loadingHistoryIcao == clean) null else cur.loadingHistoryIcao,
            logs = appendLog(cur.logs, "HIST", "24ч график ($clean): загружено ${merged.size} зап.", cur.station),
          )
        }
      }.onFailure { err ->
        updateState { cur ->
          cur.copy(
            loadingHistoryIcao = if (cur.loadingHistoryIcao == clean) null else cur.loadingHistoryIcao,
            logs = appendLog(cur.logs, "HIST", "24ч ($clean): ${err.message ?: "нет данных"}", cur.station),
          )
        }
      }
    }
  }

  fun load24hHistory(force: Boolean = false) {
    val target = state.station
    if (!autoFetch && !force) return
    scope.launch {
      updateState { it.copy(isLoadingHistory = true, historyStatusMessage = null) }
      val result = repository.fetch24hHistory(target, state.serverConfig)
      result.onSuccess { fetched ->
        val merged = synchronized(weatherHistoryByIcao) {
          val curEntries = weatherHistoryByIcao[target.icao].orEmpty()
          val nextEntries = WeatherHistoryAnalyzer.mergeHistory(curEntries, fetched.entries)
          weatherHistoryByIcao[target.icao] = nextEntries
          weatherHistoryStorage.saveAll(weatherHistoryByIcao.toMap())
          nextEntries
        }
        val allSnap = snapshotAllHistory24h()
        updateState { cur ->
          var updatedLogs = cur.logs
          fetched.logMessages.forEach { msg ->
            updatedLogs = appendLog(updatedLogs, "HIST", msg, target)
          }
          if (cur.station.icao.equals(target.icao, ignoreCase = true)) {
            cur.copy(
              weatherHistory24h = merged,
              weatherHistoryByIcaoMap = allSnap,
              qnhTrend24h = WeatherHistoryAnalyzer.buildQnhTrend(target.icao, merged),
              isLoadingHistory = false,
              historyStatusMessage = "Загружено записей за 24ч: ${merged.size}",
              logs = updatedLogs,
            )
          } else {
            cur.copy(
              weatherHistoryByIcaoMap = allSnap,
              isLoadingHistory = false,
              logs = updatedLogs,
            )
          }
        }
      }.onFailure { err ->
        val localCount = currentHistory24hFor(target.icao).size
        updateState { cur ->
          cur.copy(
            isLoadingHistory = false,
            historyStatusMessage = if (localCount > 0) {
              "Локальный кэш ($localCount зап.): ${err.message ?: "сервер недоступен"}"
            } else {
              err.message ?: "Нет данных истории за 24ч"
            },
            logs = appendLog(cur.logs, "HIST", err.message ?: "Ошибка загрузки 24ч", target),
          )
        }
      }
    }
  }

  fun mergeExternal24hHistory(icao: String, entries: List<WeatherHistoryEntry>) {
    val clean = icao.trim().uppercase()
    if (clean.length != 4 || entries.isEmpty()) return
    val merged = synchronized(weatherHistoryByIcao) {
      val curEntries = weatherHistoryByIcao[clean].orEmpty()
      val nextEntries = WeatherHistoryAnalyzer.mergeHistory(curEntries, entries)
      weatherHistoryByIcao[clean] = nextEntries
      weatherHistoryStorage.saveAll(weatherHistoryByIcao.toMap())
      nextEntries
    }
    val allSnap = snapshotAllHistory24h()
    updateState { cur ->
      val isActive = cur.station.icao.equals(clean, ignoreCase = true)
      cur.copy(
        weatherHistory24h = if (isActive) merged else cur.weatherHistory24h,
        weatherHistoryByIcaoMap = allSnap,
        qnhTrend24h = if (isActive) WeatherHistoryAnalyzer.buildQnhTrend(clean, merged) else cur.qnhTrend24h,
      )
    }
  }

  private fun updateState(transform: (DesktopUiState) -> DesktopUiState) {
    synchronized(this) {
      val next = transform(state)
      state = next
      if (SwingUtilities.isEventDispatchThread()) {
        listeners.forEach { it(next) }
      } else {
        SwingUtilities.invokeLater {
          listeners.forEach { it(state) }
        }
      }
    }
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
      val curEntries = weatherHistoryByIcao[clean].orEmpty()
      val nextEntries = WeatherHistoryAnalyzer.appendObservation(curEntries, weather)
      if (nextEntries != curEntries) {
        weatherHistoryByIcao[clean] = nextEntries
        weatherHistoryStorage.saveAll(weatherHistoryByIcao.toMap())
      }
      nextEntries
    }
  }

  fun fetchStationWeatherForMap(icaoInput: String) {
    val clean = icaoInput.trim().uppercase()
    if (clean.length != 4 || !autoFetch) return
    val target = resolvedStationsByIcao[clean] ?: AirportCatalog.findOrDefault(clean)
    scope.launch {
      updateState { it.copy(loadingMapIcao = clean) }
      repository.fetchLiveWeather(target, state.serverConfig).onSuccess { fetched ->
        val resolved = fetched.resolvedStation
        resolvedStationsByIcao[resolved.icao] = resolved
        val nextTrend = recordWindObservation(resolved.icao, fetched.weather)
        val nextHist = recordWeatherHistory(resolved.icao, fetched.weather)
        val allHist = snapshotAllHistory24h()
        updateState { cur ->
          val isCurrent = cur.station.icao.equals(resolved.icao, ignoreCase = true)
          cur.copy(
            station = if (isCurrent) resolved else cur.station,
            weather = if (isCurrent) fetched.weather else cur.weather,
            windTrend = if (isCurrent) nextTrend else cur.windTrend,
            weatherHistory24h = if (isCurrent) nextHist else cur.weatherHistory24h,
            weatherHistoryByIcaoMap = allHist,
            qnhTrend24h = if (isCurrent) WeatherHistoryAnalyzer.buildQnhTrend(resolved.icao, nextHist) else cur.qnhTrend24h,
            mapStations = buildMapStationList(if (isCurrent) resolved else cur.station, cur.favoriteIcaos),
            mapWeatherByIcao = cur.mapWeatherByIcao + (resolved.icao to fetched.weather),
            loadingMapIcao = if (cur.loadingMapIcao == clean) null else cur.loadingMapIcao,
          )
        }
        evaluateFavoriteObservation(resolved, fetched.weather)
      }.onFailure {
        updateState { cur ->
          cur.copy(
            loadingMapIcao = if (cur.loadingMapIcao == clean) null else cur.loadingMapIcao,
            logs = appendLog(cur.logs, "MAP", "Нет ответа METAR для $clean на карте", cur.station),
          )
        }
      }
    }
  }

  fun refreshMapWeather() {
    if (!autoFetch) return
    val stations = state.mapStations
    if (stations.isEmpty()) return
    scope.launch {
      updateState { it.copy(isRefreshingMap = true) }
      val serverCfg = state.serverConfig
      val results = coroutineScope {
        stations.map { st ->
          async {
            st.icao to repository.fetchLiveWeather(st, serverCfg)
          }
        }.awaitAll()
      }
      var loadedCount = 0
      val updatedWeather = state.mapWeatherByIcao.toMutableMap()
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
      val allHist = snapshotAllHistory24h()
      updateState { cur ->
        cur.copy(
          isRefreshingMap = false,
          weatherHistoryByIcaoMap = allHist,
          mapStations = buildMapStationList(cur.station, cur.favoriteIcaos),
          mapWeatherByIcao = updatedWeather.toMap(),
          logs = appendLog(cur.logs, "MAP", "Карта: загружена погода для $loadedCount из ${stations.size} аэродромов", cur.station),
        )
      }
    }
  }

  fun testIpServerConnection(
    icao: String,
    config: ServerConfig,
    onResult: (CustomMeteoServerClient.ConnectionTestReport) -> Unit,
  ) {
    scope.launch {
      val report = CustomMeteoServerClient().testConnection(icao, config)
      SwingUtilities.invokeLater { onResult(report) }
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
    station: AirportStation,
  ): List<LogEntry> {
    val ts = AviationWeatherParser.currentLocalTimeString(station.utcOffsetHours)
    return (listOf(LogEntry(ts, level, message)) + existing).take(60)
  }
}
