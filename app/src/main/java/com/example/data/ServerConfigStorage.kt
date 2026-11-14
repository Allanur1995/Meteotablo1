package com.example.data

import android.content.Context

data class ServerConfig(
  val url: String = DEFAULT_SERVER_URL,
  val username: String = DEFAULT_USERNAME,
  val password: String = DEFAULT_PASSWORD,
  val useCustomServer: Boolean = true,
  val fallbackToNoaa: Boolean = true,
) {
  companion object {
    const val DEFAULT_SERVER_URL = "http://217.174.229.206:8181/"
    const val DEFAULT_USERNAME = "meteo"
    const val DEFAULT_PASSWORD = "meteo"
  }
}

interface ServerConfigStorage {
  fun loadConfig(): ServerConfig
  fun saveConfig(config: ServerConfig)
}

class InMemoryServerConfigStorage(
  initial: ServerConfig = ServerConfig(),
) : ServerConfigStorage {
  private var stored: ServerConfig = initial

  override fun loadConfig(): ServerConfig = stored

  override fun saveConfig(config: ServerConfig) {
    stored = config
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
    return ServerConfig(
      url = prefs.getString(KEY_SERVER_URL, ServerConfig.DEFAULT_SERVER_URL)
        ?.trim()
        ?.ifBlank { ServerConfig.DEFAULT_SERVER_URL }
        ?: ServerConfig.DEFAULT_SERVER_URL,
      username = prefs.getString(KEY_USERNAME, ServerConfig.DEFAULT_USERNAME).orEmpty(),
      password = prefs.getString(KEY_PASSWORD, ServerConfig.DEFAULT_PASSWORD).orEmpty(),
      useCustomServer = prefs.getBoolean(KEY_USE_CUSTOM_SERVER, true),
      fallbackToNoaa = prefs.getBoolean(KEY_FALLBACK_NOAA, true),
    )
  }

  override fun saveConfig(config: ServerConfig) {
    val normalizedUrl = normalizeServerUrl(config.url)
    prefs.edit()
      .putString(KEY_SERVER_URL, normalizedUrl)
      .putString(KEY_USERNAME, config.username.trim())
      .putString(KEY_PASSWORD, config.password)
      .putBoolean(KEY_USE_CUSTOM_SERVER, config.useCustomServer)
      .putBoolean(KEY_FALLBACK_NOAA, config.fallbackToNoaa)
      .apply()
  }

  companion object {
    private const val PREFS_NAME = "meteotablo_prefs"
    private const val KEY_SERVER_URL = "custom_server_url"
    private const val KEY_USERNAME = "custom_server_user"
    private const val KEY_PASSWORD = "custom_server_pass"
    private const val KEY_USE_CUSTOM_SERVER = "use_custom_server"
    private const val KEY_FALLBACK_NOAA = "fallback_to_noaa"

    fun normalizeServerUrl(raw: String): String {
      val trimmed = raw.trim()
      if (trimmed.isBlank()) return ServerConfig.DEFAULT_SERVER_URL
      val withScheme = if (trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true)
      ) {
        trimmed
      } else {
        "http://$trimmed"
      }
      return if (withScheme.endsWith("/")) withScheme else "$withScheme/"
    }
  }
}
