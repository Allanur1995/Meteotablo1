package com.example.data

enum class DataSourceMode(
  val shortLabel: String,
  val displayName: String,
) {
  AUTO_IP_FIRST("Серв→Инет", "Авто: Сервер → Интернет"),
  AUTO_INTERNET_FIRST("Инет→Серв", "Авто: Интернет → Сервер"),
  IP_ONLY("Сервер", "Только метеосервер"),
  INTERNET_ONLY("Интернет", "Только Интернет"),
}

data class ServerConfig(
  val url: String = DEFAULT_SERVER_URL,
  val username: String = DEFAULT_USERNAME,
  val password: String = DEFAULT_PASSWORD,
  val useCustomServer: Boolean = true,
  val fallbackToNoaa: Boolean = true,
  val sourceMode: DataSourceMode = DataSourceMode.AUTO_IP_FIRST,
  val useAviationWeatherGov: Boolean = true,
  val useNoaaTgftp: Boolean = true,
  val useVatsimMetar: Boolean = true,
) {
  fun resolvedMode(): DataSourceMode = when {
    !useCustomServer && fallbackToNoaa -> DataSourceMode.INTERNET_ONLY
    useCustomServer && !fallbackToNoaa -> DataSourceMode.IP_ONLY
    else -> sourceMode
  }

  fun withMode(mode: DataSourceMode): ServerConfig = when (mode) {
    DataSourceMode.AUTO_IP_FIRST -> copy(sourceMode = mode, useCustomServer = true, fallbackToNoaa = true)
    DataSourceMode.AUTO_INTERNET_FIRST -> copy(sourceMode = mode, useCustomServer = true, fallbackToNoaa = true)
    DataSourceMode.IP_ONLY -> copy(sourceMode = mode, useCustomServer = true, fallbackToNoaa = false)
    DataSourceMode.INTERNET_ONLY -> copy(sourceMode = mode, useCustomServer = false, fallbackToNoaa = true)
  }

  companion object {
    const val DEFAULT_SERVER_URL = "http://217.174.229.206:8181/cgi-bin/wdr"
    const val LEGACY_WLOGIN_URL = "http://217.174.229.206:8181/cgi-bin/wlogin"
    const val LEGACY_ROOT_URL = "http://217.174.229.206:8181/"
    const val DEFAULT_USERNAME = "meteo"
    const val DEFAULT_PASSWORD = "meteo"

    fun migrateLegacyUrl(raw: String): String {
      val trimmed = raw.trim()
      return when {
        trimmed.isBlank() ||
          trimmed == LEGACY_ROOT_URL ||
          trimmed == "http://217.174.229.206:8181" ||
          trimmed == LEGACY_WLOGIN_URL -> DEFAULT_SERVER_URL
        else -> trimmed
      }
    }

    fun normalizeServerUrl(raw: String): String {
      val trimmed = raw.trim()
      if (trimmed.isBlank()) return DEFAULT_SERVER_URL
      val withScheme = if (trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true)
      ) {
        trimmed
      } else {
        "http://$trimmed"
      }
      if (withScheme.endsWith("/") || withScheme.contains("?")) {
        return withScheme
      }
      val schemeIdx = withScheme.indexOf("://")
      val pathStart = if (schemeIdx >= 0) withScheme.indexOf('/', schemeIdx + 3) else withScheme.indexOf('/')
      // Bare host[:port] without path gets a trailing "/"; any explicit path (e.g. /cgi-bin/wlogin or /index.php) is preserved as-is.
      return if (pathStart < 0) "$withScheme/" else withScheme
    }

    fun retrofitBaseUrl(raw: String): String {
      val normalized = normalizeServerUrl(raw).substringBefore('?')
      if (normalized.endsWith("/")) return normalized
      val schemeIdx = normalized.indexOf("://")
      val firstPathSlash = if (schemeIdx >= 0) normalized.indexOf('/', schemeIdx + 3) else normalized.indexOf('/')
      return if (firstPathSlash < 0) {
        "$normalized/"
      } else {
        val lastSlash = normalized.lastIndexOf('/')
        if (lastSlash > firstPathSlash) {
          normalized.substring(0, lastSlash + 1)
        } else {
          normalized.substring(0, firstPathSlash + 1)
        }
      }
    }
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
