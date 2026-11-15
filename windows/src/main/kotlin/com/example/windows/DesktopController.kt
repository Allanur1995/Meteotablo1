package com.example.windows

import com.example.data.AirportCatalog
import com.example.data.AviationWeatherRepository
import com.example.data.DataSourceMode
import com.example.data.FavoriteAirportsStorage
import com.example.data.InMemoryFavoriteStorage
import com.example.data.InMemoryServerConfigStorage
import com.example.data.ServerConfig
import com.example.data.ServerConfigStorage
import com.example.domain.AviationWeatherParser
import com.example.domain.UnitConverter
import com.example.model.AirportStation
import com.example.model.DecodedWeather
import com.example.model.LogEntry
import com.example.model.PressureUnit
import com.example.model.RunwayCalculation
import com.example.model.RunwayPair
import com.example.model.WindSpeedUnit
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
  val favoriteIcaos: List<String> = emptyList(),
  val serverConfig: ServerConfig = ServerConfig(),
  val isLoading: Boolean = false,
  val statusBanner: String? = null,
  val liveLocalClock: String = "--:--",
  val logs: List<LogEntry> = emptyList(),
) {
  val activeRunwayPair: RunwayPair
    get() = station.runways.getOrElse(selectedRunwayPairIndex) { station.runways.first() }

  val isCurrentStationFavorite: Boolean
    get() = station.icao in favoriteIcaos

  val palette: DesktopPalette
    get() = if (isNightMode) NightDesktopPalette else DayDesktopPalette
}

class DesktopController(
  private val favoriteStorage: FavoriteAirportsStorage = InMemoryFavoriteStorage(),
  private val serverConfigStorage: ServerConfigStorage = InMemoryServerConfigStorage(),
  private val autoFetch: Boolean = true,
) {
  private val repository = AviationWeatherRepository()
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val scheduler = Executors.newSingleThreadScheduledExecutor { r ->
    Thread(r, "MeteotabloClock").apply { isDaemon = true }
  }
  private val listeners = CopyOnWriteArrayList<(DesktopUiState) -> Unit>()

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
    state = DesktopUiState(
      station = defaultStation,
      selectedRunwayPairIndex = 0,
      weather = initialWeather,
      primaryRunwayCalc = pCalc,
      reciprocalRunwayCalc = rCalc,
      favoriteIcaos = favoriteStorage.loadFavorites(),
      serverConfig = serverConfigStorage.loadConfig(),
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
      // Auto-refresh live weather every 5 minutes
      scheduler.scheduleAtFixedRate(
        { fetchLiveForStation(state.station) },
        300,
        300,
        TimeUnit.SECONDS,
      )
      fetchLiveForStation(defaultStation)
    }
  }

  fun addListener(listener: (DesktopUiState) -> Unit) {
    listeners.add(listener)
    listener(state)
  }

  fun removeListener(listener: (DesktopUiState) -> Unit) {
    listeners.remove(listener)
  }

  fun shutdown() {
    scheduler.shutdownNow()
  }

  fun toggleNightMode() {
    updateState { cur ->
      val next = !cur.isNightMode
      cur.copy(
        isNightMode = next,
        logs = appendLog(cur.logs, "UI", if (next) "Включен ночной режим" else "Включен дневной режим", cur.station),
      )
    }
  }

  fun toggleWindUnit() {
    updateState { cur ->
      val next = UnitConverter.cycleWindUnit(cur.windUnit)
      cur.copy(
        windUnit = next,
        logs = appendLog(cur.logs, "UI", "Единицы ветра: ${next.label}", cur.station),
      )
    }
  }

  fun setWindUnit(unit: WindSpeedUnit) {
    updateState { cur ->
      if (cur.windUnit == unit) return@updateState cur
      cur.copy(
        windUnit = unit,
        logs = appendLog(cur.logs, "UI", "Единицы ветра: ${unit.label}", cur.station),
      )
    }
  }

  fun togglePressureUnit() {
    updateState { cur ->
      val next = UnitConverter.cyclePressureUnit(cur.pressureUnit)
      cur.copy(
        pressureUnit = next,
        logs = appendLog(cur.logs, "UI", "Единицы давления: ${next.label}", cur.station),
      )
    }
  }

  fun setPressureUnit(unit: PressureUnit) {
    updateState { cur ->
      if (cur.pressureUnit == unit) return@updateState cur
      cur.copy(
        pressureUnit = unit,
        logs = appendLog(cur.logs, "UI", "Единицы давления: ${unit.label}", cur.station),
      )
    }
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
    val station = AirportCatalog.findOrDefault(clean)
    val emptyWeather = repository.createEmptyWeather(
      station = station,
      sourceLabel = if (autoFetch) "загрузка с сервера..." else "ожидание обновления",
    )
    val pair = station.runways.first()
    val (pCalc, rCalc) = computeRunwayCalcs(pair, emptyWeather)
    updateState { cur ->
      cur.copy(
        station = station,
        selectedRunwayPairIndex = 0,
        weather = emptyWeather,
        primaryRunwayCalc = pCalc,
        reciprocalRunwayCalc = rCalc,
        liveLocalClock = emptyWeather.receivedTimeLocal,
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
        logs = appendLog(cur.logs, "INFO", "Удалено из избранного: $clean", cur.station),
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
    updateState { s ->
      s.copy(
        weather = parsed,
        primaryRunwayCalc = pCalc,
        reciprocalRunwayCalc = rCalc,
        liveLocalClock = nowLocal,
        statusBanner = null,
        logs = appendLog(s.logs, "INFO", "Применён ручной METAR для ${cur.station.icao}", cur.station),
      )
    }
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

  private fun fetchLiveForStation(station: AirportStation) {
    scope.launch {
      updateState { it.copy(isLoading = true, statusBanner = null) }
      val result = repository.fetchLiveWeather(station, state.serverConfig)
      result.onSuccess { fetched ->
        val resolved = fetched.resolvedStation
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
            isLoading = false,
            statusBanner = null,
            liveLocalClock = nowLocal,
            logs = updatedLogs,
          )
        }
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
