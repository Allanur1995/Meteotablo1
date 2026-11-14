package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AirportCatalog
import com.example.data.AviationWeatherRepository
import com.example.data.FavoriteAirportsStorage
import com.example.data.InMemoryFavoriteStorage
import com.example.data.InMemoryServerConfigStorage
import com.example.data.ServerConfig
import com.example.data.ServerConfigStorage
import com.example.data.SharedPreferencesServerConfigStorage
import com.example.domain.AviationWeatherParser
import com.example.domain.UnitConverter
import com.example.model.AirportStation
import com.example.model.DecodedWeather
import com.example.model.LogEntry
import com.example.model.PressureUnit
import com.example.model.RunwayCalculation
import com.example.model.RunwayPair
import com.example.model.WindSpeedUnit
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
  val favoriteIcaos: List<String> = emptyList(),
  val serverConfig: ServerConfig = ServerConfig(),
  val isLoading: Boolean = false,
  val statusBanner: String? = null,
  val liveLocalClock: String = "22:11",
  val logs: List<LogEntry> = emptyList(),
) {
  val activeRunwayPair: RunwayPair
    get() = station.runways.getOrElse(selectedRunwayPairIndex) { station.runways.first() }

  val isCurrentStationFavorite: Boolean
    get() = station.icao in favoriteIcaos
}

class MeteoViewModel(
  private val favoriteStorage: FavoriteAirportsStorage = InMemoryFavoriteStorage(),
  private val serverConfigStorage: ServerConfigStorage = InMemoryServerConfigStorage(),
  private val autoFetch: Boolean = true,
) : ViewModel() {

  private val repository = AviationWeatherRepository()

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
    val initialServerConfig = serverConfigStorage.loadConfig()
    _uiState = MutableStateFlow(
      MeteoUiState(
        station = defaultStation,
        selectedRunwayPairIndex = 0,
        weather = initialWeather,
        primaryRunwayCalc = primaryCalc,
        reciprocalRunwayCalc = reciprocalCalc,
        favoriteIcaos = initialFavorites,
        serverConfig = initialServerConfig,
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

    startClockTicker()
    if (autoFetch) {
      fetchLiveForStation(defaultStation)
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

  fun toggleNightMode() {
    _uiState.update { state ->
      val next = !state.isNightMode
      state.copy(
        isNightMode = next,
        logs = appendLog(state.logs, "UI", if (next) "Включен ночной режим" else "Включен дневной режим"),
      )
    }
  }

  fun toggleFullscreen() {
    _uiState.update { state ->
      state.copy(isFullscreen = !state.isFullscreen)
    }
  }

  fun toggleWindUnit() {
    _uiState.update { state ->
      val next = UnitConverter.cycleWindUnit(state.windUnit)
      state.copy(
        windUnit = next,
        logs = appendLog(state.logs, "UI", "Единицы ветра: ${next.label}"),
      )
    }
  }

  fun setWindUnit(unit: WindSpeedUnit) {
    _uiState.update { state ->
      if (state.windUnit == unit) return@update state
      state.copy(
        windUnit = unit,
        logs = appendLog(state.logs, "UI", "Единицы ветра: ${unit.label}"),
      )
    }
  }

  fun togglePressureUnit() {
    _uiState.update { state ->
      val next = UnitConverter.cyclePressureUnit(state.pressureUnit)
      state.copy(
        pressureUnit = next,
        logs = appendLog(state.logs, "UI", "Единицы давления: ${next.label}"),
      )
    }
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
      val msg = if (clean in updated) "Добавлено в избранное: $clean" else "Удалено из избранного: $clean"
      state.copy(
        favoriteIcaos = updated,
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
      state.copy(
        favoriteIcaos = updated,
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
      state.copy(
        favoriteIcaos = updated,
        logs = appendLog(state.logs, "INFO", "Удалено из избранного: $clean"),
      )
    }
  }

  fun setPressureUnit(unit: PressureUnit) {
    _uiState.update { state ->
      if (state.pressureUnit == unit) return@update state
      state.copy(
        pressureUnit = unit,
        logs = appendLog(state.logs, "UI", "Единицы давления: ${unit.label}"),
      )
    }
  }

  fun updateServerConfig(config: ServerConfig, fetchNow: Boolean = true) {
    val normalized = config.copy(
      url = SharedPreferencesServerConfigStorage.normalizeServerUrl(config.url),
      username = config.username.trim(),
    )
    serverConfigStorage.saveConfig(normalized)
    _uiState.update { state ->
      state.copy(
        serverConfig = normalized,
        logs = appendLog(state.logs, "NET", "Сохранены настройки сервера: ${normalized.url}"),
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
    val station = AirportCatalog.findOrDefault(clean)
    val emptyWeather = repository.createEmptyWeather(
      station = station,
      sourceLabel = if (autoFetch) "загрузка с сервера..." else "ожидание обновления",
    )
    val pair = station.runways.first()
    val (pCalc, rCalc) = computeRunwayCalcs(pair, emptyWeather)
    _uiState.update { state ->
      state.copy(
        station = station,
        selectedRunwayPairIndex = 0,
        weather = emptyWeather,
        primaryRunwayCalc = pCalc,
        reciprocalRunwayCalc = rCalc,
        liveLocalClock = emptyWeather.receivedTimeLocal,
        statusBanner = null,
        logs = appendLog(state.logs, "INFO", "Выбран аэропорт ${station.icao} (${station.name})"),
      )
    }
    if (autoFetch) {
      fetchLiveForStation(station)
    }
  }

  fun refresh() {
    fetchLiveForStation(_uiState.value.station)
  }

  fun applyCustomMetar(rawMetar: String, rawTaf: String) {
    val current = _uiState.value
    if (rawMetar.isBlank()) return
    val parsed = AviationWeatherParser.parse(
      rawMetarInput = rawMetar,
      rawTafInput = rawTaf.ifBlank { current.weather.rawTaf },
      station = current.station,
      sourceLabel = "ручной ввод METAR",
    )
    val pair = current.activeRunwayPair
    val (pCalc, rCalc) = computeRunwayCalcs(pair, parsed)
    val nowLocal = AviationWeatherParser.currentLocalTimeString(current.station.utcOffsetHours)
    _uiState.update { state ->
      state.copy(
        weather = parsed,
        primaryRunwayCalc = pCalc,
        reciprocalRunwayCalc = rCalc,
        liveLocalClock = nowLocal,
        statusBanner = null,
        logs = appendLog(state.logs, "INFO", "Применён ручной METAR для ${current.station.icao}"),
      )
    }
  }

  fun dismissBanner() {
    _uiState.update { it.copy(statusBanner = null) }
  }

  private fun fetchLiveForStation(station: AirportStation) {
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true, statusBanner = null) }
      val result = repository.fetchLiveWeather(station, _uiState.value.serverConfig)
      result.onSuccess { fetched ->
        val resolved = fetched.resolvedStation
        val pair = resolved.runways.getOrElse(_uiState.value.selectedRunwayPairIndex) { resolved.runways.first() }
        val (pCalc, rCalc) = computeRunwayCalcs(pair, fetched.weather)
        val nowLocal = AviationWeatherParser.currentLocalTimeString(resolved.utcOffsetHours)
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
            isLoading = false,
            statusBanner = null,
            liveLocalClock = nowLocal,
            logs = updatedLogs,
          )
        }
      }.onFailure { err ->
        val msg = err.message ?: "Ошибка загрузки METAR"
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
  ): List<LogEntry> {
    val ts = AviationWeatherParser.currentLocalTimeString(_uiState.value.station.utcOffsetHours)
    return (listOf(LogEntry(ts, level, message)) + existing).take(50)
  }
}
