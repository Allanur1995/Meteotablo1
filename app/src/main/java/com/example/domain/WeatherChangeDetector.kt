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

  data class VfrMinimumStatus(
    val icao: String,
    val isBelowVfr: Boolean,
    val visibilityBelow: Boolean,
    val ceilingBelow: Boolean,
    val windAbove: Boolean,
    val currentVisMeters: Int?,
    val currentCeilFeet: Int?,
    val currentPeakWindKt: Int,
    val breachReasons: List<String>,
  )

  fun evaluateVfrMinimums(
    weather: DecodedWeather,
    config: WeatherAlertConfig,
  ): VfrMinimumStatus {
    val curVis = if (weather.isCavok) 9999 else weather.visibilityMeters
    val curCeilFt = when {
      weather.isCavok -> null
      weather.ceilingFeet != null -> weather.ceilingFeet
      weather.ceilingMeters != null -> kotlin.math.round(weather.ceilingMeters * 3.28084).toInt()
      else -> null
    }
    val peakWindKt = maxOf(weather.wind.speedKt, weather.wind.gustKt ?: 0)

    val visBelow = !weather.isCavok && curVis != null && curVis < config.vfrMinVisibilityMeters
    val ceilBelow = !weather.isCavok && curCeilFt != null && curCeilFt < config.vfrMinCeilingFeet
    val windAbove = peakWindKt > config.vfrMaxWindKt

    val reasons = buildList {
      if (visBelow && curVis != null) {
        add("Ниже мин. ПВП (VFR): видимость $curVis м (< ${config.vfrMinVisibilityMeters} м)")
      }
      if (ceilBelow && curCeilFt != null) {
        add("Ниже мин. ПВП (VFR): ВНГО $curCeilFt ft (< ${config.vfrMinCeilingFeet} ft)")
      }
      if (windAbove) {
        val gustSuffix = weather.wind.gustKt?.let { " (порывы $it kt)" }.orEmpty()
        add("Ветер выше ПВП (VFR): ${weather.wind.speedKt} kt$gustSuffix > ${config.vfrMaxWindKt} kt")
      }
    }

    return VfrMinimumStatus(
      icao = weather.stationIcao.trim().uppercase(),
      isBelowVfr = reasons.isNotEmpty(),
      visibilityBelow = visBelow,
      ceilingBelow = ceilBelow,
      windAbove = windAbove,
      currentVisMeters = curVis,
      currentCeilFeet = curCeilFt,
      currentPeakWindKt = peakWindKt,
      breachReasons = reasons,
    )
  }

  private fun evaluateSnapshotVfr(
    snapshot: FavoriteWeatherSnapshot,
    config: WeatherAlertConfig,
  ): VfrMinimumStatus {
    val rawUpper = snapshot.rawMetar.uppercase()
    val isCavok = rawUpper.contains("CAVOK")
    val vis = if (isCavok) 9999 else snapshot.visibilityMeters
    val ceilFt = if (isCavok) null else snapshot.ceilingMeters?.let { kotlin.math.round(it * 3.28084).toInt() }
    val peakWindKt = maxOf(snapshot.windSpeedKt, snapshot.gustKt ?: 0)
    val visBelow = !isCavok && vis != null && vis < config.vfrMinVisibilityMeters
    val ceilBelow = !isCavok && ceilFt != null && ceilFt < config.vfrMinCeilingFeet
    val windAbove = peakWindKt > config.vfrMaxWindKt
    return VfrMinimumStatus(
      icao = snapshot.icao,
      isBelowVfr = visBelow || ceilBelow || windAbove,
      visibilityBelow = visBelow,
      ceilingBelow = ceilBelow,
      windAbove = windAbove,
      currentVisMeters = vis,
      currentCeilFeet = ceilFt,
      currentPeakWindKt = peakWindKt,
      breachReasons = emptyList(),
    )
  }

  fun evaluate(
    station: AirportStation,
    previous: FavoriteWeatherSnapshot?,
    current: DecodedWeather,
    config: WeatherAlertConfig,
    nowMillis: Long = System.currentTimeMillis(),
    isVfrMonitored: Boolean = true,
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
    val curCeilFt = current.ceilingFeet ?: curCeil?.let { kotlin.math.round(it * 3.28084).toInt() }
    val curWindKt = current.wind.speedKt
    val curGustKt = current.wind.gustKt
    val curPhenom = current.weatherPhenomena.trim()
    val hasPhenom = curPhenom.isNotBlank() &&
      !curPhenom.equals("Без явлений", ignoreCase = true) &&
      !curPhenom.equals("Нет данных", ignoreCase = true)
    val rawUpper = current.rawMetar.uppercase()
    val hasSevereToken = hazardCodes.any { token -> rawUpper.contains(token) }

    val vfrStatus = if (config.vfrAlertsEnabled && isVfrMonitored) {
      evaluateVfrMinimums(current, config)
    } else {
      null
    }
    val prevVfrStatus = if (previous != null && config.vfrAlertsEnabled && isVfrMonitored) {
      evaluateSnapshotVfr(previous, config)
    } else {
      null
    }

    if (previous == null) {
      // On first observation: alert on severe hazards OR when conditions are below user-defined VFR minimums.
      if (hasSevereToken) {
        reasons.add("Опасные явления: ${if (hasPhenom) curPhenom else extractMatchedHazard(rawUpper)}")
        isCritical = true
      }
      if (vfrStatus != null && vfrStatus.isBelowVfr) {
        reasons.addAll(vfrStatus.breachReasons)
        if ((vfrStatus.currentVisMeters != null && vfrStatus.currentVisMeters < 1500) ||
          (vfrStatus.currentCeilFeet != null && vfrStatus.currentCeilFeet < 500) ||
          vfrStatus.currentPeakWindKt >= 30
        ) {
          isCritical = true
        }
      } else {
        if (curVis != null && curVis <= 1500) {
          reasons.add("Низкая видимость: $curVis м")
          isCritical = true
        }
        if (curCeil != null && curCeil <= 200) {
          reasons.add("Низкая ВНГО: $curCeilFt ft")
          isCritical = true
        }
        if (curWindKt >= 25 || (curGustKt != null && curGustKt >= 25)) {
          val gustSuffix = curGustKt?.let { " (порывы $it kt)" }.orEmpty()
          reasons.add("Сильный ветер: $curWindKt kt$gustSuffix")
          isCritical = true
        }
      }
    } else {
      // 1. User-defined VFR minimums drop / worsening
      if (vfrStatus != null && vfrStatus.isBelowVfr) {
        val newlyBelowVfr = prevVfrStatus == null || !prevVfrStatus.isBelowVfr
        val worsenedVis = prevVfrStatus?.currentVisMeters != null &&
          vfrStatus.currentVisMeters != null &&
          vfrStatus.visibilityBelow &&
          prevVfrStatus.currentVisMeters - vfrStatus.currentVisMeters >= 300
        val worsenedCeil = prevVfrStatus?.currentCeilFeet != null &&
          vfrStatus.currentCeilFeet != null &&
          vfrStatus.ceilingBelow &&
          prevVfrStatus.currentCeilFeet - vfrStatus.currentCeilFeet >= 100
        if (newlyBelowVfr || worsenedVis || worsenedCeil) {
          reasons.addAll(vfrStatus.breachReasons)
          if ((vfrStatus.currentVisMeters != null && vfrStatus.currentVisMeters < 1500) ||
            (vfrStatus.currentCeilFeet != null && vfrStatus.currentCeilFeet < 500) ||
            vfrStatus.currentPeakWindKt >= 30
          ) {
            isCritical = true
          }
        }
      }

      // 2. Wind speed / gust changes
      val windDiff = abs(curWindKt - previous.windSpeedKt)
      val prevGust = previous.gustKt ?: 0
      if (windDiff >= config.windChangeThresholdKt) {
        val arrow = if (curWindKt > previous.windSpeedKt) "↑" else "↓"
        reasons.add("Ветер $arrow ${previous.windSpeedKt}→$curWindKt kt")
        if (curWindKt >= 25) isCritical = true
      }
      if (curGustKt != null && (curGustKt - prevGust >= config.windChangeThresholdKt || (prevGust == 0 && curGustKt >= 20))) {
        reasons.add("Порывы ветра до $curGustKt kt")
        if (curGustKt >= 25) isCritical = true
      }

      // 3. Wind direction shift at >= 10 kt
      val prevDir = previous.windDirectionDeg
      val curDir = current.wind.directionDeg
      if (prevDir != null && curDir != null && (curWindKt >= 10 || previous.windSpeedKt >= 10)) {
        val rawDiff = abs(curDir - prevDir)
        val angularDiff = minOf(rawDiff, 360 - rawDiff)
        if (angularDiff >= 45) {
          reasons.add("Сдвиг направления ветра ${prevDir.toString().padStart(3, '0')}°→${curDir.toString().padStart(3, '0')}°")
        }
      }

      // 4. Visibility drop / recovery across operational thresholds
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

      // 5. Ceiling (ВНГО) drop
      val prevCeil = previous.ceilingMeters
      if (curCeil != null && curCeilFt != null) {
        if (prevCeil != null) {
          val prevCeilFt = kotlin.math.round(prevCeil * 3.28084).toInt()
          val crossedBelow = prevCeil > config.ceilingThresholdMeters && curCeil <= config.ceilingThresholdMeters
          val sharpDrop = prevCeil - curCeil >= 200 && curCeil <= 600
          if (crossedBelow || sharpDrop) {
            reasons.add("Снижение ВНГО ${prevCeilFt}→$curCeilFt ft")
            if (curCeil <= 150) isCritical = true
          }
        } else if (curCeil <= config.ceilingThresholdMeters) {
          reasons.add("Низкая ВНГО: $curCeilFt ft")
          if (curCeil <= 150) isCritical = true
        }
      }

      // 6. Weather phenomena changes
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

      // 7. QNH pressure jump
      val prevQnh = previous.qnhHpa
      val curQnh = current.qnhHpa
      if (prevQnh != null && curQnh != null && abs(curQnh - prevQnh) >= 4) {
        reasons.add("Давление QNH ${prevQnh}→$curQnh гПа")
      }
    }

    val dedupReasons = reasons.distinct()
    if (dedupReasons.isEmpty()) {
      return EvaluationResult(alert = null, snapshot = nextSnapshot)
    }

    val severity = if (isCritical) WeatherAlertSeverity.CRITICAL else WeatherAlertSeverity.WARNING
    val cleanIcao = station.icao.trim().uppercase()
    val hasVfrReason = dedupReasons.any { it.contains("ПВП", ignoreCase = true) }
    val title = if (hasVfrReason) {
      "Минимум ПВП (VFR): $cleanIcao (${station.city})"
    } else {
      "${severity.label}: $cleanIcao (${station.city})"
    }
    val summary = dedupReasons.joinToString(" · ")
    val alertId = "${cleanIcao}_${current.observationTimeUtc}_${abs(current.rawMetar.hashCode())}"
    val alert = WeatherChangeAlert(
      id = alertId,
      icao = cleanIcao,
      airportName = station.city.ifBlank { station.name },
      severity = severity,
      title = title,
      summary = summary,
      reasons = dedupReasons,
      observationTimeUtc = current.observationTimeUtc,
      rawMetar = current.rawMetar,
      timestampLocal = current.receivedTimeLocal.ifBlank { current.localObsTime },
      createdAtMillis = nowMillis,
      isRead = false,
    )
    return EvaluationResult(alert = alert, snapshot = nextSnapshot)
  }

  fun evaluateVfrBreachDirect(
    station: AirportStation,
    current: DecodedWeather,
    config: WeatherAlertConfig,
    nowMillis: Long = System.currentTimeMillis(),
  ): WeatherChangeAlert? {
    if (!config.enabled || !config.vfrAlertsEnabled || !hasValidObservation(current)) return null
    val status = evaluateVfrMinimums(current, config)
    if (!status.isBelowVfr) return null
    val cleanIcao = station.icao.trim().uppercase()
    val isCritical = (status.currentVisMeters != null && status.currentVisMeters < 1500) ||
      (status.currentCeilFeet != null && status.currentCeilFeet < 500) ||
      status.currentPeakWindKt >= 30
    val severity = if (isCritical) WeatherAlertSeverity.CRITICAL else WeatherAlertSeverity.WARNING
    val alertId = "${cleanIcao}_VFR_${current.observationTimeUtc}_${config.vfrMinVisibilityMeters}_${config.vfrMinCeilingFeet}_${config.vfrMaxWindKt}"
    return WeatherChangeAlert(
      id = alertId,
      icao = cleanIcao,
      airportName = station.city.ifBlank { station.name },
      severity = severity,
      title = "Минимум ПВП (VFR): $cleanIcao (${station.city})",
      summary = status.breachReasons.joinToString(" · "),
      reasons = status.breachReasons,
      observationTimeUtc = current.observationTimeUtc,
      rawMetar = current.rawMetar,
      timestampLocal = current.receivedTimeLocal.ifBlank { current.localObsTime },
      createdAtMillis = nowMillis,
      isRead = false,
    )
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
