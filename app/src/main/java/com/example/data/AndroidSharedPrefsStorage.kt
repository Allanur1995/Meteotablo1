package com.example.data

import android.content.Context

class SharedPreferencesFavoriteStorage(
  context: Context,
) : FavoriteAirportsStorage {

  private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  override fun loadFavorites(): List<String> {
    if (!prefs.contains(KEY_FAVORITES)) {
      saveFavorites(DEFAULT_FAVORITE_ICAOS)
      return DEFAULT_FAVORITE_ICAOS
    }
    val raw = prefs.getString(KEY_FAVORITES, "").orEmpty()
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
    prefs.edit().putString(KEY_FAVORITES, clean.joinToString(",")).apply()
  }

  private companion object {
    const val PREFS_NAME = "meteotablo_prefs"
    const val KEY_FAVORITES = "favorite_icao_codes"
  }
}

class SharedPreferencesServerConfigStorage(
  context: Context,
) : ServerConfigStorage {

  private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  override fun loadConfig(): ServerConfig {
    if (!prefs.contains(KEY_SERVER_URL)) {
      val defaults = ServerConfig()
      saveConfig(defaults)
      return defaults
    }
    val useCustom = prefs.getBoolean(KEY_USE_CUSTOM_SERVER, true)
    val fallbackNoaa = prefs.getBoolean(KEY_FALLBACK_NOAA, true)
    val rawMode = prefs.getString(KEY_SOURCE_MODE, null)
    val parsedMode = rawMode?.let { name ->
      DataSourceMode.entries.firstOrNull { it.name == name }
    } ?: when {
      !useCustom -> DataSourceMode.INTERNET_ONLY
      !fallbackNoaa -> DataSourceMode.IP_ONLY
      else -> DataSourceMode.AUTO_IP_FIRST
    }
    val loadedUrl = prefs.getString(KEY_SERVER_URL, ServerConfig.DEFAULT_SERVER_URL)
      ?.trim()
      ?.ifBlank { ServerConfig.DEFAULT_SERVER_URL }
      ?: ServerConfig.DEFAULT_SERVER_URL
    val migratedUrl = ServerConfig.migrateLegacyUrl(loadedUrl)
    return ServerConfig(
      url = migratedUrl,
      username = prefs.getString(KEY_USERNAME, ServerConfig.DEFAULT_USERNAME).orEmpty(),
      password = prefs.getString(KEY_PASSWORD, ServerConfig.DEFAULT_PASSWORD).orEmpty(),
      useCustomServer = useCustom,
      fallbackToNoaa = fallbackNoaa,
      sourceMode = parsedMode,
      useAviationWeatherGov = prefs.getBoolean(KEY_USE_AWC, true),
      useNoaaTgftp = prefs.getBoolean(KEY_USE_TGFTP, true),
      useVatsimMetar = prefs.getBoolean(KEY_USE_VATSIM, true),
    )
  }

  override fun saveConfig(config: ServerConfig) {
    val normalizedUrl = normalizeServerUrl(config.url)
    val resolvedMode = config.resolvedMode()
    prefs.edit()
      .putString(KEY_SERVER_URL, normalizedUrl)
      .putString(KEY_USERNAME, config.username.trim())
      .putString(KEY_PASSWORD, config.password)
      .putBoolean(KEY_USE_CUSTOM_SERVER, config.useCustomServer)
      .putBoolean(KEY_FALLBACK_NOAA, config.fallbackToNoaa)
      .putString(KEY_SOURCE_MODE, resolvedMode.name)
      .putBoolean(KEY_USE_AWC, config.useAviationWeatherGov)
      .putBoolean(KEY_USE_TGFTP, config.useNoaaTgftp)
      .putBoolean(KEY_USE_VATSIM, config.useVatsimMetar)
      .apply()
  }

  companion object {
    private const val PREFS_NAME = "meteotablo_prefs"
    private const val KEY_SERVER_URL = "custom_server_url"
    private const val KEY_USERNAME = "custom_server_user"
    private const val KEY_PASSWORD = "custom_server_pass"
    private const val KEY_USE_CUSTOM_SERVER = "use_custom_server"
    private const val KEY_FALLBACK_NOAA = "fallback_to_noaa"
    private const val KEY_SOURCE_MODE = "data_source_mode"
    private const val KEY_USE_AWC = "use_awc_gov"
    private const val KEY_USE_TGFTP = "use_noaa_tgftp"
    private const val KEY_USE_VATSIM = "use_vatsim_metar"

    fun normalizeServerUrl(raw: String): String = ServerConfig.normalizeServerUrl(raw)
  }
}

class SharedPreferencesWeatherPresetStorage(
  context: Context,
) : WeatherPresetStorage {

  private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  override fun loadPresets(): List<com.example.model.SavedWeatherPreset> {
    val raw = prefs.getString(KEY_WEATHER_PRESETS, null)
    return WeatherPresetSerializer.fromJson(raw)
  }

  override fun savePresets(presets: List<com.example.model.SavedWeatherPreset>) {
    prefs.edit()
      .putString(KEY_WEATHER_PRESETS, WeatherPresetSerializer.toJson(presets))
      .apply()
  }

  private companion object {
    const val PREFS_NAME = "meteotablo_prefs"
    const val KEY_WEATHER_PRESETS = "saved_weather_presets_json"
  }
}

class SharedPreferencesWeatherAlertStorage(
  context: Context,
) : WeatherAlertStorage {

  private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  override fun loadConfig(): com.example.model.WeatherAlertConfig {
    return WeatherAlertSerializer.configFromJson(prefs.getString(KEY_ALERT_CONFIG, null))
  }

  override fun saveConfig(config: com.example.model.WeatherAlertConfig) {
    prefs.edit().putString(KEY_ALERT_CONFIG, WeatherAlertSerializer.configToJson(config)).apply()
  }

  override fun loadSnapshots(): Map<String, com.example.model.FavoriteWeatherSnapshot> {
    return WeatherAlertSerializer.snapshotsFromJson(prefs.getString(KEY_ALERT_SNAPSHOTS, null))
  }

  override fun saveSnapshots(snapshots: Map<String, com.example.model.FavoriteWeatherSnapshot>) {
    prefs.edit().putString(KEY_ALERT_SNAPSHOTS, WeatherAlertSerializer.snapshotsToJson(snapshots)).apply()
  }

  override fun loadAlerts(): List<com.example.model.WeatherChangeAlert> {
    return WeatherAlertSerializer.alertsFromJson(prefs.getString(KEY_ALERT_HISTORY, null))
  }

  override fun saveAlerts(alerts: List<com.example.model.WeatherChangeAlert>) {
    prefs.edit().putString(KEY_ALERT_HISTORY, WeatherAlertSerializer.alertsToJson(alerts)).apply()
  }

  private companion object {
    const val PREFS_NAME = "meteotablo_prefs"
    const val KEY_ALERT_CONFIG = "weather_alert_config_json"
    const val KEY_ALERT_SNAPSHOTS = "weather_alert_snapshots_json"
    const val KEY_ALERT_HISTORY = "weather_alert_history_json"
  }
}

class SharedPreferencesWindHistoryStorage(
  context: Context,
) : WindHistoryStorage {

  private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  override fun loadAll(): Map<String, List<com.example.model.WindHistoryEntry>> {
    return WindHistorySerializer.fromJson(prefs.getString(KEY_WIND_HISTORY, null))
  }

  override fun saveAll(historyByIcao: Map<String, List<com.example.model.WindHistoryEntry>>) {
    prefs.edit().putString(KEY_WIND_HISTORY, WindHistorySerializer.toJson(historyByIcao)).apply()
  }

  private companion object {
    const val PREFS_NAME = "meteotablo_prefs"
    const val KEY_WIND_HISTORY = "wind_history_by_icao_json"
  }
}

class SharedPreferencesUnitPreferencesStorage(
  context: Context,
) : UnitPreferencesStorage {

  private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  override fun loadPreferences(): UnitPreferences {
    val rawWind = prefs.getString(KEY_WIND_UNIT, null)
    val rawPressure = prefs.getString(KEY_PRESSURE_UNIT, null)
    val wind = com.example.model.WindSpeedUnit.entries.firstOrNull { it.name == rawWind }
      ?: com.example.model.WindSpeedUnit.KT
    val pressure = com.example.model.PressureUnit.entries.firstOrNull { it.name == rawPressure }
      ?: com.example.model.PressureUnit.HPA
    val autoRefreshEnabled = prefs.getBoolean(KEY_AUTO_REFRESH_ENABLED, true)
    val rawInterval = prefs.getInt(
      KEY_AUTO_REFRESH_INTERVAL_MINUTES,
      AutoRefreshInterval.FIFTEEN_MIN.minutes,
    )
    val autoRefreshInterval = AutoRefreshInterval.fromMinutes(rawInterval).minutes
    val isNightMode = prefs.getBoolean(KEY_IS_NIGHT_MODE, false)
    return UnitPreferences(
      windUnit = wind,
      pressureUnit = pressure,
      autoRefreshEnabled = autoRefreshEnabled,
      autoRefreshIntervalMinutes = autoRefreshInterval,
      isNightMode = isNightMode,
    )
  }

  override fun savePreferences(preferences: UnitPreferences) {
    val validInterval = AutoRefreshInterval.fromMinutes(preferences.autoRefreshIntervalMinutes).minutes
    prefs.edit()
      .putString(KEY_WIND_UNIT, preferences.windUnit.name)
      .putString(KEY_PRESSURE_UNIT, preferences.pressureUnit.name)
      .putBoolean(KEY_AUTO_REFRESH_ENABLED, preferences.autoRefreshEnabled)
      .putInt(KEY_AUTO_REFRESH_INTERVAL_MINUTES, validInterval)
      .putBoolean(KEY_IS_NIGHT_MODE, preferences.isNightMode)
      .apply()
  }

  private companion object {
    const val PREFS_NAME = "meteotablo_prefs"
    const val KEY_WIND_UNIT = "preferred_wind_unit"
    const val KEY_PRESSURE_UNIT = "preferred_pressure_unit"
    const val KEY_AUTO_REFRESH_ENABLED = "auto_refresh_enabled"
    const val KEY_AUTO_REFRESH_INTERVAL_MINUTES = "auto_refresh_interval_minutes"
    const val KEY_IS_NIGHT_MODE = "is_night_mode"
  }
}

class SharedPreferencesWeatherHistoryStorage(
  context: Context,
) : WeatherHistoryStorage {

  private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  override fun loadAll(): Map<String, List<com.example.model.WeatherHistoryEntry>> {
    return WeatherHistorySerializer.fromJson(prefs.getString(KEY_WEATHER_HISTORY_24H, null))
  }

  override fun saveAll(historyByIcao: Map<String, List<com.example.model.WeatherHistoryEntry>>) {
    prefs.edit().putString(KEY_WEATHER_HISTORY_24H, WeatherHistorySerializer.toJson(historyByIcao)).apply()
  }

  private companion object {
    const val PREFS_NAME = "meteotablo_prefs"
    const val KEY_WEATHER_HISTORY_24H = "weather_history_24h_by_icao_json"
  }
}

