package com.example.domain

import com.example.model.AirportStation
import com.example.model.DecodedWeather
import com.example.model.FavoriteWeatherSnapshot
import com.example.model.WeatherAlertConfig
import com.example.model.WeatherAlertSeverity
import com.example.model.WeatherChangeAlert
import kotlin.math.abs

object WeatherChangeDetector {

  private val hazardCodes = listOf(
    "TS", "+RA", "+SN", "FZ", "SQ", "FC", "SS", "DS", "VA", "GR", "GS", "FG", "SHRA", "SHSN",
  )

  fun toSnapshot(
    weather: DecodedWeather,
    timestampMillis: Long = System.currentTimeMillis(),
  ): FavoriteWeatherSnapshot {
    return FavoriteWeatherSnapshot(
      icao = weather.stationIcao.trim().uppercase(),
      observationTimeUtc = weather.observationTimeUtc,
      windSpeedKt = weather.wind.speedKt,
      gustKt = weather.wind.gustKt,
      windDirectionDeg = weather.wind.directionDeg,
      visibilityMeters = weather.visibilityMeters,
      ceilingMeters = weather.ceilingMeters,
      weatherPhenomena = weather.weatherPhenomena,
      qnhHpa = weather.qnhHpa,
      rawMetar = weather.rawMetar,
      updatedAtMillis = timestampMillis,
    )
  }

  fun evaluate(
    station: AirportStation,
    previous: FavoriteWeatherSnapshot?,
    current: DecodedWeather,
    config: WeatherAlertConfig,
    nowMillis: Long = System.currentTimeMillis(),
  ): EvaluationResult {
    val nextSnapshot = toSnapshot(current, nowMillis)
    if (!config.enabled || !hasValidObservation(current)) {
      return EvaluationResult(alert = null, snapshot = if (hasValidObservation(current)) nextSnapshot else previous)
    }

    if (previous != null &&
      previous.observationTimeUtc == current.observationTimeUtc &&
      previous.rawMetar.trim() == current.rawMetar.trim()
    ) {
      return EvaluationResult(alert = null, snapshot = nextSnapshot)
    }

    val reasons = mutableListOf<String>()
    var isCritical = false

    val curVis = current.visibilityMeters
    val curCeil = current.ceilingMeters
    val curWindKt = current.wind.speedKt
    val curGustKt = current.wind.gustKt
    val curPhenom = current.weatherPhenomena.trim()
    val hasPhenom = curPhenom.isNotBlank() &&
      !curPhenom.equals("Без явлений", ignoreCase = true) &&
      !curPhenom.equals("Нет данных", ignoreCase = true)
    val rawUpper = current.rawMetar.uppercase()
    val hasSevereToken = hazardCodes.any { token -> rawUpper.contains(token) }

    if (previous == null) {
      // On first observation, alert only if severe/marginal hazardous weather is already present.
      if (hasSevereToken) {
        reasons.add("Опасные явления: ${if (hasPhenom) curPhenom else extractMatchedHazard(rawUpper)}")
        isCritical = true
      }
      if (curVis != null && curVis <= 1500) {
        reasons.add("Низкая видимость: $curVis м")
        isCritical = true
      }
      if (curCeil != null && curCeil <= 200) {
        reasons.add("Низкая ВНГО: $curCeil м")
        isCritical = true
      }
      if (curWindKt >= 25 || (curGustKt != null && curGustKt >= 25)) {
        val gustSuffix = curGustKt?.let { " (порывы $it kt)" }.orEmpty()
        reasons.add("Сильный ветер: $curWindKt kt$gustSuffix")
        isCritical = true
      }
    } else {
      // 1. Wind speed / gust changes
      val windDiff = abs(curWindKt - previous.windSpeedKt)
      val prevGust = previous.gustKt ?: 0
      val curGustVal = curGustKt ?: 0
      if (windDiff >= config.windChangeThresholdKt) {
        val arrow = if (curWindKt > previous.windSpeedKt) "↑" else "↓"
        reasons.add("Ветер $arrow ${previous.windSpeedKt}→$curWindKt kt")
        if (curWindKt >= 25) isCritical = true
      }
      if (curGustKt != null && (curGustKt - prevGust >= config.windChangeThresholdKt || (prevGust == 0 && curGustKt >= 20))) {
        reasons.add("Порывы ветра до $curGustKt kt")
        if (curGustKt >= 25) isCritical = true
      }

      // 2. Wind direction shift at >= 10 kt
      val prevDir = previous.windDirectionDeg
      val curDir = current.wind.directionDeg
      if (prevDir != null && curDir != null && (curWindKt >= 10 || previous.windSpeedKt >= 10)) {
        val rawDiff = abs(curDir - prevDir)
        val angularDiff = minOf(rawDiff, 360 - rawDiff)
        if (angularDiff >= 45) {
          reasons.add("Сдвиг направления ветра ${prevDir.toString().padStart(3, '0')}°→${curDir.toString().padStart(3, '0')}°")
        }
      }

      // 3. Visibility drop / recovery across operational thresholds
      val prevVis = previous.visibilityMeters
      if (curVis != null) {
        if (prevVis != null) {
          val droppedBelowThreshold = prevVis >= config.visibilityThresholdMeters && curVis < config.visibilityThresholdMeters
          val improvedAboveThreshold = prevVis < config.visibilityThresholdMeters && curVis >= config.visibilityThresholdMeters
          val sharpDrop = prevVis - curVis >= 2000 && curVis <= (prevVis * 0.6)
          if (droppedBelowThreshold || sharpDrop) {
            reasons.add("Ухудшение видимости ${prevVis}→$curVis м")
            if (curVis < 1500) isCritical = true
          } else if (improvedAboveThreshold && curVis - prevVis >= 1500) {
            reasons.add("Улучшение видимости ${prevVis}→$curVis м")
          }
        } else if (curVis < config.visibilityThresholdMeters) {
          reasons.add("Ограниченная видимость: $curVis м")
          if (curVis < 1500) isCritical = true
        }
      }

      // 4. Ceiling (ВНГО) drop
      val prevCeil = previous.ceilingMeters
      if (curCeil != null) {
        if (prevCeil != null) {
          val crossedBelow = prevCeil > config.ceilingThresholdMeters && curCeil <= config.ceilingThresholdMeters
          val sharpDrop = prevCeil - curCeil >= 200 && curCeil <= 600
          if (crossedBelow || sharpDrop) {
            reasons.add("Снижение ВНГО ${prevCeil}→$curCeil м")
            if (curCeil <= 150) isCritical = true
          }
        } else if (curCeil <= config.ceilingThresholdMeters) {
          reasons.add("Низкая ВНГО: $curCeil м")
          if (curCeil <= 150) isCritical = true
        }
      }

      // 5. Weather phenomena changes
      val prevPhenom = previous.weatherPhenomena.trim()
      if (hasPhenom && !curPhenom.equals(prevPhenom, ignoreCase = true)) {
        reasons.add("Явления: $curPhenom")
        if (hasSevereToken) isCritical = true
      } else if (!hasPhenom && prevPhenom.isNotBlank() &&
        !prevPhenom.equals("Без явлений", ignoreCase = true) &&
        !prevPhenom.equals("Нет данных", ignoreCase = true)
      ) {
        reasons.add("Прекращение явлений ($prevPhenom)")
      }

      // 6. QNH pressure jump
      val prevQnh = previous.qnhHpa
      val curQnh = current.qnhHpa
      if (prevQnh != null && curQnh != null && abs(curQnh - prevQnh) >= 4) {
        reasons.add("Давление QNH ${prevQnh}→$curQnh гПа")
      }
    }

    if (reasons.isEmpty()) {
      return EvaluationResult(alert = null, snapshot = nextSnapshot)
    }

    val severity = if (isCritical) WeatherAlertSeverity.CRITICAL else WeatherAlertSeverity.WARNING
    val cleanIcao = station.icao.trim().uppercase()
    val title = "${severity.label}: $cleanIcao (${station.city})"
    val summary = reasons.joinToString(" · ")
    val alertId = "${cleanIcao}_${current.observationTimeUtc}_${abs(current.rawMetar.hashCode())}"
    val alert = WeatherChangeAlert(
      id = alertId,
      icao = cleanIcao,
      airportName = station.city.ifBlank { station.name },
      severity = severity,
      title = title,
      summary = summary,
      reasons = reasons,
      observationTimeUtc = current.observationTimeUtc,
      rawMetar = current.rawMetar,
      timestampLocal = current.receivedTimeLocal.ifBlank { current.localObsTime },
      createdAtMillis = nowMillis,
      isRead = false,
    )
    return EvaluationResult(alert = alert, snapshot = nextSnapshot)
  }

  private fun hasValidObservation(weather: DecodedWeather): Boolean {
    return weather.observationTimeUtc != "--:--Z" &&
      !weather.rawMetar.contains("нет данных", ignoreCase = true)
  }

  private fun extractMatchedHazard(rawUpper: String): String {
    return hazardCodes.firstOrNull { rawUpper.contains(it) } ?: "СМУ"
  }

  data class EvaluationResult(
    val alert: WeatherChangeAlert?,
    val snapshot: FavoriteWeatherSnapshot?,
  )
}
