package com.example.domain

import com.example.model.AirportStation
import com.example.model.DecodedWeather
import kotlin.math.exp
import kotlin.math.roundToInt

data class AviationPerformanceMetrics(
  val elevationFeet: Int,
  val pressureAltitudeFeet: Int?,
  val densityAltitudeFeet: Int?,
  val isaTempC: Double?,
  val isaDeviationC: Int?,
  val relativeHumidityPercent: Int?,
  val dewPointSpreadC: Int?,
  val highDensityAltitudeWarning: Boolean,
  val fogRiskWarning: Boolean,
  val icingRiskWarning: Boolean,
) {
  fun advisorySummary(): String {
    val tags = buildList {
      if (highDensityAltitudeWarning) add("Выс. DA (+${(densityAltitudeFeet ?: 0) - elevationFeet} ft)")
      if (fogRiskWarning) add("Риск тумана (ΔT=${dewPointSpreadC}°)")
      if (icingRiskWarning) add("Усл. обледенения")
    }
    return if (tags.isEmpty()) "Норма МСА" else tags.joinToString(" · ")
  }
}

/**
 * Computes pilot-critical aerodrome performance telemetry from live METAR & station elevation:
 * - Pressure Altitude (PA)
 * - Density Altitude (DA)
 * - ISA Standard Temperature & ΔISA deviation
 * - Relative Humidity (RH %) & Temp/Dewpoint spread (fog/icing advisories)
 */
object AviationPerformanceCalculator {

  private const val METERS_TO_FEET = 3.28084

  fun calculate(
    station: AirportStation,
    weather: DecodedWeather,
  ): AviationPerformanceMetrics {
    val elevationFeet = (station.elevationMeters * METERS_TO_FEET).roundToInt()
    val qnh = weather.qnhHpa?.toDouble()
    val temp = weather.temperatureC
    val dew = weather.dewPointC

    val pressureAltFt = qnh?.let { UnitConverter.computePressureAltitudeFeet(it, station.elevationMeters) }
    val isaTemp = (pressureAltFt ?: elevationFeet).let { paFt ->
      15.0 - 0.0019812 * paFt
    }
    val isaDev = temp?.let { (it - isaTemp).roundToInt() }
    val densityAltFt = if (pressureAltFt != null && temp != null) {
      (pressureAltFt + 118.8 * (temp - isaTemp)).roundToInt()
    } else {
      null
    }

    val rh = if (temp != null && dew != null) {
      computeRelativeHumidityPercent(temp.toDouble(), dew.toDouble())
    } else {
      null
    }
    val spread = if (temp != null && dew != null) temp - dew else null

    val highDa = densityAltFt != null && (densityAltFt - elevationFeet) >= 1500
    val fogRisk = spread != null && spread <= 2
    val hasMoisture = weather.clouds.any { it.cover in listOf("BKN", "OVC") } ||
      weather.weatherPhenomena.let { p ->
        p.contains("дожд", ignoreCase = true) ||
          p.contains("снег", ignoreCase = true) ||
          p.contains("морось", ignoreCase = true) ||
          p.contains("туман", ignoreCase = true) ||
          p.contains("голол", ignoreCase = true)
      }
    val icingRisk = temp != null && temp in -15..2 && (hasMoisture || (spread != null && spread <= 3))

    return AviationPerformanceMetrics(
      elevationFeet = elevationFeet,
      pressureAltitudeFeet = pressureAltFt,
      densityAltitudeFeet = densityAltFt,
      isaTempC = isaTemp,
      isaDeviationC = isaDev,
      relativeHumidityPercent = rh,
      dewPointSpreadC = spread,
      highDensityAltitudeWarning = highDa,
      fogRiskWarning = fogRisk,
      icingRiskWarning = icingRisk,
    )
  }

  fun computeRelativeHumidityPercent(tempC: Double, dewPointC: Double): Int {
    val a = 17.625
    val b = 243.04
    val numerator = exp((a * dewPointC) / (b + dewPointC))
    val denominator = exp((a * tempC) / (b + tempC))
    return ((numerator / denominator) * 100.0).roundToInt().coerceIn(1, 100)
  }
}
