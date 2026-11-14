package com.example.domain

import com.example.model.AirportStation
import com.example.model.CloudLayer
import com.example.model.DecodedWeather
import com.example.model.RunwayCalculation
import com.example.model.WindData
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

object AviationWeatherParser {

  private const val KT_TO_MPS = 0.514444
  private const val HPA_TO_MMHG = 0.750062
  private const val HPA_TO_INHG = 0.029530

  fun parse(
    rawMetarInput: String,
    rawTafInput: String,
    station: AirportStation,
    sourceLabel: String,
    receivedTimeLocal: String? = null,
    calendarForSun: Calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")),
  ): DecodedWeather {
    val cleanMetar = rawMetarInput.trim().replace(Regex("\\s+"), " ")
    val cleanTaf = rawTafInput.trim().replace(Regex("\\s+"), " ")
    val tokens = cleanMetar.split(" ").filter { it.isNotBlank() }

    val obsTimeToken = tokens.firstOrNull { Regex("^\\d{6}Z$").matches(it) }
    val obsHour = obsTimeToken?.substring(2, 4)?.toIntOrNull()
    val obsMin = obsTimeToken?.substring(4, 6)?.toIntOrNull()
    val obsTimeUtc = if (obsHour != null && obsMin != null) {
      "%02d:%02dZ".format(obsHour, obsMin)
    } else {
      "--:--Z"
    }
    val localObsTime = if (obsHour != null && obsMin != null) {
      val localHr = ((obsHour + station.utcOffsetHours) % 24 + 24) % 24
      "%02d:%02d".format(localHr, obsMin)
    } else {
      "--:--"
    }

    val wind = parseWind(tokens)
    val isCavok = tokens.any { it == "CAVOK" }

    val visibilityMeters = when {
      isCavok -> 9999
      else -> parseVisibilityMeters(tokens)
    }

    val rvrMap = parseRvr(tokens)
    val primaryRvr = rvrMap[station.runways.firstOrNull()?.primaryDesignator] ?: rvrMap["ALL"]
    val reciprocalRvr = rvrMap[station.runways.firstOrNull()?.reciprocalDesignator] ?: rvrMap["ALL"]

    val weatherPhenomena = parseWeatherPhenomena(tokens, isCavok)
    val clouds = parseClouds(tokens, isCavok)

    val ceilingLayer = clouds.firstOrNull {
      it.cover == "BKN" || it.cover == "OVC" || it.cover == "VV"
    }
    val ceilingMeters = ceilingLayer?.baseMeters
    val ceilingCode = ceilingLayer?.rawToken ?: clouds.firstOrNull()?.rawToken

    val (tempC, dewPointC) = parseTempDewPoint(tokens)
    val qnhHpa = parseQnhHpa(tokens)
    val qnhMmHg = qnhHpa?.let { (it * HPA_TO_MMHG).roundToInt() }
    val qnhInHg = qnhHpa?.let { it * HPA_TO_INHG }

    val qfeHpa = qnhHpa?.let { computeQfeHpa(it, station.elevationMeters) }
    val qfeMmHg = qfeHpa?.let { (it * HPA_TO_MMHG).roundToInt() }
    val qfeInHg = qfeHpa?.let { it * HPA_TO_INHG }

    val frictionCoeff = parseFrictionCoeff(tokens)
    val specialConditions = buildSpecialConditions(tokens, wind, visibilityMeters, weatherPhenomena)

    val sunTimes = SolarCalculator.calculateSunriseSunset(
      latitude = station.latitude,
      longitude = station.longitude,
      utcOffsetHours = station.utcOffsetHours,
      calendar = calendarForSun,
    )

    val resolvedReceived = receivedTimeLocal ?: currentLocalTimeString(station.utcOffsetHours)

    return DecodedWeather(
      stationIcao = station.icao,
      observationTimeUtc = obsTimeUtc,
      localObsTime = localObsTime,
      receivedTimeLocal = resolvedReceived,
      wind = wind,
      visibilityMeters = visibilityMeters,
      isCavok = isCavok,
      rvrPrimaryMeters = primaryRvr,
      rvrReciprocalMeters = reciprocalRvr,
      weatherPhenomena = weatherPhenomena,
      clouds = clouds,
      ceilingMeters = ceilingMeters,
      ceilingCode = ceilingCode,
      temperatureC = tempC,
      dewPointC = dewPointC,
      qnhHpa = qnhHpa,
      qnhMmHg = qnhMmHg,
      qnhInHg = qnhInHg,
      qfeHpa = qfeHpa,
      qfeMmHg = qfeMmHg,
      qfeInHg = qfeInHg,
      frictionCoeff = frictionCoeff,
      specialConditions = specialConditions,
      rawMetar = cleanMetar,
      rawTaf = cleanTaf,
      sourceLabel = sourceLabel,
      sunriseLocal = sunTimes.sunriseLocal,
      sunsetLocal = sunTimes.sunsetLocal,
    )
  }

  fun calculateRunwayComponents(
    designator: String,
    runwayHeadingDeg: Int,
    wind: WindData,
    frictionCoeff: Double?,
    rvrMeters: Int?,
  ): RunwayCalculation {
    val dir = wind.directionDeg
    if (dir == null || wind.isCalm || wind.isVariable) {
      return RunwayCalculation(
        designator = designator,
        headingDeg = runwayHeadingDeg,
        crosswindMps = wind.speedMps,
        headwindMps = 0.0,
        crosswindKt = wind.speedKt.toDouble(),
        headwindKt = 0.0,
        frictionCoeff = frictionCoeff,
        rvrMeters = rvrMeters,
      )
    }

    val diffRad = (dir - runwayHeadingDeg) * PI / 180.0
    val headwindMps = wind.speedMps * cos(diffRad)
    val crosswindMps = abs(wind.speedMps * sin(diffRad))
    val headwindKt = wind.speedKt * cos(diffRad)
    val crosswindKt = abs(wind.speedKt * sin(diffRad))

    return RunwayCalculation(
      designator = designator,
      headingDeg = runwayHeadingDeg,
      crosswindMps = crosswindMps,
      headwindMps = headwindMps,
      crosswindKt = crosswindKt,
      headwindKt = headwindKt,
      frictionCoeff = frictionCoeff,
      rvrMeters = rvrMeters,
    )
  }

  private fun parseWind(tokens: List<String>): WindData {
    val windRegex = Regex("^(\\d{3}|VRB)(\\d{2,3})(G(\\d{2,3}))?(KT|MPS)$")
    val match = tokens.firstNotNullOfOrNull { windRegex.matchEntire(it) }

    val varRegex = Regex("^(\\d{3})V(\\d{3})$")
    val varMatch = tokens.firstNotNullOfOrNull { varRegex.matchEntire(it) }

    if (match == null) {
      return WindData(
        directionDeg = null,
        isVariable = false,
        isCalm = true,
        speedKt = 0,
        speedMps = 0.0,
      )
    }

    val dirStr = match.groupValues[1]
    val speedVal = match.groupValues[2].toIntOrNull() ?: 0
    val gustVal = match.groupValues[4].takeIf { it.isNotEmpty() }?.toIntOrNull()
    val unit = match.groupValues[5]

    val isVariable = dirStr == "VRB"
    val dirDeg = if (isVariable) null else dirStr.toIntOrNull()
    val isCalm = !isVariable && dirDeg == 0 && speedVal == 0

    val speedMps: Double
    val speedKt: Int
    val gustMps: Double?
    val gustKt: Int?

    if (unit == "MPS") {
      speedMps = speedVal.toDouble()
      speedKt = (speedVal / KT_TO_MPS).roundToInt()
      gustMps = gustVal?.toDouble()
      gustKt = gustVal?.let { (it / KT_TO_MPS).roundToInt() }
    } else {
      speedKt = speedVal
      speedMps = speedVal * KT_TO_MPS
      gustKt = gustVal
      gustMps = gustVal?.let { it * KT_TO_MPS }
    }

    return WindData(
      directionDeg = dirDeg,
      isVariable = isVariable,
      isCalm = isCalm,
      variableFromDeg = varMatch?.groupValues?.get(1)?.toIntOrNull(),
      variableToDeg = varMatch?.groupValues?.get(2)?.toIntOrNull(),
      speedKt = speedKt,
      gustKt = gustKt,
      speedMps = speedMps,
      gustMps = gustMps,
    )
  }

  private fun parseVisibilityMeters(tokens: List<String>): Int? {
    // 4-digit visibility after wind token, excluding time/QNH/runway tokens
    val visRegex = Regex("^(\\d{4})$")
    val windIdx = tokens.indexOfFirst { Regex("^(\\d{3}|VRB)\\d{2,3}.*(KT|MPS)$").matches(it) }
    val searchStart = if (windIdx >= 0) windIdx + 1 else 0
    for (i in searchStart until tokens.size) {
      val token = tokens[i]
      if (token == "RMK") break
      if (visRegex.matches(token)) {
        return token.toIntOrNull()
      }
      // US SM visibility e.g. 10SM or 1/2SM
      val smMatch = Regex("^(\\d+)SM$").matchEntire(token)
      if (smMatch != null) {
        val miles = smMatch.groupValues[1].toIntOrNull() ?: continue
        return (miles * 1609.34).roundToInt().coerceAtMost(9999)
      }
    }
    return null
  }

  private fun parseRvr(tokens: List<String>): Map<String, Int> {
    val result = mutableMapOf<String, Int>()
    val rvrRegex = Regex("^R(\\d{2}[LRC]?)/[PM]?(\\d{4})[UDN]?$")
    for (token in tokens) {
      val m = rvrRegex.matchEntire(token) ?: continue
      val rwy = m.groupValues[1]
      val meters = m.groupValues[2].toIntOrNull() ?: continue
      result[rwy] = meters
    }
    return result
  }

  private fun parseWeatherPhenomena(tokens: List<String>, isCavok: Boolean): String {
    if (isCavok) return "Явлений нет (CAVOK)"
    val wxRegex = Regex("^([+-]|VC)?(MI|PR|BC|DR|BL|SH|TS|FZ)?(DZ|RA|SN|SG|IC|PL|GR|GS|UP|BR|FG|FU|VA|DU|SA|HZ|PY|PO|SQ|FC|SS|DS)+$")
    val found = mutableListOf<String>()
    val rmkIdx = tokens.indexOf("RMK")
    val limit = if (rmkIdx >= 0) rmkIdx else tokens.size
    for (i in 0 until limit) {
      val token = tokens[i]
      if (token == "NSW") return "Явлений нет"
      if (token.length in 2..9 && wxRegex.matches(token)) {
        if (token !in listOf("METAR", "SPECI", "NOSIG", "TEMPO", "BECMG", "CAVOK", "AUTO", "COR")) {
          found.add(describeWeatherToken(token))
        }
      }
    }
    return if (found.isEmpty()) "Явлений нет" else found.joinToString(", ")
  }

  private fun describeWeatherToken(token: String): String {
    var rest = token
    val prefix = when {
      rest.startsWith("+") -> { rest = rest.drop(1); "Сильный " }
      rest.startsWith("-") -> { rest = rest.drop(1); "Слабый " }
      rest.startsWith("VC") -> { rest = rest.drop(2); "В окрестностях: " }
      else -> ""
    }
    val desc = when {
      rest.startsWith("TS") -> { rest = rest.drop(2); "гроза " }
      rest.startsWith("SH") -> { rest = rest.drop(2); "ливень " }
      rest.startsWith("FZ") -> { rest = rest.drop(2); "переохлажд. " }
      rest.startsWith("BL") -> { rest = rest.drop(2); "низовая метель/поземок " }
      else -> ""
    }
    val main = when (rest) {
      "RA" -> "дождь"
      "SN" -> "снег"
      "DZ" -> "морось"
      "FG" -> "туман"
      "BR" -> "дымка"
      "HZ" -> "мгла"
      "DU" -> "пыль"
      "SA" -> "песок"
      "GR" -> "град"
      "SQ" -> "шквал"
      "SS" -> "песчаная буря"
      "DS" -> "пыльная буря"
      "" -> ""
      else -> rest
    }
    val combined = "$prefix$desc$main".trim().replaceFirstChar { it.uppercaseChar() }
    return "$combined ($token)"
  }

  private fun parseClouds(tokens: List<String>, isCavok: Boolean): List<CloudLayer> {
    if (isCavok) return emptyList()
    val cloudRegex = Regex("^(FEW|SCT|BKN|OVC|VV)(\\d{3})(CB|TCU)?$")
    val layers = mutableListOf<CloudLayer>()
    val rmkIdx = tokens.indexOf("RMK")
    val limit = if (rmkIdx >= 0) rmkIdx else tokens.size
    for (i in 0 until limit) {
      val m = cloudRegex.matchEntire(tokens[i]) ?: continue
      val cover = m.groupValues[1]
      val fl = m.groupValues[2].toIntOrNull() ?: continue
      val baseFeet = fl * 100
      val baseMeters = (baseFeet * 0.3048).roundToInt()
      layers.add(CloudLayer(cover = cover, baseFeet = baseFeet, baseMeters = baseMeters, rawToken = tokens[i]))
    }
    return layers
  }

  private fun parseTempDewPoint(tokens: List<String>): Pair<Int?, Int?> {
    val tempRegex = Regex("^(M?\\d{2})/(M?\\d{2})$")
    val m = tokens.firstNotNullOfOrNull { tempRegex.matchEntire(it) } ?: return null to null
    val t = parseSignedTemp(m.groupValues[1])
    val dp = parseSignedTemp(m.groupValues[2])
    return t to dp
  }

  private fun parseSignedTemp(raw: String): Int? {
    return if (raw.startsWith("M")) {
      raw.drop(1).toIntOrNull()?.let { -it }
    } else {
      raw.toIntOrNull()
    }
  }

  private fun parseQnhHpa(tokens: List<String>): Int? {
    val qRegex = Regex("^Q(\\d{4})$")
    val qMatch = tokens.firstNotNullOfOrNull { qRegex.matchEntire(it) }
    if (qMatch != null) return qMatch.groupValues[1].toIntOrNull()

    val aRegex = Regex("^A(\\d{4})$")
    val aMatch = tokens.firstNotNullOfOrNull { aRegex.matchEntire(it) }
    if (aMatch != null) {
      val inHg = (aMatch.groupValues[1].toIntOrNull() ?: return null) / 100.0
      return (inHg / HPA_TO_INHG).roundToInt()
    }
    return null
  }

  private fun parseFrictionCoeff(tokens: List<String>): Double? {
    // ICAO runway state group e.g. R88/CLRD70 or R35/290065 -> last 2 digits 70 => 0.70
    val rStateRegex = Regex("^R\\d{2}[LRC]?/(?:CLRD|\\d{4})(\\d{2})$")
    val match = tokens.firstNotNullOfOrNull { rStateRegex.matchEntire(it) } ?: return null
    val code = match.groupValues[1].toIntOrNull() ?: return null
    return when {
      code in 10..90 -> code / 100.0
      code == 91 -> 0.25
      code == 92 -> 0.28
      code == 93 -> 0.33
      code == 94 -> 0.38
      code == 95 -> 0.45
      else -> null
    }
  }

  private fun computeQfeHpa(qnhHpa: Int, elevationMeters: Int): Int {
    // Barometric hypsometric approximation matching ICAO standard atmosphere
    val factor = (1.0 - 2.25577e-5 * elevationMeters).pow(5.25588)
    return (qnhHpa * factor).roundToInt()
  }

  private fun buildSpecialConditions(
    tokens: List<String>,
    wind: WindData,
    visibilityMeters: Int?,
    weatherPhenomena: String,
  ): String {
    val flags = mutableListOf<String>()
    if ((wind.gustMps ?: 0.0) >= 12.0) flags.add("порывы ветра")
    if (visibilityMeters != null && visibilityMeters < 1500) flags.add("ограниченная видимость")
    if (tokens.any { it.contains("TS") }) flags.add("гроза")
    if (tokens.any { it.contains("WS") }) flags.add("сдвиг ветра")
    if (weatherPhenomena != "Явлений нет" && !weatherPhenomena.contains("CAVOK") && flags.isEmpty()) {
      flags.add(weatherPhenomena)
    }
    return if (flags.isEmpty()) "нет" else flags.joinToString(", ")
  }

  fun currentLocalTimeString(utcOffsetHours: Int): String {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    cal.add(Calendar.HOUR_OF_DAY, utcOffsetHours)
    return "%02d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
  }
}
