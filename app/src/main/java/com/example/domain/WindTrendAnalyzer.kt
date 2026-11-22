package com.example.domain

import com.example.model.DecodedWeather
import com.example.model.WindHistoryEntry
import com.example.model.WindTrendDirection
import com.example.model.WindTrendSummary
import kotlin.math.abs

object WindTrendAnalyzer {

  const val DEFAULT_MAX_ENTRIES = 12
  private const val TREND_THRESHOLD_KT = 2

  fun isRecordable(weather: DecodedWeather): Boolean {
    return weather.observationTimeUtc != "--:--Z" &&
      !weather.rawMetar.contains("нет данных", ignoreCase = true)
  }

  fun appendObservation(
    existing: List<WindHistoryEntry>,
    weather: DecodedWeather,
    nowMillis: Long = System.currentTimeMillis(),
    maxEntries: Int = DEFAULT_MAX_ENTRIES,
  ): List<WindHistoryEntry> {
    if (!isRecordable(weather)) return existing

    val next = WindHistoryEntry(
      observationTimeUtc = weather.observationTimeUtc,
      speedKt = weather.wind.speedKt.coerceAtLeast(0),
      gustKt = weather.wind.gustKt?.coerceAtLeast(0),
      directionDeg = weather.wind.directionDeg,
      rawMetar = weather.rawMetar,
      recordedAtMillis = nowMillis,
    )

    val last = existing.lastOrNull()
    if (last != null &&
      last.observationTimeUtc == next.observationTimeUtc &&
      last.speedKt == next.speedKt &&
      last.gustKt == next.gustKt &&
      last.directionDeg == next.directionDeg
    ) {
      return existing
    }

    return (existing + next).takeLast(maxEntries.coerceAtLeast(2))
  }

  fun analyze(
    icao: String,
    entries: List<WindHistoryEntry>,
  ): WindTrendSummary {
    val cleanIcao = icao.trim().uppercase()
    if (entries.isEmpty()) {
      return WindTrendSummary(
        icao = cleanIcao,
        direction = WindTrendDirection.INSUFFICIENT,
        deltaKt = 0,
        currentSpeedKt = 0,
        previousSpeedKt = null,
        minSpeedKt = 0,
        maxSpeedKt = 0,
        entries = emptyList(),
      )
    }

    val current = entries.last()
    val minSpeed = entries.minOf { it.speedKt }
    val maxSpeed = entries.maxOf { it.speedKt }

    if (entries.size == 1) {
      return WindTrendSummary(
        icao = cleanIcao,
        direction = WindTrendDirection.INSUFFICIENT,
        deltaKt = 0,
        currentSpeedKt = current.speedKt,
        previousSpeedKt = null,
        minSpeedKt = minSpeed,
        maxSpeedKt = maxSpeed,
        entries = entries,
      )
    }

    val previous = entries[entries.lastIndex - 1]
    val stepDelta = current.speedKt - previous.speedKt
    val effectiveDelta = if (abs(stepDelta) >= TREND_THRESHOLD_KT || entries.size < 3) {
      stepDelta
    } else {
      val twoStepDelta = current.speedKt - entries[entries.lastIndex - 2].speedKt
      if (abs(twoStepDelta) >= TREND_THRESHOLD_KT) twoStepDelta else stepDelta
    }

    val direction = when {
      effectiveDelta >= TREND_THRESHOLD_KT -> WindTrendDirection.INCREASING
      effectiveDelta <= -TREND_THRESHOLD_KT -> WindTrendDirection.DECREASING
      else -> WindTrendDirection.STEADY
    }

    return WindTrendSummary(
      icao = cleanIcao,
      direction = direction,
      deltaKt = effectiveDelta,
      currentSpeedKt = current.speedKt,
      previousSpeedKt = previous.speedKt,
      minSpeedKt = minSpeed,
      maxSpeedKt = maxSpeed,
      entries = entries,
    )
  }
}
