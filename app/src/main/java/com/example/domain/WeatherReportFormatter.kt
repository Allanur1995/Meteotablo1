package com.example.domain

import com.example.model.AirportStation
import com.example.model.AuthorBrand
import com.example.model.DecodedWeather
import com.example.model.PressureUnit
import com.example.model.QnhTrendSummary
import com.example.model.RunwayCalculation
import com.example.model.WeatherHistoryEntry
import com.example.model.WindSpeedUnit
import com.example.model.WindTrendDirection
import com.example.model.WindTrendSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.roundToInt

data class WeatherExportPayload(
  val station: AirportStation,
  val weather: DecodedWeather,
  val primaryRunwayCalc: RunwayCalculation,
  val reciprocalRunwayCalc: RunwayCalculation,
  val windUnit: WindSpeedUnit,
  val pressureUnit: PressureUnit,
  val windTrend: WindTrendSummary,
  val qnhTrend24h: QnhTrendSummary,
  val weatherHistory24h: List<WeatherHistoryEntry>,
)

object WeatherReportFormatter {

  fun suggestedFileBaseName(
    station: AirportStation,
    weather: DecodedWeather,
  ): String {
    val cleanIcao = station.icao.trim().uppercase().replace(Regex("[^A-Z0-9]"), "")
    val cleanTime = weather.observationTimeUtc
      .trim()
      .uppercase()
      .replace(":", "")
      .replace(Regex("[^A-Z0-9_-]"), "")
      .ifBlank { "CURRENT" }
    return "Meteotablo_${cleanIcao}_$cleanTime"
  }

  fun buildTextSummary(
    payload: WeatherExportPayload,
    generatedAtMillis: Long = System.currentTimeMillis(),
  ): String {
    val station = payload.station
    val weather = payload.weather
    val windUnit = payload.windUnit
    val pressureUnit = payload.pressureUnit
    val category = AirportMapProjection.evaluateFlightCategory(weather)

    val latDir = if (station.latitude >= 0) "N" else "S"
    val lonDir = if (station.longitude >= 0) "E" else "W"
    val coords = String.format(
      Locale.US,
      "%.4f°%s, %.4f°%s · превышение %d м",
      abs(station.latitude),
      latDir,
      abs(station.longitude),
      lonDir,
      station.elevationMeters,
    )

    val windStr = formatWind(weather, windUnit)
    val visStr = when {
      weather.isCavok -> "CAVOK (> 10 км)"
      weather.visibilityMeters != null && weather.visibilityMeters >= 1000 ->
        "${weather.visibilityMeters} м (${weather.visibilityMeters / 1000} км)"
      weather.visibilityMeters != null -> "${weather.visibilityMeters} м"
      else -> "Н/Д"
    }
    val cloudsStr = if (weather.clouds.isNotEmpty()) {
      weather.clouds.joinToString(", ") { "${it.cover} ${it.baseFeet} ft (${it.baseMeters} м)" }
    } else if (weather.isCavok) {
      "CAVOK (существенной облачности нет)"
    } else {
      "NSC / без существенной облачности"
    }

    val (qnhPrimary, qnhPrimaryUnit) = UnitConverter.formatPressurePrimary(
      hpa = weather.qnhHpa,
      inHg = weather.qnhInHg,
      unit = pressureUnit,
      locale = Locale.US,
    )
    val qnhMm = weather.qnhMmHg ?: weather.qnhHpa?.let { UnitConverter.convertHpaToMmHg(it) }
    val qnhIn = weather.qnhInHg ?: weather.qnhHpa?.let { UnitConverter.convertHpaToInHg(it) }
    val qnhSecondary = buildList {
      weather.qnhHpa?.let { add("$it гПа") }
      qnhMm?.let { add("$it мм рт. ст.") }
      qnhIn?.let { add(String.format(Locale.US, "%.2f inHg", it)) }
    }.joinToString(" · ").ifBlank { "Н/Д" }

    val (qfePrimary, qfePrimaryUnit) = UnitConverter.formatPressurePrimary(
      hpa = weather.qfeHpa,
      inHg = weather.qfeInHg,
      unit = pressureUnit,
      locale = Locale.US,
    )

    val tempStr = weather.temperatureC?.let { "$it °C" } ?: "Н/Д"
    val dewStr = weather.dewPointC?.let { "$it °C" } ?: "Н/Д"
    val perf = AviationPerformanceCalculator.calculate(station, weather)
    val paStr = perf.pressureAltitudeFeet?.let { "$it ft" } ?: "Н/Д"
    val daStr = perf.densityAltitudeFeet?.let { "$it ft" } ?: "Н/Д"
    val isaStr = perf.isaDeviationC?.let { if (it >= 0) "МСА +$it°C" else "МСА $it°C" } ?: "Н/Д"
    val rhStr = perf.relativeHumidityPercent?.let { "$it%" } ?: "Н/Д"

    val windTrendLine = formatWindTrend(payload.windTrend, windUnit)
    val qnhTrendLine = formatQnhTrend(payload.qnhTrend24h, pressureUnit)
    val tempTrendLine = formatTempTrend(payload.qnhTrend24h)
    val generatedUtc = formatUtcTimestamp(generatedAtMillis)

    return buildString {
      appendLine("============================================================")
      appendLine("${AuthorBrand.APP_TITLE.uppercase(Locale.US)} — МЕТЕОСВОДКА АЭРОДРОМА (${station.icao})")
      appendLine("============================================================")
      appendLine("Аэродром      : ${station.icao} — ${station.name} (${station.city})")
      appendLine("Координаты    : $coords")
      appendLine("Наблюдение    : ${weather.observationTimeUtc} (местн. ${weather.localObsTime} ${station.timezoneLabel})")
      appendLine("Категория     : ${category.code} (${category.description})")
      appendLine("Источник      : ${weather.sourceLabel}")
      appendLine("Сформировано  : $generatedUtc")
      appendLine()
      appendLine("--- ТЕКУЩАЯ ПОГОДА (METAR) ---")
      appendLine("Ветер         : $windStr")
      appendLine("Видимость     : $visStr")
      appendLine("Облачность    : $cloudsStr")
      appendLine("Явления       : ${weather.weatherPhenomena}")
      appendLine("Темп. / Роса  : $tempStr / $dewStr (RH $rhStr)")
      appendLine("Давление QNH  : $qnhPrimary $qnhPrimaryUnit ($qnhSecondary)")
      appendLine("Давление QFE  : $qfePrimary $qfePrimaryUnit")
      appendLine("Выс.давл / DA : PA $paStr · DA $daStr ($isaStr)")
      appendLine("Статус ВПХ    : ${perf.advisorySummary()}")
      appendLine("Особые усл.   : ${weather.specialConditions}")
      appendLine()
      appendLine("--- РАСЧЁТ ВЕТРА НА ВПП (${windUnit.label}) ---")
      appendLine(formatRunwayLine(payload.primaryRunwayCalc, windUnit))
      appendLine(formatRunwayLine(payload.reciprocalRunwayCalc, windUnit))
      appendLine()
      appendLine("--- ТРЕНДЫ ВЕТРА, ДАВЛЕНИЯ QNH И ТЕМПЕРАТУРЫ (24Ч) ---")
      appendLine("Тренд ветра   : $windTrendLine")
      appendLine("Тренд QNH 24ч : $qnhTrendLine")
      appendLine("Тренд темп.24ч: $tempTrendLine")

      val recentEntries = payload.weatherHistory24h
        .sortedByDescending { it.observedAtMillis }
        .take(10)
      if (recentEntries.isNotEmpty()) {
        appendLine()
        appendLine("--- ЖУРНАЛ НАБЛЮДЕНИЙ 24Ч (последние ${recentEntries.size}) ---")
        recentEntries.forEach { entry ->
          val eWind = UnitConverter.formatWindSpeedValue(entry.windSpeedKt, windUnit)
          val eGust = entry.gustKt?.let { "G${UnitConverter.formatWindSpeedValue(it, windUnit)}" }.orEmpty()
          val eDir = entry.windDirectionDeg?.let { String.format(Locale.US, "%03d°", it) } ?: "VRB"
          val eVis = when {
            entry.isCavok -> "CAVOK"
            entry.visibilityMeters != null -> "${entry.visibilityMeters}м"
            else -> "--"
          }
          val (eQnh, eQnhU) = UnitConverter.formatPressurePrimary(
            hpa = entry.qnhHpa,
            inHg = entry.qnhInHg,
            unit = pressureUnit,
            locale = Locale.US,
          )
          val eTemp = entry.temperatureC?.let { "${it}°C" } ?: "--"
          appendLine(
            "• ${entry.observationTimeUtc} [${entry.flightCategoryCode}] | " +
              "Ветер $eDir $eWind$eGust ${windUnit.label} | Вид. $eVis | " +
              "QNH $eQnh $eQnhU | T $eTemp",
          )
        }
      }

      appendLine()
      appendLine("--- ИСХОДНЫЕ СВОДКИ ---")
      appendLine("METAR: ${weather.rawMetar}")
      appendLine("TAF  : ${weather.rawTaf}")
      appendLine()
      appendLine("${AuthorBrand.APP_TITLE} v${AuthorBrand.APP_VERSION}")
    }
  }

  private fun formatWind(weather: DecodedWeather, unit: WindSpeedUnit): String {
    val w = weather.wind
    if (w.isCalm) return "Штиль (0 ${unit.label})"
    val dir = when {
      w.isVariable -> "Переменный (VRB)"
      w.directionDeg != null -> String.format(Locale.US, "%03d°", w.directionDeg)
      else -> "---"
    }
    val spd = UnitConverter.formatWindSpeedValue(w.speedKt, unit)
    val gust = w.gustKt?.let { " (порывы ${UnitConverter.formatWindSpeedValue(it, unit)} ${unit.label})" }.orEmpty()
    val varSector = if (w.variableFromDeg != null && w.variableToDeg != null) {
      " [${String.format(Locale.US, "%03d", w.variableFromDeg)}°–${String.format(Locale.US, "%03d", w.variableToDeg)}°]"
    } else {
      ""
    }
    return "$dir $spd ${unit.label}$gust$varSector"
  }

  private fun formatRunwayLine(calc: RunwayCalculation, unit: WindSpeedUnit): String {
    val headRaw = UnitConverter.convertWindFromKnots(calc.headwindKt, unit).roundToInt()
    val headLabel = if (headRaw >= 0) "встречный $headRaw" else "попутный ${abs(headRaw)}"
    val crossVal = UnitConverter.convertWindFromKnots(abs(calc.crosswindKt), unit).roundToInt()
    val crossSide = when {
      calc.crosswindKt > 0.4 -> "справа"
      calc.crosswindKt < -0.4 -> "слева"
      else -> "по оси"
    }
    val rvrText = calc.rvrMeters?.let { "$it м" } ?: "---"
    val frictionText = calc.frictionCoeff?.let { String.format(Locale.US, "%.2f", it) } ?: "---"
    return "ВПП ${calc.designator} (${String.format(Locale.US, "%03d", calc.headingDeg)}°): " +
      "$headLabel ${unit.label} · боковой $crossVal ${unit.label} ($crossSide) · " +
      "RVR $rvrText · Ксц $frictionText"
  }

  private fun formatWindTrend(trend: WindTrendSummary, unit: WindSpeedUnit): String {
    if (trend.direction == WindTrendDirection.INSUFFICIENT || trend.entries.isEmpty()) {
      return "Недостаточно наблюдений (накоплено ${trend.entries.size})"
    }
    val curr = UnitConverter.formatWindSpeedValue(trend.currentSpeedKt, unit)
    val min = UnitConverter.formatWindSpeedValue(trend.minSpeedKt, unit)
    val max = UnitConverter.formatWindSpeedValue(trend.maxSpeedKt, unit)
    val deltaVal = UnitConverter.convertWindFromKnots(trend.deltaKt.toDouble(), unit).roundToInt()
    val sign = if (deltaVal > 0) "+" else ""
    return "${trend.direction.arrow} ${trend.direction.label} ($sign$deltaVal ${unit.label}) · " +
      "текущ. $curr, диапазон $min–$max ${unit.label} (${trend.entries.size} набл.)"
  }

  private fun formatQnhTrend(trend: QnhTrendSummary, pressureUnit: PressureUnit): String {
    if (trend.points.isEmpty()) {
      return "Нет точек QNH за последние 24 часа"
    }
    val latest = trend.latestHpa?.let {
      UnitConverter.formatPressurePrimary(it, null, pressureUnit, Locale.US).let { (v, u) -> "$v $u" }
    } ?: "--"
    val min = trend.minHpa?.let {
      UnitConverter.formatPressurePrimary(it, null, pressureUnit, Locale.US).first
    } ?: "--"
    val max = trend.maxHpa?.let {
      UnitConverter.formatPressurePrimary(it, null, pressureUnit, Locale.US).first
    } ?: "--"
    val deltaStr = trend.delta24hHpa?.let { d ->
      when (pressureUnit) {
        PressureUnit.HPA -> if (d > 0) "+$d гПа" else "$d гПа"
        PressureUnit.INHG -> {
          val dIn = UnitConverter.convertHpaToInHg(d)
          val prefix = if (dIn > 0) "+" else ""
          String.format(Locale.US, "%s%.2f дм", prefix, dIn)
        }
      }
    } ?: "--"
    val dirLabel = when {
      (trend.delta24hHpa ?: 0) >= 2 -> "↑ Рост давления"
      (trend.delta24hHpa ?: 0) <= -2 -> "↓ Падение давления"
      else -> "→ Стабильное давление"
    }
    return "$dirLabel ($deltaStr) · текущ. $latest · мин/макс $min–$max (${trend.points.size} т.)"
  }

  private fun formatTempTrend(trend: QnhTrendSummary): String {
    if (trend.temperaturePoints.isEmpty()) {
      return "Нет точек температуры за последние 24 часа"
    }
    val latest = trend.latestTempC?.let { "${it}°C" } ?: "--"
    val min = trend.minTempC?.let { "${it}°C" } ?: "--"
    val max = trend.maxTempC?.let { "${it}°C" } ?: "--"
    val delta = trend.delta24hTempC?.let { d -> if (d > 0) "+$d°C" else "$d°C" } ?: "--"
    return "Δ $delta · текущ. $latest · диапазон $min…$max (${trend.temperaturePoints.size} т.)"
  }

  private fun formatUtcTimestamp(millis: Long): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm 'UTC'", Locale.US).apply {
      timeZone = TimeZone.getTimeZone("UTC")
    }
    return fmt.format(Date(millis))
  }

  fun buildPrintableHtml(
    payload: WeatherExportPayload,
    generatedAtMillis: Long = System.currentTimeMillis(),
  ): String {
    val summary = buildTextSummary(payload, generatedAtMillis)
    val escapedSummary = summary
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
    val title = "${AuthorBrand.APP_TITLE} — ${payload.station.icao} (${payload.station.city})"
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
    return """
      <!DOCTYPE html>
      <html lang="ru">
      <head>
        <meta charset="UTF-8" />
        <title>$title</title>
        <style>
          body { font-family: 'Segoe UI', Arial, sans-serif; margin: 24px; background: #0f172a; color: #e2e8f0; }
          .card { max-width: 860px; margin: 0 auto; background: #1e293b; border: 1px solid #334155; border-radius: 8px; padding: 20px; }
          h1 { margin-top: 0; font-size: 18px; color: #f59e0b; }
          pre { white-space: pre-wrap; font-family: 'Consolas', 'Courier New', monospace; font-size: 13px; line-height: 1.45; color: #f8fafc; background: #0b1220; padding: 14px; border-radius: 6px; border: 1px solid #334155; }
          @media print {
            body { background: #ffffff; color: #0f172a; margin: 12px; }
            .card { background: #ffffff; border: none; padding: 0; }
            h1 { color: #1e293b; }
            pre { background: #f8fafc; color: #0f172a; border: 1px solid #cbd5e1; }
          }
        </style>
      </head>
      <body>
        <div class="card">
          <h1>$title</h1>
          <pre>$escapedSummary</pre>
        </div>
      </body>
      </html>
    """.trimIndent()
  }
}
