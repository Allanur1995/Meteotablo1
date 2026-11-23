package com.example.domain

import com.example.model.AirportStation
import com.example.model.DecodedWeather
import com.example.model.QnhHistoryPoint
import com.example.model.QnhTrendSummary
import com.example.model.WeatherHistoryEntry
import java.util.Calendar
import java.util.TimeZone

object WeatherHistoryAnalyzer {

  const val WINDOW_24H_MILLIS = 24L * 60L * 60L * 1000L
  const val MAX_ENTRIES_PER_STATION = 96

  private val metarDayTimeRegex = Regex("\\b(\\d{2})(\\d{2})(\\d{2})Z\\b")

  fun isRecordable(weather: DecodedWeather): Boolean {
    return weather.observationTimeUtc != "--:--Z" &&
      !weather.rawMetar.contains("нет данных", ignoreCase = true)
  }

  fun resolveObservationMillis(
    rawMetar: String,
    referenceNowMillis: Long = System.currentTimeMillis(),
  ): Long {
    val m = metarDayTimeRegex.find(rawMetar) ?: return referenceNowMillis
    val day = m.groupValues[1].toIntOrNull() ?: return referenceNowMillis
    val hour = m.groupValues[2].toIntOrNull() ?: return referenceNowMillis
    val minute = m.groupValues[3].toIntOrNull() ?: return referenceNowMillis
    if (day !in 1..31 || hour !in 0..23 || minute !in 0..59) return referenceNowMillis

    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
      timeInMillis = referenceNowMillis
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
      val maxDay = getActualMaximum(Calendar.DAY_OF_MONTH)
      if (day <= maxDay) {
        set(Calendar.DAY_OF_MONTH, day)
      } else {
        add(Calendar.MONTH, -1)
        set(Calendar.DAY_OF_MONTH, day.coerceAtMost(getActualMaximum(Calendar.DAY_OF_MONTH)))
      }
      set(Calendar.HOUR_OF_DAY, hour)
      set(Calendar.MINUTE, minute)
    }

    // Handle month rollover (e.g. ref = 1st of month, obs = 30th/31st of previous month)
    val twelveHours = 12L * 3600_000L
    if (cal.timeInMillis - referenceNowMillis > twelveHours) {
      cal.add(Calendar.MONTH, -1)
      val prevMaxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
      cal.set(Calendar.DAY_OF_MONTH, day.coerceAtMost(prevMaxDay))
    }
    return cal.timeInMillis
  }

  fun fromDecodedWeather(
    weather: DecodedWeather,
    observedAtMillis: Long = resolveObservationMillis(weather.rawMetar),
  ): WeatherHistoryEntry {
    val category = AirportMapProjection.evaluateFlightCategory(weather)
    return WeatherHistoryEntry(
      icao = weather.stationIcao.trim().uppercase(),
      observationTimeUtc = weather.observationTimeUtc,
      observedAtMillis = observedAtMillis,
      windSpeedKt = weather.wind.speedKt.coerceAtLeast(0),
      gustKt = weather.wind.gustKt?.coerceAtLeast(0),
      windDirectionDeg = weather.wind.directionDeg,
      visibilityMeters = weather.visibilityMeters,
      isCavok = weather.isCavok,
      ceilingFeet = weather.ceilingFeet,
      temperatureC = weather.temperatureC,
      dewPointC = weather.dewPointC,
      qnhHpa = weather.qnhHpa,
      qnhInHg = weather.qnhInHg,
      flightCategoryCode = category.code,
      rawMetar = weather.rawMetar.trim(),
      rawTaf = weather.rawTaf.trim(),
      sourceLabel = weather.sourceLabel,
    )
  }

  fun parseHistoricalMetar(
    rawMetar: String,
    rawTaf: String?,
    station: AirportStation,
    sourceLabel: String,
    explicitTimestampMillis: Long? = null,
    referenceNowMillis: Long = System.currentTimeMillis(),
  ): WeatherHistoryEntry? {
    val cleanMetar = rawMetar.trim().replace(Regex("\\s+"), " ")
    if (cleanMetar.isBlank()) return null
    val parsed = AviationWeatherParser.parse(
      rawMetarInput = cleanMetar,
      rawTafInput = rawTaf?.takeIf { it.isNotBlank() } ?: "TAF ${station.icao} — нет данных",
      station = station,
      sourceLabel = sourceLabel,
    )
    if (!isRecordable(parsed)) return null
    val obsMillis = explicitTimestampMillis ?: resolveObservationMillis(cleanMetar, referenceNowMillis)
    return fromDecodedWeather(parsed, obsMillis)
  }

  fun appendObservation(
    existing: List<WeatherHistoryEntry>,
    weather: DecodedWeather,
    referenceNowMillis: Long = System.currentTimeMillis(),
    explicitObservedAtMillis: Long? = null,
    maxEntries: Int = MAX_ENTRIES_PER_STATION,
  ): List<WeatherHistoryEntry> {
    if (!isRecordable(weather)) return existing
    val obsMillis = explicitObservedAtMillis ?: resolveObservationMillis(weather.rawMetar, referenceNowMillis)
    val entry = fromDecodedWeather(weather, obsMillis)
    return mergeHistory(
      existing = existing,
      incoming = listOf(entry),
      referenceNowMillis = referenceNowMillis,
      maxEntries = maxEntries,
    )
  }

  fun mergeHistory(
    existing: List<WeatherHistoryEntry>,
    incoming: List<WeatherHistoryEntry>,
    referenceNowMillis: Long = System.currentTimeMillis(),
    maxEntries: Int = MAX_ENTRIES_PER_STATION,
  ): List<WeatherHistoryEntry> {
    val cutoff = referenceNowMillis - WINDOW_24H_MILLIS
    val combined = LinkedHashMap<String, WeatherHistoryEntry>()

    fun putOrMerge(item: WeatherHistoryEntry) {
      if (item.observedAtMillis < cutoff) return
      val dedupKey = "${item.observationTimeUtc}|${item.rawMetar.trim()}"
      val prev = combined[dedupKey]
      if (prev == null) {
        combined[dedupKey] = item
      } else {
        val mergedTaf = when {
          !item.rawTaf.contains("нет данных", ignoreCase = true) && item.rawTaf.isNotBlank() -> item.rawTaf
          else -> prev.rawTaf
        }
        combined[dedupKey] = item.copy(
          rawTaf = mergedTaf,
          observedAtMillis = maxOf(prev.observedAtMillis, item.observedAtMillis),
        )
      }
    }

    existing.forEach(::putOrMerge)
    incoming.forEach(::putOrMerge)

    return combined.values
      .sortedBy { it.observedAtMillis }
      .takeLast(maxEntries.coerceAtLeast(10))
  }

  fun buildQnhTrend(
    icao: String,
    entries: List<WeatherHistoryEntry>,
    referenceNowMillis: Long = System.currentTimeMillis(),
  ): QnhTrendSummary {
    val cleanIcao = icao.trim().uppercase()
    val cutoff = referenceNowMillis - WINDOW_24H_MILLIS
    val points = entries
      .filter { it.qnhHpa != null && it.observedAtMillis >= cutoff }
      .sortedBy { it.observedAtMillis }
      .mapNotNull { entry ->
        val hpa = entry.qnhHpa ?: return@mapNotNull null
        val inHg = entry.qnhInHg ?: (hpa * 0.029530)
        QnhHistoryPoint(
          observationTimeUtc = entry.observationTimeUtc,
          observedAtMillis = entry.observedAtMillis,
          qnhHpa = hpa,
          qnhInHg = inHg,
          rawMetar = entry.rawMetar,
        )
      }

    if (points.isEmpty()) {
      return QnhTrendSummary(
        icao = cleanIcao,
        points = emptyList(),
        minHpa = null,
        maxHpa = null,
        latestHpa = null,
        delta24hHpa = null,
      )
    }

    val minHpa = points.minOf { it.qnhHpa }
    val maxHpa = points.maxOf { it.qnhHpa }
    val latestHpa = points.last().qnhHpa
    val delta = if (points.size >= 2) latestHpa - points.first().qnhHpa else 0
    return QnhTrendSummary(
      icao = cleanIcao,
      points = points,
      minHpa = minHpa,
      maxHpa = maxHpa,
      latestHpa = latestHpa,
      delta24hHpa = delta,
    )
  }
}
