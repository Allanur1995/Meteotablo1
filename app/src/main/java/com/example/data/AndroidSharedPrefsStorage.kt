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
    return ServerConfig(
      url = prefs.getString(KEY_SERVER_URL, ServerConfig.DEFAULT_SERVER_URL)
        ?.trim()
        ?.ifBlank { ServerConfig.DEFAULT_SERVER_URL }
        ?: ServerConfig.DEFAULT_SERVER_URL,
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
