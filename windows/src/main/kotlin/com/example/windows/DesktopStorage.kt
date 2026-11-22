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
    return ServerConfig(
      url = props.getProperty(KEY_SERVER_URL, ServerConfig.DEFAULT_SERVER_URL)
        .trim()
        .ifBlank { ServerConfig.DEFAULT_SERVER_URL },
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

