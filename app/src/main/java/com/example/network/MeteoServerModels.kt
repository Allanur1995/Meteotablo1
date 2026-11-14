package com.example.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.Locale

@JsonClass(generateAdapter = true)
data class MeteoLoginRequest(
  @Json(name = "username") val username: String,
  @Json(name = "password") val password: String,
)

@JsonClass(generateAdapter = true)
data class MeteoLoginResponse(
  @Json(name = "success") val success: Boolean? = null,
  @Json(name = "token") val token: String? = null,
  @Json(name = "message") val message: String? = null,
  @Json(name = "error") val error: String? = null,
)

@JsonClass(generateAdapter = true)
data class ServerStationWeatherDto(
  @Json(name = "icao") val icao: String? = null,
  @Json(name = "icaoId") val icaoId: String? = null,
  @Json(name = "station") val station: String? = null,
  @Json(name = "name") val name: String? = null,
  @Json(name = "rawOb") val rawOb: String? = null,
  @Json(name = "rawMetar") val rawMetar: String? = null,
  @Json(name = "metar") val metar: String? = null,
  @Json(name = "rawTAF") val rawTafCamel: String? = null,
  @Json(name = "rawTaf") val rawTaf: String? = null,
  @Json(name = "taf") val taf: String? = null,
  @Json(name = "windDir") val windDirDeg: Int? = null,
  @Json(name = "windSpeed") val windSpeedKt: Int? = null,
  @Json(name = "windGust") val windGustKt: Int? = null,
  @Json(name = "visibility") val visibilityMeters: Int? = null,
  @Json(name = "qnh") val qnhHpa: Int? = null,
  @Json(name = "qfe") val qfeHpa: Int? = null,
  @Json(name = "temp") val temperatureC: Int? = null,
  @Json(name = "dewPoint") val dewPointC: Int? = null,
  @Json(name = "ceiling") val ceilingMeters: Int? = null,
  @Json(name = "obsTime") val obsTimeUtc: String? = null,
  @Json(name = "updatedAt") val updatedAt: String? = null,
) {
  fun resolvedIcao(): String? =
    (icao ?: icaoId ?: station)?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }

  fun resolvedMetar(): String? {
    val explicit = listOf(rawOb, rawMetar, metar)
      .firstOrNull { !it.isNullOrBlank() }
      ?.trim()
    if (!explicit.isNullOrBlank()) return explicit
    return toSyntheticMetar()
  }

  fun resolvedTaf(): String? =
    listOf(rawTafCamel, rawTaf, taf)
      .firstOrNull { !it.isNullOrBlank() }
      ?.trim()

  private fun toSyntheticMetar(): String? {
    val code = resolvedIcao() ?: return null
    val hasTelemetry = windSpeedKt != null || visibilityMeters != null || qnhHpa != null || temperatureC != null
    if (!hasTelemetry) return null

    val timeToken = obsTimeUtc?.filter { it.isDigit() }?.take(6)?.let { "${it}Z" } ?: "000000Z"
    val windToken = buildString {
      val dir = windDirDeg?.let { String.format(Locale.US, "%03d", ((it % 360) + 360) % 360) } ?: "VRB"
      val spd = String.format(Locale.US, "%02d", (windSpeedKt ?: 0).coerceAtLeast(0))
      append(dir).append(spd)
      if (windGustKt != null && windGustKt > 0) {
        append("G").append(String.format(Locale.US, "%02d", windGustKt))
      }
      append("KT")
    }
    val visToken = visibilityMeters?.coerceIn(0, 9999)?.let {
      String.format(Locale.US, "%04d", it)
    } ?: "9999"
    val tempToken = if (temperatureC != null && dewPointC != null) {
      "${formatMetarTemp(temperatureC)}/${formatMetarTemp(dewPointC)}"
    } else {
      null
    }
    val qnhToken = qnhHpa?.let { String.format(Locale.US, "Q%04d", it.coerceIn(800, 1100)) }

    return listOfNotNull("METAR", code, timeToken, windToken, visToken, tempToken, qnhToken, "NOSIG")
      .joinToString(" ")
  }

  private fun formatMetarTemp(value: Int): String {
    val absVal = kotlin.math.abs(value)
    val digits = String.format(Locale.US, "%02d", absVal)
    return if (value < 0) "M$digits" else digits
  }
}

@JsonClass(generateAdapter = true)
data class ServerWeatherEnvelopeDto(
  @Json(name = "station") val station: ServerStationWeatherDto? = null,
  @Json(name = "data") val data: ServerStationWeatherDto? = null,
  @Json(name = "weather") val weather: ServerStationWeatherDto? = null,
  @Json(name = "stations") val stations: List<ServerStationWeatherDto>? = null,
  @Json(name = "items") val items: List<ServerStationWeatherDto>? = null,
  @Json(name = "icao") val icao: String? = null,
  @Json(name = "rawOb") val rawOb: String? = null,
  @Json(name = "rawMetar") val rawMetar: String? = null,
  @Json(name = "metar") val metar: String? = null,
  @Json(name = "rawTAF") val rawTafCamel: String? = null,
  @Json(name = "rawTaf") val rawTaf: String? = null,
  @Json(name = "taf") val taf: String? = null,
) {
  fun findForStation(targetIcao: String): ServerStationWeatherDto? {
    val clean = targetIcao.trim().uppercase()
    val candidates = buildList {
      station?.let(::add)
      data?.let(::add)
      weather?.let(::add)
      stations?.let(::addAll)
      items?.let(::addAll)
    }
    val exact = candidates.firstOrNull { it.resolvedIcao() == clean }
    if (exact != null) return exact

    val flat = ServerStationWeatherDto(
      icao = icao,
      rawOb = rawOb,
      rawMetar = rawMetar,
      metar = metar,
      rawTafCamel = rawTafCamel,
      rawTaf = rawTaf,
      taf = taf,
    )
    if (flat.resolvedMetar() != null || flat.resolvedTaf() != null) return flat
    return candidates.firstOrNull { it.resolvedIcao() == null && it.resolvedMetar() != null }
  }
}
