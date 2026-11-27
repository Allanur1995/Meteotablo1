package com.example.domain

import com.example.model.AirportStation
import com.example.model.DecodedWeather
import com.example.model.PressureUnit
import com.example.model.WeatherHistoryEntry
import com.example.model.WindSpeedUnit
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.roundToInt

data class D3TrendChartPoint(
  val observedAtMillis: Long,
  val observationTimeUtc: String,
  val windDisplay: Double,
  val gustDisplay: Double?,
  val pressureDisplay: Double?,
  val visibilityMeters: Int? = null,
  val isCavok: Boolean = false,
) {
  val visibilityKm: Double?
    get() = visibilityMeters?.let { (it / 100.0).roundToInt() / 10.0 }
}

data class D3TrendsDashboardModel(
  val icao: String,
  val city: String,
  val windUnit: WindSpeedUnit,
  val pressureUnit: PressureUnit,
  val points: List<D3TrendChartPoint>,
  val minWindDisplay: Double?,
  val maxWindDisplay: Double?,
  val avgWindDisplay: Double?,
  val maxGustDisplay: Double?,
  val minPressureDisplay: Double?,
  val maxPressureDisplay: Double?,
  val latestPressureDisplay: Double?,
  val deltaPressureDisplay: Double?,
  val minVisibilityMeters: Int? = null,
  val maxVisibilityMeters: Int? = null,
  val latestVisibilityMeters: Int? = null,
) {
  fun toD3JsonPayload(
    darkTheme: Boolean,
    mode: String = "WIND_VIS",
  ): String {
    val pressureDecimals = if (pressureUnit == PressureUnit.INHG) 2 else 0
    val arr = JSONArray()
    points.forEach { pt ->
      arr.put(
        JSONObject().apply {
          put("ts", pt.observedAtMillis)
          put("timeUtc", pt.observationTimeUtc)
          put("wind", pt.windDisplay)
          if (pt.gustDisplay != null) {
            put("gust", pt.gustDisplay)
          } else {
            put("gust", JSONObject.NULL)
          }
          if (pt.pressureDisplay != null) {
            put("qnh", pt.pressureDisplay)
          } else {
            put("qnh", JSONObject.NULL)
          }
          if (pt.visibilityMeters != null) {
            put("visMeters", pt.visibilityMeters)
            put("visKm", pt.visibilityKm)
          } else {
            put("visMeters", JSONObject.NULL)
            put("visKm", JSONObject.NULL)
          }
          put("cavok", pt.isCavok)
        },
      )
    }
    return JSONObject().apply {
      put("icao", icao)
      put("city", city)
      put("mode", mode)
      put("windUnit", windUnit.label)
      put("pressureUnit", pressureUnit.label)
      put("pressureDecimals", pressureDecimals)
      put("darkTheme", darkTheme)
      put("points", arr)
    }.toString()
  }

  fun formattedWindRange(): String {
    if (minWindDisplay == null || maxWindDisplay == null) return "--"
    val base = "${minWindDisplay.roundToInt()}–${maxWindDisplay.roundToInt()} ${windUnit.label}"
    val gustPart = maxGustDisplay?.let { " · G${it.roundToInt()}" }.orEmpty()
    return "$base$gustPart"
  }

  fun formattedPressureRange(): String {
    if (minPressureDisplay == null || maxPressureDisplay == null) return "--"
    return if (pressureUnit == PressureUnit.INHG) {
      String.format(Locale.US, "%.2f–%.2f %s", minPressureDisplay, maxPressureDisplay, pressureUnit.label)
    } else {
      "${minPressureDisplay.roundToInt()}–${maxPressureDisplay.roundToInt()} ${pressureUnit.label}"
    }
  }

  fun formattedPressureDelta(): String {
    val d = deltaPressureDisplay ?: return "—"
    return if (pressureUnit == PressureUnit.INHG) {
      val sign = if (d > 0.004) "+" else ""
      String.format(Locale.US, "%s%.2f дм", sign, d)
    } else {
      val rounded = d.roundToInt()
      val sign = if (rounded > 0) "+" else ""
      "$sign$rounded гПа"
    }
  }

  fun formattedVisibilityRange(): String {
    val minV = minVisibilityMeters ?: return "--"
    val maxV = maxVisibilityMeters ?: return "--"
    return if (minV == maxV) {
      formatVisibility(minV)
    } else {
      "${formatVisibility(minV)}–${formatVisibility(maxV)}"
    }
  }

  companion object {
    fun formatVisibility(meters: Int?): String {
      if (meters == null) return "--"
      return when {
        meters >= 10000 -> "≥10 км"
        meters >= 1000 -> String.format(Locale.US, "%.1f км", meters / 1000.0)
        else -> "$meters м"
      }
    }
  }
}

object D3TrendsChartDataBuilder {

  fun build(
    station: AirportStation,
    historyEntries: List<WeatherHistoryEntry>,
    liveFallbackWeather: DecodedWeather?,
    windUnit: WindSpeedUnit,
    pressureUnit: PressureUnit,
    referenceNowMillis: Long = System.currentTimeMillis(),
  ): D3TrendsDashboardModel {
    val cutoff = referenceNowMillis - WeatherHistoryAnalyzer.WINDOW_24H_MILLIS
    val effectiveEntries = buildList {
      addAll(historyEntries.filter { it.observedAtMillis >= cutoff })
      if (liveFallbackWeather != null && WeatherHistoryAnalyzer.isRecordable(liveFallbackWeather)) {
        val liveObsMs = WeatherHistoryAnalyzer.resolveObservationMillis(liveFallbackWeather.rawMetar, referenceNowMillis)
        val liveEntry = WeatherHistoryAnalyzer.fromDecodedWeather(liveFallbackWeather, liveObsMs)
        val alreadyPresent = any {
          it.observationTimeUtc == liveEntry.observationTimeUtc &&
            it.rawMetar.trim() == liveEntry.rawMetar.trim()
        }
        if (!alreadyPresent && liveEntry.observedAtMillis >= cutoff) {
          add(liveEntry)
        }
      }
    }.sortedBy { it.observedAtMillis }

    val chartPoints = effectiveEntries.map { entry ->
      val windVal = UnitConverter.convertWindFromKnots(entry.windSpeedKt.toDouble(), windUnit)
      val gustVal = entry.gustKt?.let { UnitConverter.convertWindFromKnots(it.toDouble(), windUnit) }
      val qnhVal = when (pressureUnit) {
        PressureUnit.HPA -> entry.qnhHpa?.toDouble()
          ?: entry.qnhInHg?.let { UnitConverter.convertInHgToHpa(it) }
        PressureUnit.INHG -> entry.qnhInHg
          ?: entry.qnhHpa?.let { UnitConverter.convertHpaToInHg(it) }
      }
      val resolvedVisMeters = when {
        entry.isCavok -> 10000
        entry.visibilityMeters != null -> entry.visibilityMeters.coerceIn(0, 10000)
        else -> null
      }
      D3TrendChartPoint(
        observedAtMillis = entry.observedAtMillis,
        observationTimeUtc = entry.observationTimeUtc,
        windDisplay = (windVal * 10.0).roundToInt() / 10.0,
        gustDisplay = gustVal?.let { (it * 10.0).roundToInt() / 10.0 },
        pressureDisplay = qnhVal?.let {
          if (pressureUnit == PressureUnit.INHG) (it * 100.0).roundToInt() / 100.0 else it.roundToInt().toDouble()
        },
        visibilityMeters = resolvedVisMeters,
        isCavok = entry.isCavok,
      )
    }

    val windSeries = chartPoints.map { it.windDisplay }
    val gustSeries = chartPoints.mapNotNull { it.gustDisplay }
    val pressureSeries = chartPoints.mapNotNull { it.pressureDisplay }
    val visibilitySeries = chartPoints.mapNotNull { it.visibilityMeters }
    val pressureDelta = if (pressureSeries.size >= 2) {
      pressureSeries.last() - pressureSeries.first()
    } else if (pressureSeries.size == 1) {
      0.0
    } else {
      null
    }

    return D3TrendsDashboardModel(
      icao = station.icao.trim().uppercase(),
      city = station.city.ifBlank { station.name },
      windUnit = windUnit,
      pressureUnit = pressureUnit,
      points = chartPoints,
      minWindDisplay = windSeries.minOrNull(),
      maxWindDisplay = windSeries.maxOrNull(),
      avgWindDisplay = if (windSeries.isNotEmpty()) windSeries.average() else null,
      maxGustDisplay = gustSeries.maxOrNull(),
      minPressureDisplay = pressureSeries.minOrNull(),
      maxPressureDisplay = pressureSeries.maxOrNull(),
      latestPressureDisplay = pressureSeries.lastOrNull(),
      deltaPressureDisplay = pressureDelta,
      minVisibilityMeters = visibilitySeries.minOrNull(),
      maxVisibilityMeters = visibilitySeries.maxOrNull(),
      latestVisibilityMeters = visibilitySeries.lastOrNull(),
    )
  }
}
