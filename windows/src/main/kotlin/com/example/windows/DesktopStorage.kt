package com.example.windows

import com.example.data.DEFAULT_FAVORITE_ICAOS
import com.example.data.FavoriteAirportsStorage
import com.example.data.ServerConfig
import com.example.data.ServerConfigStorage
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.Properties

private fun defaultDesktopConfigFile(): File {
  val home = System.getProperty("user.home").orEmpty().ifBlank { "." }
  val dir = File(home, ".meteotablo")
  if (!dir.exists()) {
    dir.mkdirs()
  }
  return File(dir, "config.properties")
}

private fun loadProps(file: File): Properties {
  val props = Properties()
  if (file.exists()) {
    runCatching {
      InputStreamReader(FileInputStream(file), StandardCharsets.UTF_8).use { reader ->
        props.load(reader)
      }
    }
  }
  return props
}

private fun saveProps(file: File, props: Properties) {
  runCatching {
    file.parentFile?.mkdirs()
    OutputStreamWriter(FileOutputStream(file), StandardCharsets.UTF_8).use { writer ->
      props.store(writer, "Meteotablo Windows Settings")
    }
  }
}

class DesktopFavoriteStorage(
  private val configFile: File = defaultDesktopConfigFile(),
) : FavoriteAirportsStorage {

  override fun loadFavorites(): List<String> {
    val props = loadProps(configFile)
    if (!props.containsKey(KEY_FAVORITES)) {
      saveFavorites(DEFAULT_FAVORITE_ICAOS)
      return DEFAULT_FAVORITE_ICAOS
    }
    val raw = props.getProperty(KEY_FAVORITES, "")
    return raw
      .split(",")
      .map { it.trim().uppercase() }
      .filter { it.length == 4 }
      .distinct()
  }

  override fun saveFavorites(icaos: List<String>) {
    val clean = icaos
      .map { it.trim().uppercase() }
      .filter { it.length == 4 }
      .distinct()
    val props = loadProps(configFile)
    props.setProperty(KEY_FAVORITES, clean.joinToString(","))
    saveProps(configFile, props)
  }

  private companion object {
    const val KEY_FAVORITES = "favorite_icao_codes"
  }
}

class DesktopServerConfigStorage(
  private val configFile: File = defaultDesktopConfigFile(),
) : ServerConfigStorage {

  override fun loadConfig(): ServerConfig {
    val props = loadProps(configFile)
    if (!props.containsKey(KEY_SERVER_URL)) {
      val defaults = ServerConfig()
      saveConfig(defaults)
      return defaults
    }
    val useCustom = props.getProperty(KEY_USE_CUSTOM_SERVER, "true").toBooleanStrictOrNull() ?: true
    val fallbackNoaa = props.getProperty(KEY_FALLBACK_NOAA, "true").toBooleanStrictOrNull() ?: true
    val rawMode = props.getProperty(KEY_SOURCE_MODE, null)
    val parsedMode = rawMode?.let { name ->
      com.example.data.DataSourceMode.entries.firstOrNull { it.name == name }
    } ?: when {
      !useCustom -> com.example.data.DataSourceMode.INTERNET_ONLY
      !fallbackNoaa -> com.example.data.DataSourceMode.IP_ONLY
      else -> com.example.data.DataSourceMode.AUTO_IP_FIRST
    }
    val loadedUrl = props.getProperty(KEY_SERVER_URL, ServerConfig.DEFAULT_SERVER_URL)
      .trim()
      .ifBlank { ServerConfig.DEFAULT_SERVER_URL }
    val migratedUrl = ServerConfig.migrateLegacyUrl(loadedUrl)
    return ServerConfig(
      url = migratedUrl,
      username = props.getProperty(KEY_USERNAME, ServerConfig.DEFAULT_USERNAME).orEmpty(),
      password = props.getProperty(KEY_PASSWORD, ServerConfig.DEFAULT_PASSWORD).orEmpty(),
      useCustomServer = useCustom,
      fallbackToNoaa = fallbackNoaa,
      sourceMode = parsedMode,
      useAviationWeatherGov = props.getProperty(KEY_USE_AWC, "true").toBooleanStrictOrNull() ?: true,
      useNoaaTgftp = props.getProperty(KEY_USE_TGFTP, "true").toBooleanStrictOrNull() ?: true,
      useVatsimMetar = props.getProperty(KEY_USE_VATSIM, "true").toBooleanStrictOrNull() ?: true,
    )
  }

  override fun saveConfig(config: ServerConfig) {
    val props = loadProps(configFile)
    props.setProperty(KEY_SERVER_URL, ServerConfig.normalizeServerUrl(config.url))
    props.setProperty(KEY_USERNAME, config.username.trim())
    props.setProperty(KEY_PASSWORD, config.password)
    props.setProperty(KEY_USE_CUSTOM_SERVER, config.useCustomServer.toString())
    props.setProperty(KEY_FALLBACK_NOAA, config.fallbackToNoaa.toString())
    props.setProperty(KEY_SOURCE_MODE, config.resolvedMode().name)
    props.setProperty(KEY_USE_AWC, config.useAviationWeatherGov.toString())
    props.setProperty(KEY_USE_TGFTP, config.useNoaaTgftp.toString())
    props.setProperty(KEY_USE_VATSIM, config.useVatsimMetar.toString())
    saveProps(configFile, props)
  }

  private companion object {
    const val KEY_SERVER_URL = "custom_server_url"
    const val KEY_USERNAME = "custom_server_user"
    const val KEY_PASSWORD = "custom_server_pass"
    const val KEY_USE_CUSTOM_SERVER = "use_custom_server"
    const val KEY_FALLBACK_NOAA = "fallback_to_noaa"
    const val KEY_SOURCE_MODE = "data_source_mode"
    const val KEY_USE_AWC = "use_awc_gov"
    const val KEY_USE_TGFTP = "use_noaa_tgftp"
    const val KEY_USE_VATSIM = "use_vatsim_metar"
  }
}

class DesktopWeatherPresetStorage(
  private val configFile: File = defaultDesktopConfigFile(),
) : com.example.data.WeatherPresetStorage {

  override fun loadPresets(): List<com.example.model.SavedWeatherPreset> {
    val props = loadProps(configFile)
    return com.example.data.WeatherPresetSerializer.fromJson(props.getProperty(KEY_WEATHER_PRESETS, null))
  }

  override fun savePresets(presets: List<com.example.model.SavedWeatherPreset>) {
    val props = loadProps(configFile)
    props.setProperty(KEY_WEATHER_PRESETS, com.example.data.WeatherPresetSerializer.toJson(presets))
    saveProps(configFile, props)
  }

  private companion object {
    const val KEY_WEATHER_PRESETS = "saved_weather_presets_json"
  }
}

class DesktopWeatherAlertStorage(
  private val configFile: File = defaultDesktopConfigFile(),
) : com.example.data.WeatherAlertStorage {

  override fun loadConfig(): com.example.model.WeatherAlertConfig {
    val props = loadProps(configFile)
    return com.example.data.WeatherAlertSerializer.configFromJson(props.getProperty(KEY_ALERT_CONFIG, null))
  }

  override fun saveConfig(config: com.example.model.WeatherAlertConfig) {
    val props = loadProps(configFile)
    props.setProperty(KEY_ALERT_CONFIG, com.example.data.WeatherAlertSerializer.configToJson(config))
    saveProps(configFile, props)
  }

  override fun loadSnapshots(): Map<String, com.example.model.FavoriteWeatherSnapshot> {
    val props = loadProps(configFile)
    return com.example.data.WeatherAlertSerializer.snapshotsFromJson(props.getProperty(KEY_ALERT_SNAPSHOTS, null))
  }

  override fun saveSnapshots(snapshots: Map<String, com.example.model.FavoriteWeatherSnapshot>) {
    val props = loadProps(configFile)
    props.setProperty(KEY_ALERT_SNAPSHOTS, com.example.data.WeatherAlertSerializer.snapshotsToJson(snapshots))
    saveProps(configFile, props)
  }

  override fun loadAlerts(): List<com.example.model.WeatherChangeAlert> {
    val props = loadProps(configFile)
    return com.example.data.WeatherAlertSerializer.alertsFromJson(props.getProperty(KEY_ALERT_HISTORY, null))
  }

  override fun saveAlerts(alerts: List<com.example.model.WeatherChangeAlert>) {
    val props = loadProps(configFile)
    props.setProperty(KEY_ALERT_HISTORY, com.example.data.WeatherAlertSerializer.alertsToJson(alerts))
    saveProps(configFile, props)
  }

  private companion object {
    const val KEY_ALERT_CONFIG = "weather_alert_config_json"
    const val KEY_ALERT_SNAPSHOTS = "weather_alert_snapshots_json"
    const val KEY_ALERT_HISTORY = "weather_alert_history_json"
  }
}

class DesktopWindHistoryStorage(
  private val configFile: File = defaultDesktopConfigFile(),
) : com.example.data.WindHistoryStorage {

  override fun loadAll(): Map<String, List<com.example.model.WindHistoryEntry>> {
    val props = loadProps(configFile)
    return com.example.data.WindHistorySerializer.fromJson(props.getProperty(KEY_WIND_HISTORY, null))
  }

  override fun saveAll(historyByIcao: Map<String, List<com.example.model.WindHistoryEntry>>) {
    val props = loadProps(configFile)
    props.setProperty(KEY_WIND_HISTORY, com.example.data.WindHistorySerializer.toJson(historyByIcao))
    saveProps(configFile, props)
  }

  private companion object {
    const val KEY_WIND_HISTORY = "wind_history_by_icao_json"
  }
}

class DesktopWeatherHistoryStorage(
  private val configFile: File = defaultDesktopConfigFile(),
) : com.example.data.WeatherHistoryStorage {

  override fun loadAll(): Map<String, List<com.example.model.WeatherHistoryEntry>> {
    val props = loadProps(configFile)
    return com.example.data.WeatherHistorySerializer.fromJson(props.getProperty(KEY_WEATHER_HISTORY, null))
  }

  override fun saveAll(historyByIcao: Map<String, List<com.example.model.WeatherHistoryEntry>>) {
    val props = loadProps(configFile)
    props.setProperty(KEY_WEATHER_HISTORY, com.example.data.WeatherHistorySerializer.toJson(historyByIcao))
    saveProps(configFile, props)
  }

  private companion object {
    const val KEY_WEATHER_HISTORY = "weather_history_24h_by_icao_json"
  }
}

class DesktopUnitPreferencesStorage(
  private val configFile: File = defaultDesktopConfigFile(),
) : com.example.data.UnitPreferencesStorage {

  override fun loadPreferences(): com.example.data.UnitPreferences {
    val props = loadProps(configFile)
    val windUnit = props.getProperty(KEY_WIND_UNIT, null)?.let { name ->
      com.example.model.WindSpeedUnit.entries.firstOrNull { it.name == name }
    } ?: com.example.model.WindSpeedUnit.KT
    val pressureUnit = props.getProperty(KEY_PRESSURE_UNIT, null)?.let { name ->
      com.example.model.PressureUnit.entries.firstOrNull { it.name == name }
    } ?: com.example.model.PressureUnit.HPA
    val autoRefreshEnabled = props.getProperty(KEY_AUTO_REFRESH_ENABLED, "true").toBooleanStrictOrNull() ?: true
    val intervalMin = com.example.data.AutoRefreshInterval.fromMinutes(
      props.getProperty(KEY_AUTO_REFRESH_MINUTES, com.example.data.AutoRefreshInterval.ONE_MIN.minutes.toString()).toIntOrNull()
        ?: com.example.data.AutoRefreshInterval.ONE_MIN.minutes,
    ).minutes
    val isNightMode = props.getProperty(KEY_NIGHT_MODE, "false").toBooleanStrictOrNull() ?: false
    return com.example.data.UnitPreferences(
      windUnit = windUnit,
      pressureUnit = pressureUnit,
      autoRefreshEnabled = autoRefreshEnabled,
      autoRefreshIntervalMinutes = intervalMin,
      isNightMode = isNightMode,
    )
  }

  override fun savePreferences(preferences: com.example.data.UnitPreferences) {
    val props = loadProps(configFile)
    props.setProperty(KEY_WIND_UNIT, preferences.windUnit.name)
    props.setProperty(KEY_PRESSURE_UNIT, preferences.pressureUnit.name)
    props.setProperty(KEY_AUTO_REFRESH_ENABLED, preferences.autoRefreshEnabled.toString())
    props.setProperty(
      KEY_AUTO_REFRESH_MINUTES,
      com.example.data.AutoRefreshInterval.fromMinutes(preferences.autoRefreshIntervalMinutes).minutes.toString(),
    )
    props.setProperty(KEY_NIGHT_MODE, preferences.isNightMode.toString())
    saveProps(configFile, props)
  }

  private companion object {
    const val KEY_WIND_UNIT = "pref_wind_unit"
    const val KEY_PRESSURE_UNIT = "pref_pressure_unit"
    const val KEY_AUTO_REFRESH_ENABLED = "pref_auto_refresh_enabled"
    const val KEY_AUTO_REFRESH_MINUTES = "pref_auto_refresh_minutes"
    const val KEY_NIGHT_MODE = "pref_night_mode"
  }
}

class DesktopRecentSearchesStorage(
  private val configFile: File = defaultDesktopConfigFile(),
) : com.example.data.RecentSearchesStorage {

  override fun loadRecentSearches(): List<String> {
    val props = loadProps(configFile)
    val raw = props.getProperty(KEY_RECENT_SEARCHES, "").orEmpty()
    if (raw.isBlank()) return emptyList()
    return com.example.data.RecentSearchSerializer.normalizeIcaos(raw.split(","))
  }

  override fun saveRecentSearches(icaos: List<String>) {
    val clean = com.example.data.RecentSearchSerializer.normalizeIcaos(icaos)
    val props = loadProps(configFile)
    props.setProperty(KEY_RECENT_SEARCHES, clean.joinToString(","))
    saveProps(configFile, props)
  }

  private companion object {
    const val KEY_RECENT_SEARCHES = "recent_search_icao_codes"
  }
}

class DesktopWeatherCacheStorage(
  private val cacheFile: File = File(defaultDesktopConfigFile().parentFile, "weather_cache.properties"),
) : com.example.data.WeatherCacheRepository {

  private fun hasMeaningfulReport(weather: com.example.model.DecodedWeather): Boolean {
    val metar = weather.rawMetar.trim()
    val taf = weather.rawTaf.trim()
    val validMetar = metar.isNotBlank() &&
      !metar.contains("--:--Z") &&
      !metar.contains("нет данных", ignoreCase = true)
    val validTaf = taf.isNotBlank() &&
      !taf.contains("--:--Z") &&
      !taf.contains("нет данных", ignoreCase = true)
    return validMetar || validTaf
  }

  override suspend fun getCachedWeather(icao: String): com.example.data.CachedWeatherRecord? {
    val clean = icao.trim().uppercase()
    if (clean.length != 4) return null
    val props = loadProps(cacheFile)
    val rawMetar = props.getProperty("$clean.metar", "") ?: return null
    if (rawMetar.isBlank()) return null
    val rawTaf = props.getProperty("$clean.taf", "").orEmpty()
    val sourceLabel = props.getProperty("$clean.source", "локальный кэш").orEmpty()
    val obsUtc = props.getProperty("$clean.obsUtc", "--:--Z").orEmpty()
    val cachedAt = props.getProperty("$clean.cachedAt", "0")?.toLongOrNull() ?: 0L
    val runways = com.example.data.RunwayPairSerializer.deserialize(
      props.getProperty("$clean.runways", "").orEmpty(),
      clean,
    )
    val fallbackStation = com.example.data.AirportCatalog.findOrDefault(clean)
    val station = com.example.model.AirportStation(
      icao = clean,
      name = props.getProperty("$clean.name", fallbackStation.name).orEmpty(),
      city = props.getProperty("$clean.city", fallbackStation.city).orEmpty(),
      latitude = props.getProperty("$clean.lat", fallbackStation.latitude.toString())?.toDoubleOrNull()
        ?: fallbackStation.latitude,
      longitude = props.getProperty("$clean.lon", fallbackStation.longitude.toString())?.toDoubleOrNull()
        ?: fallbackStation.longitude,
      elevationMeters = props.getProperty("$clean.elev", fallbackStation.elevationMeters.toString())?.toIntOrNull()
        ?: fallbackStation.elevationMeters,
      utcOffsetHours = props.getProperty("$clean.utcOffset", fallbackStation.utcOffsetHours.toString())?.toIntOrNull()
        ?: fallbackStation.utcOffsetHours,
      timezoneLabel = props.getProperty("$clean.tzLabel", fallbackStation.timezoneLabel).orEmpty(),
      runways = runways,
    )
    return com.example.data.CachedWeatherRecord(
      station = station,
      rawMetar = rawMetar,
      rawTaf = rawTaf,
      sourceLabel = sourceLabel,
      observationTimeUtc = obsUtc,
      cachedAtMillis = cachedAt,
    )
  }

  override suspend fun getAllCachedWeather(): Map<String, com.example.data.CachedWeatherRecord> {
    val props = loadProps(cacheFile)
    val icaos = props.stringPropertyNames()
      .mapNotNull { key -> key.substringBefore('.', "").takeIf { it.length == 4 } }
      .toSet()
    val out = linkedMapOf<String, com.example.data.CachedWeatherRecord>()
    for (icao in icaos) {
      getCachedWeather(icao)?.let { out[icao] = it }
    }
    return out
  }

  override suspend fun saveWeather(
    station: com.example.model.AirportStation,
    weather: com.example.model.DecodedWeather,
    cachedAtMillis: Long,
  ) {
    if (!hasMeaningfulReport(weather)) return
    val clean = station.icao.trim().uppercase()
    if (clean.length != 4) return
    val props = loadProps(cacheFile)
    props.setProperty("$clean.metar", weather.rawMetar)
    props.setProperty("$clean.taf", weather.rawTaf)
    props.setProperty("$clean.source", weather.sourceLabel)
    props.setProperty("$clean.obsUtc", weather.observationTimeUtc)
    props.setProperty("$clean.cachedAt", cachedAtMillis.toString())
    props.setProperty("$clean.name", station.name)
    props.setProperty("$clean.city", station.city)
    props.setProperty("$clean.lat", station.latitude.toString())
    props.setProperty("$clean.lon", station.longitude.toString())
    props.setProperty("$clean.elev", station.elevationMeters.toString())
    props.setProperty("$clean.utcOffset", station.utcOffsetHours.toString())
    props.setProperty("$clean.tzLabel", station.timezoneLabel)
    props.setProperty("$clean.runways", com.example.data.RunwayPairSerializer.serialize(station.runways))
    saveProps(cacheFile, props)
  }
}

class DesktopAirportRunwaysStorage(
  private val configFile: File = defaultDesktopConfigFile(),
) : com.example.data.AirportRunwaysStorage {

  override fun loadAll(): Map<String, List<com.example.model.RunwayPair>> {
    val props = loadProps(configFile)
    return com.example.data.AirportRunwaysSerializer.fromJson(props.getProperty(KEY_CUSTOM_RUNWAYS, null))
  }

  override fun saveForAirport(icao: String, runways: List<com.example.model.RunwayPair>) {
    val clean = icao.trim().uppercase()
    if (clean.length != 4 || runways.isEmpty()) return
    val current = loadAll().toMutableMap()
    current[clean] = runways.toList()
    val props = loadProps(configFile)
    props.setProperty(KEY_CUSTOM_RUNWAYS, com.example.data.AirportRunwaysSerializer.toJson(current))
    saveProps(configFile, props)
  }

  override fun clearForAirport(icao: String) {
    val clean = icao.trim().uppercase()
    if (clean.length != 4) return
    val current = loadAll().toMutableMap()
    if (current.remove(clean) != null) {
      val props = loadProps(configFile)
      props.setProperty(KEY_CUSTOM_RUNWAYS, com.example.data.AirportRunwaysSerializer.toJson(current))
      saveProps(configFile, props)
    }
  }

  private companion object {
    const val KEY_CUSTOM_RUNWAYS = "custom_airport_runways_json"
  }
}




