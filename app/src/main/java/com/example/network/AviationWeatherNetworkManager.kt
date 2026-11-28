package com.example.network

import com.example.model.AirportStation
import com.squareup.moshi.Moshi
import java.util.Locale
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * Centralized Networking manager responsible for configuring Retrofit/OkHttp and fetching
 * aviation weather (METAR, TAF, coordinate bounding-box lookups, and 24-hour history) from
 * public APIs.
 */
class AviationWeatherNetworkManager(
  awcBaseUrl: String = DEFAULT_AWC_BASE_URL,
  moshi: Moshi = MeteoServerRetrofitFactory.createMoshi(),
  okHttpClient: OkHttpClient = defaultOkHttpClient(),
) {

  data class PublicWeatherResponse(
    val rawMetar: String,
    val rawTaf: String?,
    val sourceLabel: String,
    val resolvedLat: Double? = null,
    val resolvedLon: Double? = null,
    val resolvedElevMeters: Int? = null,
    val resolvedName: String? = null,
  )

  data class CoordinateMetarCandidate(
    val icao: String,
    val rawMetar: String,
    val rawTaf: String?,
    val stationLat: Double?,
    val stationLon: Double?,
    val elevationMeters: Int?,
    val name: String?,
    val distanceKm: Double,
  )

  data class HistoricalPublicData(
    val metars: List<PublicMetarDto>,
    val tafs: List<PublicTafDto>,
  )

  private val retrofit: Retrofit = Retrofit.Builder()
    .baseUrl(if (awcBaseUrl.endsWith("/")) awcBaseUrl else "$awcBaseUrl/")
    .client(okHttpClient)
    .addConverterFactory(MoshiConverterFactory.create(moshi))
    .build()

  val api: PublicAviationWeatherApi = retrofit.create(PublicAviationWeatherApi::class.java)

  suspend fun fetchLatestWeather(
    icao: String,
    useAviationWeatherGov: Boolean = true,
    useNoaaTgftp: Boolean = true,
    useVatsimMetar: Boolean = true,
    logs: MutableList<String> = mutableListOf(),
  ): PublicWeatherResponse? {
    val cleanIcao = icao.trim().uppercase(Locale.US)
    var rawMetar: String? = null
    var rawTaf: String? = null
    var sourceSite = "интернет"
    var resolvedLat: Double? = null
    var resolvedLon: Double? = null
    var resolvedElev: Int? = null
    var resolvedName: String? = null

    if (useAviationWeatherGov) {
      logs.add("Retrofit GET api/data/metar?ids=$cleanIcao")
      runCatching {
        val resp = api.getMetars(ids = cleanIcao)
        logs.add("aviationweather.gov METAR HTTP ${resp.code()}")
        if (resp.isSuccessful) {
          val first = resp.body().orEmpty().firstOrNull { !it.rawOb.isNullOrBlank() }
          if (first != null) {
            rawMetar = first.rawOb?.trim()
            sourceSite = "интернет (aviationweather.gov)"
            resolvedLat = first.lat
            resolvedLon = first.lon
            resolvedElev = first.elev
            resolvedName = first.name?.takeIf { it.isNotBlank() }
          }
        }
      }.onFailure { err ->
        logs.add("aviationweather.gov ошибка: ${err.message ?: err::class.simpleName}")
      }
    }

    if (rawMetar.isNullOrBlank() && useNoaaTgftp) {
      val noaaUrl = "https://tgftp.nws.noaa.gov/data/observations/metar/stations/$cleanIcao.TXT"
      logs.add("Retrofit GET $noaaUrl")
      runCatching {
        val resp = api.fetchRawTextFeed(noaaUrl)
        logs.add("tgftp.nws.noaa.gov METAR HTTP ${resp.code()}")
        if (resp.isSuccessful) {
          val lines = resp.body()?.string().orEmpty()
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
          val matched = lines.lastOrNull { it.contains(cleanIcao, ignoreCase = true) } ?: lines.lastOrNull()
          if (!matched.isNullOrBlank()) {
            rawMetar = matched
            sourceSite = "интернет (noaa.gov)"
          }
        }
      }.onFailure { err ->
        logs.add("tgftp.nws.noaa.gov ошибка: ${err.message ?: err::class.simpleName}")
      }
    }

    if (rawMetar.isNullOrBlank() && useVatsimMetar) {
      val vatsimUrl = "https://metar.vatsim.net/metar.php?id=$cleanIcao"
      logs.add("Retrofit GET $vatsimUrl")
      runCatching {
        val resp = api.fetchRawTextFeed(vatsimUrl)
        logs.add("metar.vatsim.net HTTP ${resp.code()}")
        if (resp.isSuccessful) {
          val matched = resp.body()?.string().orEmpty()
            .lines()
            .map { it.trim() }
            .firstOrNull { it.contains(cleanIcao, ignoreCase = true) }
          if (!matched.isNullOrBlank()) {
            rawMetar = matched
            sourceSite = "интернет (vatsim.net)"
          }
        }
      }.onFailure { err ->
        logs.add("metar.vatsim.net ошибка: ${err.message ?: err::class.simpleName}")
      }
    }

    if (useAviationWeatherGov) {
      runCatching {
        val resp = api.getTafs(ids = cleanIcao)
        logs.add("aviationweather.gov TAF HTTP ${resp.code()}")
        if (resp.isSuccessful) {
          rawTaf = resp.body().orEmpty().firstOrNull { !it.rawTaf.isNullOrBlank() }?.rawTaf?.trim()
        }
      }
    }

    if (rawTaf.isNullOrBlank() && useNoaaTgftp) {
      val noaaTafUrl = "https://tgftp.nws.noaa.gov/data/forecasts/taf/stations/$cleanIcao.TXT"
      runCatching {
        val resp = api.fetchRawTextFeed(noaaTafUrl)
        if (resp.isSuccessful) {
          val lines = resp.body()?.string().orEmpty()
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.matches(Regex("\\d{4}/\\d{2}/\\d{2}.*")) }
          if (lines.isNotEmpty()) {
            rawTaf = lines.joinToString(" ")
          }
        }
      }
    }

    val finalMetar = rawMetar?.takeIf { it.isNotBlank() } ?: return null
    return PublicWeatherResponse(
      rawMetar = finalMetar,
      rawTaf = rawTaf,
      sourceLabel = sourceSite,
      resolvedLat = resolvedLat,
      resolvedLon = resolvedLon,
      resolvedElevMeters = resolvedElev,
      resolvedName = resolvedName,
    )
  }

  suspend fun fetchLatestTafOnly(
    icao: String,
    useAviationWeatherGov: Boolean = true,
    useNoaaTgftp: Boolean = true,
    logs: MutableList<String> = mutableListOf(),
  ): String? {
    val cleanIcao = icao.trim().uppercase(Locale.US)
    var rawTaf: String? = null

    if (useAviationWeatherGov) {
      runCatching {
        val resp = api.getTafs(ids = cleanIcao)
        logs.add("aviationweather.gov TAF-добор HTTP ${resp.code()}")
        if (resp.isSuccessful) {
          rawTaf = resp.body().orEmpty().firstOrNull { !it.rawTaf.isNullOrBlank() }?.rawTaf?.trim()
        }
      }
    }

    if (rawTaf.isNullOrBlank() && useNoaaTgftp) {
      val noaaTafUrl = "https://tgftp.nws.noaa.gov/data/forecasts/taf/stations/$cleanIcao.TXT"
      runCatching {
        val resp = api.fetchRawTextFeed(noaaTafUrl)
        if (resp.isSuccessful) {
          val lines = resp.body()?.string().orEmpty()
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.matches(Regex("\\d{4}/\\d{2}/\\d{2}.*")) }
          if (lines.isNotEmpty()) {
            rawTaf = lines.joinToString(" ")
          }
        }
      }
    }

    return rawTaf?.takeIf { it.isNotBlank() }
  }

  suspend fun fetchNearestByCoordinates(
    latitude: Double,
    longitude: Double,
    useAviationWeatherGov: Boolean = true,
    logs: MutableList<String> = mutableListOf(),
  ): CoordinateMetarCandidate? {
    if (!useAviationWeatherGov) return null
    val clampedLat = latitude.coerceIn(-89.9, 89.9)
    val clampedLon = longitude.coerceIn(-180.0, 180.0)

    var best: CoordinateMetarCandidate? = null
    for (delta in listOf(1.2, 3.0)) {
      if (best != null) break
      val minLat = (clampedLat - delta).coerceAtLeast(-89.9)
      val maxLat = (clampedLat + delta).coerceAtMost(89.9)
      val minLon = (clampedLon - delta).coerceAtLeast(-180.0)
      val maxLon = (clampedLon + delta).coerceAtMost(180.0)
      val bbox = String.format(Locale.US, "%.3f,%.3f,%.3f,%.3f", minLat, minLon, maxLat, maxLon)
      logs.add("Retrofit GET api/data/metar?bbox=$bbox")
      runCatching {
        val resp = api.getMetarsByBoundingBox(bbox = bbox)
        logs.add("AWC bbox HTTP ${resp.code()}")
        if (resp.isSuccessful) {
          val candidates = resp.body().orEmpty()
          candidates.forEachIndexed { idx, dto ->
            val rawOb = dto.rawOb?.takeIf { it.isNotBlank() } ?: return@forEachIndexed
            val id = dto.icaoId?.trim()?.uppercase(Locale.US)?.takeIf { it.length == 4 } ?: return@forEachIndexed
            val sLat = dto.lat
            val sLon = dto.lon
            val distKm = if (sLat != null && sLon != null) {
              com.example.domain.AirportMapProjection.haversineKm(clampedLat, clampedLon, sLat, sLon).toDouble()
            } else {
              1000.0 + idx
            }
            if (best == null || distKm < best!!.distanceKm) {
              best = CoordinateMetarCandidate(
                icao = id,
                rawMetar = rawOb,
                rawTaf = null,
                stationLat = sLat,
                stationLon = sLon,
                elevationMeters = dto.elev,
                name = dto.name?.takeIf { it.isNotBlank() },
                distanceKm = distKm,
              )
            }
          }
        }
      }.onFailure { err ->
        logs.add("AWC bbox ошибка: ${err.message ?: err::class.simpleName}")
      }
    }

    val matched = best ?: return null
    val tafText = runCatching {
      val tafResp = api.getTafs(ids = matched.icao)
      if (tafResp.isSuccessful) {
        tafResp.body().orEmpty().firstOrNull { !it.rawTaf.isNullOrBlank() }?.rawTaf?.trim()
      } else {
        null
      }
    }.getOrNull()
    return matched.copy(rawTaf = tafText)
  }

  suspend fun fetch24hHistory(
    icao: String,
    useAviationWeatherGov: Boolean = true,
    logs: MutableList<String> = mutableListOf(),
  ): HistoricalPublicData {
    if (!useAviationWeatherGov) return HistoricalPublicData(emptyList(), emptyList())
    val cleanIcao = icao.trim().uppercase(Locale.US)

    val tafs = runCatching {
      logs.add("Retrofit GET api/data/taf?ids=$cleanIcao&hours=24")
      val resp = api.getTafs(ids = cleanIcao, hours = 24)
      if (resp.isSuccessful) resp.body().orEmpty() else emptyList()
    }.getOrDefault(emptyList())

    val metars = runCatching {
      logs.add("Retrofit GET api/data/metar?ids=$cleanIcao&hours=24")
      val resp = api.getMetars(ids = cleanIcao, hours = 24)
      logs.add("aviationweather.gov 24h METAR HTTP ${resp.code()}")
      if (resp.isSuccessful) resp.body().orEmpty() else emptyList()
    }.onFailure { err ->
      logs.add("aviationweather.gov 24ч ошибка: ${err.message ?: err::class.simpleName}")
    }.getOrDefault(emptyList())

    return HistoricalPublicData(metars = metars, tafs = tafs)
  }

  suspend fun fetchActiveSigmets(
    station: AirportStation,
    logs: MutableList<String> = mutableListOf(),
  ): List<com.example.model.SigmetAlert> {
    val all = mutableListOf<com.example.model.SigmetAlert>()
    runCatching {
      logs.add("Retrofit GET api/data/isigmet?format=json")
      val resp = api.getISigmets()
      logs.add("AWC isigmet HTTP ${resp.code()}")
      if (resp.isSuccessful) {
        val body = resp.body()?.string().orEmpty()
        all.addAll(parseSigmetsJson(body, station = station, isAirmet = false))
      }
    }.onFailure { err ->
      logs.add("isigmet ошибка: ${err.message ?: err::class.simpleName}")
    }

    runCatching {
      logs.add("Retrofit GET api/data/airsigmet?format=json")
      val resp = api.getAirSigmets()
      logs.add("AWC airsigmet HTTP ${resp.code()}")
      if (resp.isSuccessful) {
        val body = resp.body()?.string().orEmpty()
        all.addAll(parseSigmetsJson(body, station = station, isAirmet = true))
      }
    }.onFailure { err ->
      logs.add("airsigmet ошибка: ${err.message ?: err::class.simpleName}")
    }

    return all
      .distinctBy { "${it.icaoId}_${it.validFromUtc}_${it.rawText.take(64)}" }
      .sortedWith(compareBy({ it.distanceKm ?: Int.MAX_VALUE }, { !it.hazard.isSevere }))
      .take(25)
  }

  companion object {
    const val DEFAULT_AWC_BASE_URL = "https://aviationweather.gov/"

    fun parseSigmetsJson(
      jsonText: String,
      station: AirportStation,
      isAirmet: Boolean = false,
      maxDistanceKm: Double = 1400.0,
    ): List<com.example.model.SigmetAlert> {
      val trimmed = jsonText.trim()
      if (trimmed.isBlank() || trimmed == "[]") return emptyList()
      val array = runCatching {
        if (trimmed.startsWith("{")) {
          org.json.JSONObject(trimmed).optJSONArray("features") ?: org.json.JSONArray()
        } else {
          org.json.JSONArray(trimmed)
        }
      }.getOrNull() ?: return emptyList()

      val stationIcao = station.icao.trim().uppercase(Locale.US)
      val stationPrefix2 = stationIcao.take(2)
      val utcFmt = java.text.SimpleDateFormat("dd HH:mm'Z'", Locale.US).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
      }
      val out = mutableListOf<com.example.model.SigmetAlert>()

      for (i in 0 until array.length()) {
        val item = array.optJSONObject(i) ?: continue
        val props = item.optJSONObject("properties") ?: item
        val raw = (props.optString("rawSigmet", "").ifBlank {
          props.optString("rawAirSigmet", "").ifBlank {
            props.optString("rawText", "")
          }
        }).trim()
        if (raw.isBlank()) continue

        val icaoId = props.optString("icaoId", "").trim().uppercase(Locale.US)
          .ifBlank { props.optString("airSigmetType", if (isAirmet) "AIRSIG" else "SIGMET") }
        val firName = props.optString("firName", "").ifBlank {
          props.optString("firId", "")
        }.trim()
        val qualifier = props.optString("qualifier", "").trim()
        val hazardRaw = props.optString("hazard", "").ifBlank {
          props.optString("sigmetType", "")
        }
        val hazard = com.example.model.SigmetHazardType.fromRaw("$hazardRaw $raw")

        val validFromEpoch = props.optLong("validTimeFrom", 0L)
        val validToEpoch = props.optLong("validTimeTo", 0L)
        val fromLabel = if (validFromEpoch > 0L) utcFmt.format(java.util.Date(validFromEpoch * 1000L)) else "--:--Z"
        val toLabel = if (validToEpoch > 0L) utcFmt.format(java.util.Date(validToEpoch * 1000L)) else "--:--Z"

        val baseFt = props.optInt("altitudeLow1", Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }
          ?: props.optInt("base", Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }
        val topFt = props.optInt("altitudeHi1", Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }
          ?: props.optInt("top", Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }

        val minDistKm = extractMinDistanceKm(item, props, station.latitude, station.longitude)
        val textMentionsStation = raw.contains(stationIcao, ignoreCase = true) ||
          firName.contains(stationIcao, ignoreCase = true) ||
          (stationPrefix2.length == 2 && icaoId.startsWith(stationPrefix2))
        val isNear = minDistKm != null && minDistKm <= maxDistanceKm

        if (textMentionsStation || isNear) {
          out.add(
            com.example.model.SigmetAlert(
              id = props.optString("seriesId", "").ifBlank { "${icaoId}_$i" },
              icaoId = icaoId,
              firName = firName,
              qualifier = qualifier,
              hazard = hazard,
              rawText = raw,
              validFromUtc = fromLabel,
              validToUtc = toLabel,
              minAltitudeFt = baseFt,
              maxAltitudeFt = topFt,
              distanceKm = minDistKm?.toInt(),
              isAirmet = isAirmet || qualifier.equals("AIRMET", ignoreCase = true),
            ),
          )
        }
      }
      return out
    }

    private fun extractMinDistanceKm(
      item: org.json.JSONObject,
      props: org.json.JSONObject,
      targetLat: Double,
      targetLon: Double,
    ): Double? {
      var minKm: Double? = null
      fun considerPoint(lat: Double, lon: Double) {
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return
        val d = com.example.domain.AirportMapProjection.haversineKm(targetLat, targetLon, lat, lon).toDouble()
        if (minKm == null || d < minKm!!) {
          minKm = d
        }
      }

      // 1. AWC JSON "coords": [{"lat": ..., "lon": ...}]
      val coordsArr = props.optJSONArray("coords") ?: item.optJSONArray("coords")
      if (coordsArr != null) {
        for (j in 0 until coordsArr.length()) {
          val pt = coordsArr.optJSONObject(j) ?: continue
          val lat = pt.optDouble("lat", Double.NaN)
          val lon = pt.optDouble("lon", Double.NaN)
          if (!lat.isNaN() && !lon.isNaN()) considerPoint(lat, lon)
        }
      }

      // 2. Optional GeoJSON geometry.coordinates [[[lon, lat], ...]]
      val geom = item.optJSONObject("geometry")
      val geomCoords = geom?.optJSONArray("coordinates")
      if (geomCoords != null) {
        fun walk(arr: org.json.JSONArray) {
          if (arr.length() >= 2 && arr.opt(0) is Number && arr.opt(1) is Number) {
            considerPoint(arr.optDouble(1), arr.optDouble(0))
          } else {
            for (k in 0 until arr.length()) {
              val child = arr.optJSONArray(k) ?: continue
              walk(child)
            }
          }
        }
        walk(geomCoords)
      }
      return minKm
    }

    fun defaultOkHttpClient(): OkHttpClient {
      val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
      }
      return OkHttpClient.Builder()
        .addInterceptor { chain ->
          val req = chain.request().newBuilder()
            .header("User-Agent", "Meteotablo/1.0")
            .build()
          chain.proceed(req)
        }
        .addInterceptor(logging)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()
    }
  }
}
