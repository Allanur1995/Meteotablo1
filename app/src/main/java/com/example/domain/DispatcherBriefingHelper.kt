package com.example.domain

import com.example.model.AirportStation
import com.example.model.DecodedWeather
import com.example.model.RunwayCalculation
import com.example.model.WeatherAlertConfig
import kotlin.math.abs

data class RunwayUsabilityEntry(
  val calculation: RunwayCalculation,
  val isWithinCrosswindLimit: Boolean,
  val isPreferredHeadwind: Boolean,
)

data class DispatcherBriefingSummary(
  val flightCategory: FlightCategory,
  val metarAgeMinutes: Int?,
  val isMetarStale: Boolean,
  val recommendedRunway: RunwayCalculation?,
  val runwayEvaluations: List<RunwayUsabilityEntry>,
  val hazards: List<String>,
  val tempDewSpreadC: Int?,
  val gustSpreadKt: Int?,
)

object DispatcherBriefingHelper {

  fun evaluate(
    station: AirportStation,
    weather: DecodedWeather,
    alertConfig: WeatherAlertConfig,
    referenceNowMillis: Long = System.currentTimeMillis(),
  ): DispatcherBriefingSummary {
    val category = AirportMapProjection.evaluateFlightCategory(weather)
    val hasLiveMetar = weather.observationTimeUtc != "--:--Z" &&
      weather.rawMetar.isNotBlank() &&
      !weather.rawMetar.contains("нет данных", ignoreCase = true)

    val ageMinutes = if (hasLiveMetar) {
      val obsMillis = WeatherHistoryAnalyzer.resolveObservationMillis(weather.rawMetar, referenceNowMillis)
      ((referenceNowMillis - obsMillis) / 60_000L).toInt().coerceAtLeast(0)
    } else {
      null
    }
    val isStale = ageMinutes != null && ageMinutes >= 65

    val allEnds = station.runways.flatMap { pair ->
      listOf(
        AviationWeatherParser.calculateRunwayComponents(
          designator = pair.primaryDesignator,
          runwayHeadingDeg = pair.primaryHeadingDeg,
          wind = weather.wind,
          frictionCoeff = weather.frictionCoeff,
          rvrMeters = weather.rvrPrimaryMeters,
        ),
        AviationWeatherParser.calculateRunwayComponents(
          designator = pair.reciprocalDesignator,
          runwayHeadingDeg = pair.reciprocalHeadingDeg,
          wind = weather.wind,
          frictionCoeff = weather.frictionCoeff,
          rvrMeters = weather.rvrReciprocalMeters ?: weather.rvrPrimaryMeters,
        ),
      )
    }

    val bestEnd = allEnds.maxByOrNull { calc ->
      // Prefer headwind >= 0 and minimal crosswind
      (calc.headwindKt * 1.5) - abs(calc.crosswindKt)
    }

    val gustFactor = if (weather.wind.gustKt != null && weather.wind.speedKt > 0) {
      weather.wind.gustKt.toDouble() / weather.wind.speedKt.toDouble()
    } else {
      1.0
    }

    val evaluations = allEnds.map { calc ->
      val gustCross = if (weather.wind.gustKt != null) calc.crosswindKt * gustFactor else null
      val withinLimit = !alertConfig.isCrosswindExceeded(calc.crosswindKt, gustCross)
      RunwayUsabilityEntry(
        calculation = calc,
        isWithinCrosswindLimit = withinLimit,
        isPreferredHeadwind = bestEnd != null &&
          calc.designator == bestEnd.designator &&
          calc.headingDeg == bestEnd.headingDeg,
      )
    }

    val tempDewSpread = if (weather.temperatureC != null && weather.dewPointC != null) {
      weather.temperatureC - weather.dewPointC
    } else {
      null
    }
    val gustSpread = if (weather.wind.gustKt != null) {
      (weather.wind.gustKt - weather.wind.speedKt).coerceAtLeast(0)
    } else {
      null
    }

    val hazards = buildList {
      val phenLower = weather.weatherPhenomena.lowercase()
      if (phenLower.contains("гроз") || phenLower.contains("град")) {
        add("Грозовая активность (TS/CB)")
      }
      if (phenLower.contains("шквал")) {
        add("Шквалы (SQ)")
      }
      if (phenLower.contains("туман") || (weather.visibilityMeters != null && weather.visibilityMeters < 1500)) {
        add("Ограниченная видимость / туман (< 1500 м)")
      }
      if (weather.ceilingFeet != null && weather.ceilingFeet < 500) {
        add("Низкая ВНГО (${weather.ceilingFeet} ft)")
      }
      if (tempDewSpread != null && tempDewSpread <= 2 && hasLiveMetar) {
        add("Риск тумана/обледенения (T−Td = $tempDewSpread°C)")
      }
      if (gustSpread != null && gustSpread >= 10) {
        add("Порывистость +$gustSpread kt")
      }
      if (evaluations.any { !it.isWithinCrosswindLimit }) {
        add("Превышение бок. ветра > ${alertConfig.maxCrosswindKt} kt (${alertConfig.resolvedAircraftName()})")
      }
      if (isStale) {
        add("METAR устарел ($ageMinutes мин назад)")
      }
    }

    return DispatcherBriefingSummary(
      flightCategory = category,
      metarAgeMinutes = ageMinutes,
      isMetarStale = isStale,
      recommendedRunway = bestEnd,
      runwayEvaluations = evaluations,
      hazards = hazards,
      tempDewSpreadC = tempDewSpread,
      gustSpreadKt = gustSpread,
    )
  }
}
