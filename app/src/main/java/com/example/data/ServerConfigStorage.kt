package com.example.data

enum class DataSourceMode(
  val shortLabel: String,
  val displayName: String,
) {
  AUTO_IP_FIRST("IP→Инет", "Авто: IP → Интернет"),
  AUTO_INTERNET_FIRST("Инет→IP", "Авто: Интернет → IP"),
  IP_ONLY("Только IP", "Только IP-сервер"),
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
    const val DEFAULT_SERVER_URL = "http://217.174.229.206:8181/"
    const val DEFAULT_USERNAME = "meteo"
    const val DEFAULT_PASSWORD = "meteo"

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
      return if (withScheme.endsWith("/")) withScheme else "$withScheme/"
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
