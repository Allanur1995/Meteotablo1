package com.example.domain

import com.example.model.DecodedTaf
import com.example.model.TafChangeType
import com.example.model.TafForecastPeriod
import com.example.model.WeatherAlertConfig
import com.example.model.WindSpeedUnit
import java.util.Locale
import kotlin.math.roundToInt

object TafParser {

  private const val KT_TO_MPS = 0.514444
  private const val KMH_TO_KT = 0.539957

  private val windTokenRegex = Regex("^(\\d{3}|VRB)(\\d{2,3})(G(\\d{2,3}))?(KT|MPS|KMH)$")
  private val visMetersRegex = Regex("^(\\d{4})$")
  private val visSmRegex = Regex("^([PM]?\\d+(?:/\\d+)?SM)$")
  private val cloudRegex = Regex("^(FEW|SCT|BKN|OVC|VV)(\\d{3}|///)(CB|TCU)?$")
  private val periodRangeRegex = Regex("^(\\d{2})(\\d{2})/(\\d{2})(\\d{2})$")
  private val fmTokenRegex = Regex("^FM(\\d{2})(\\d{2})(\\d{2})$")
  private val probTokenRegex = Regex("^PROB(30|40)$")
  private val issueTimeRegex = Regex("^\\d{6}Z$")

  private val phenomDictionary = listOf(
    "+TSRA" to "Сильная гроза с дождём",
    "-TSRA" to "Слабая гроза с дождём",
    "TSRA" to "Гроза с дождём",
    "TSGR" to "Гроза с градом",
    "VCTS" to "Гроза в окрестности",
    "TS" to "Гроза",
    "+SHRA" to "Сильный ливневый дождь",
    "-SHRA" to "Слабый ливневый дождь",
    "SHRA" to "Ливневый дождь",
    "SHSN" to "Ливневый снег",
    "FZRA" to "Переохлаждённый дождь",
    "FZDZ" to "Переохлаждённая морось",
    "FZFG" to "Переохлаждённый туман",
    "+RA" to "Сильный дождь",
    "-RA" to "Слабый дождь",
    "RA" to "Дождь",
    "+SN" to "Сильный снег",
    "-SN" to "Слабый снег",
    "SN" to "Снег",
    "DZ" to "Морось",
    "FG" to "Туман",
    "BR" to "Дымка",
    "HZ" to "Мгла",
    "FU" to "Дым",
    "SQ" to "Шквал",
    "DS" to "Пыльная буря",
    "SS" to "Песчаная буря",
    "BLSN" to "Низовая метель",
    "NSW" to "Без особых явлений (NSW)",
  )

  fun parse(
    rawTafInput: String,
    windUnit: WindSpeedUnit = WindSpeedUnit.KT,
    alertConfig: WeatherAlertConfig = WeatherAlertConfig(),
  ): DecodedTaf {
    val clean = rawTafInput.trim().replace(Regex("\\s+"), " ").trimEnd('=')
    if (clean.isBlank() ||
      clean.contains("нет данных", ignoreCase = true) ||
      clean.equals("TAF", ignoreCase = true)
    ) {
      return DecodedTaf(
        stationIcao = "----",
        issuedUtc = "--:--Z",
        validityWindow = "--",
        isCancelled = false,
        isNil = true,
        periods = emptyList(),
        rawTaf = clean,
      )
    }

    val tokens = clean.split(" ").filter { it.isNotBlank() }.map { it.uppercase(Locale.US) }
    var cursor = 0
    if (tokens.getOrNull(cursor) == "TAF") cursor++
    if (tokens.getOrNull(cursor) in setOf("AMD", "COR", "RTD")) cursor++

    val stationIcao = tokens.getOrNull(cursor)
      ?.takeIf { Regex("^[A-Z]{4}$").matches(it) }
      ?.also { cursor++ }
      ?: "----"

    val rawIssueToken = tokens.getOrNull(cursor)
      ?.takeIf { issueTimeRegex.matches(it) }
      ?.also { cursor++ }
    val issuedUtc = rawIssueToken?.let { tok ->
      "${tok.substring(2, 4)}:${tok.substring(4, 6)}Z (${tok.substring(0, 2)})"
    } ?: "--:--Z"

    val rawValidityToken = tokens.getOrNull(cursor)
      ?.takeIf { periodRangeRegex.matches(it) }
      ?.also { cursor++ }
    val validityWindow = rawValidityToken?.let { formatValidityRange(it) } ?: "--"

    val bodyTokens = tokens.drop(cursor)
    val isCancelled = bodyTokens.any { it == "CNL" }
    val isNil = bodyTokens.isEmpty() || bodyTokens.any { it == "NIL" }
    if (isCancelled || isNil) {
      return DecodedTaf(
        stationIcao = stationIcao,
        issuedUtc = issuedUtc,
        validityWindow = validityWindow,
        isCancelled = isCancelled,
        isNil = isNil,
        periods = emptyList(),
        rawTaf = clean,
      )
    }

    val segments = splitForecastSegments(bodyTokens, validityWindow)
    val parsedPeriods = segments.map { seg ->
      parseSegment(seg, windUnit, alertConfig)
    }

    return DecodedTaf(
      stationIcao = stationIcao,
      issuedUtc = issuedUtc,
      validityWindow = validityWindow,
      isCancelled = false,
      isNil = false,
      periods = parsedPeriods,
      rawTaf = clean,
    )
  }

  private data class RawSegment(
    val type: TafChangeType,
    val badgeLabel: String,
    val validityLabel: String,
    val tokens: List<String>,
    val rawSegment: String,
  )

  private fun splitForecastSegments(
    bodyTokens: List<String>,
    baseValidity: String,
  ): List<RawSegment> {
    val segments = mutableListOf<RawSegment>()
    val currentTokens = mutableListOf<String>()
    var currentType = TafChangeType.BASE
    var currentBadge = "BASE"
    var currentValidity = baseValidity
    var currentPrefixTokens = emptyList<String>()

    fun flushCurrent() {
      if (currentTokens.isNotEmpty() || currentType == TafChangeType.BASE) {
        val raw = (currentPrefixTokens + currentTokens).joinToString(" ").trim()
        if (raw.isNotBlank()) {
          segments.add(
            RawSegment(
              type = currentType,
              badgeLabel = currentBadge,
              validityLabel = currentValidity,
              tokens = currentTokens.toList(),
              rawSegment = raw,
            ),
          )
        }
      }
    }

    var i = 0
    while (i < bodyTokens.size) {
      val tok = bodyTokens[i]
      when {
        probTokenRegex.matches(tok) -> {
          flushCurrent()
          currentTokens.clear()
          val probPct = probTokenRegex.matchEntire(tok)!!.groupValues[1]
          val nextTok = bodyTokens.getOrNull(i + 1)
          if (nextTok == "TEMPO" || nextTok == "BECMG") {
            val rangeTok = bodyTokens.getOrNull(i + 2)?.takeIf { periodRangeRegex.matches(it) }
            currentType = TafChangeType.PROB
            currentBadge = "PROB$probPct $nextTok"
            currentValidity = rangeTok?.let { formatValidityRange(it) } ?: baseValidity
            currentPrefixTokens = listOfNotNull(tok, nextTok, rangeTok)
            i += if (rangeTok != null) 3 else 2
          } else {
            val rangeTok = nextTok?.takeIf { periodRangeRegex.matches(it) }
            currentType = TafChangeType.PROB
            currentBadge = "PROB$probPct"
            currentValidity = rangeTok?.let { formatValidityRange(it) } ?: baseValidity
            currentPrefixTokens = listOfNotNull(tok, rangeTok)
            i += if (rangeTok != null) 2 else 1
          }
        }
        tok == "TEMPO" || tok == "BECMG" -> {
          flushCurrent()
          currentTokens.clear()
          val rangeTok = bodyTokens.getOrNull(i + 1)?.takeIf { periodRangeRegex.matches(it) }
          currentType = if (tok == "TEMPO") TafChangeType.TEMPO else TafChangeType.BECMG
          currentBadge = tok
          currentValidity = rangeTok?.let { formatValidityRange(it) } ?: baseValidity
          currentPrefixTokens = listOfNotNull(tok, rangeTok)
          i += if (rangeTok != null) 2 else 1
        }
        fmTokenRegex.matches(tok) -> {
          flushCurrent()
          currentTokens.clear()
          val m = fmTokenRegex.matchEntire(tok)!!
          currentType = TafChangeType.FM
          currentBadge = "FM"
          currentValidity = "с ${m.groupValues[1]} ${m.groupValues[2]}:${m.groupValues[3]}Z"
          currentPrefixTokens = listOf(tok)
          i++
        }
        else -> {
          currentTokens.add(tok)
          i++
        }
      }
    }
    flushCurrent()
    return segments
  }

  private fun parseSegment(
    seg: RawSegment,
    windUnit: WindSpeedUnit,
    alertConfig: WeatherAlertConfig,
  ): TafForecastPeriod {
    val tokens = seg.tokens
    var hazardous = false
    var parsedWindKt: Int? = null
    var parsedGustKt: Int? = null
    var parsedVisMeters: Int? = null
    var windAlert = false
    var visAlert = false

    // Wind
    val windMatch = tokens.firstNotNullOfOrNull { windTokenRegex.matchEntire(it) }
    val windSummary = if (windMatch != null) {
      val dir = windMatch.groupValues[1]
      val rawSpeed = windMatch.groupValues[2].toIntOrNull() ?: 0
      val rawGust = windMatch.groupValues[4].takeIf { it.isNotEmpty() }?.toIntOrNull()
      val srcUnit = windMatch.groupValues[5]

      val speedKt = toKnots(rawSpeed, srcUnit)
      val gustKt = rawGust?.let { toKnots(it, srcUnit) }
      parsedWindKt = speedKt
      parsedGustKt = gustKt
      val peakWindKt = gustKt ?: speedKt
      if (alertConfig.tableAlertsEnabled && peakWindKt >= alertConfig.tableWindAlertKt) {
        windAlert = true
        hazardous = true
      } else if (speedKt >= 22 || (gustKt != null && gustKt >= 25)) {
        hazardous = true
      }

      val dispSpeed = UnitConverter.formatWindSpeedValue(speedKt, windUnit)
      val dirLabel = when {
        dir == "VRB" -> "VRB"
        dir == "000" && rawSpeed == 0 -> "Штиль"
        else -> "$dir°"
      }
      val gustSuffix = gustKt?.let { gKt ->
        " (порывы ${UnitConverter.formatWindSpeedValue(gKt, windUnit)} ${windUnit.label})"
      }.orEmpty()
      if (dir == "000" && rawSpeed == 0) {
        "Штиль"
      } else {
        "$dirLabel $dispSpeed ${windUnit.label}$gustSuffix"
      }
    } else {
      "Без изменений"
    }

    // Visibility
    val isCavok = tokens.any { it == "CAVOK" }
    val visMetersTok = tokens.firstNotNullOfOrNull { visMetersRegex.matchEntire(it)?.groupValues?.get(1)?.toIntOrNull() }
    val visSmTok = tokens.firstNotNullOfOrNull { visSmRegex.matchEntire(it)?.groupValues?.get(1) }
    val visibilitySummary = when {
      isCavok -> {
        parsedVisMeters = 9999
        "≥ 10 км (CAVOK)"
      }
      visMetersTok != null && visMetersTok >= 9999 -> {
        parsedVisMeters = 9999
        "≥ 10 км (9999 м)"
      }
      visMetersTok != null -> {
        parsedVisMeters = visMetersTok
        if (alertConfig.tableAlertsEnabled && visMetersTok <= alertConfig.tableVisibilityAlertMeters) {
          visAlert = true
          hazardous = true
        } else if (visMetersTok < 3000) {
          hazardous = true
        }
        "$visMetersTok м"
      }
      visSmTok != null -> {
        val approxMeters = parseStatuteMilesToMeters(visSmTok)
        parsedVisMeters = approxMeters
        if (approxMeters != null && alertConfig.tableAlertsEnabled && approxMeters <= alertConfig.tableVisibilityAlertMeters) {
          visAlert = true
          hazardous = true
        }
        visSmTok
      }
      else -> "Без изменений"
    }

    // Clouds (strictly in feet!)
    val cloudTokens = tokens.mapNotNull { tok ->
      val m = cloudRegex.matchEntire(tok) ?: return@mapNotNull null
      val cover = m.groupValues[1]
      val heightCode = m.groupValues[2]
      val cb = m.groupValues[3]
      val feet = heightCode.toIntOrNull()?.times(100)
      if (cover in setOf("BKN", "OVC", "VV") && feet != null && feet <= 1500) {
        hazardous = true
      }
      if (cb.isNotEmpty()) hazardous = true
      if (feet != null) {
        if (cb.isNotEmpty()) "$cover $feet ft $cb" else "$cover $feet ft"
      } else {
        "$cover $heightCode $cb".trim()
      }
    }
    val cloudsSummary = when {
      isCavok -> "CAVOK"
      cloudTokens.isNotEmpty() -> cloudTokens.joinToString(", ")
      tokens.any { it == "NSC" } -> "Без существенной облачности (NSC)"
      tokens.any { it == "SKC" || it == "CLR" } -> "Ясно (${tokens.first { it == "SKC" || it == "CLR" }})"
      else -> "Без изменений"
    }

    // Phenomena
    val phenomList = tokens.mapNotNull { tok ->
      if (tok == "CAVOK" || tok == "NOSIG" || tok == "NSC" || tok == "SKC" || tok == "CLR") return@mapNotNull null
      if (windTokenRegex.matches(tok) || visMetersRegex.matches(tok) || visSmRegex.matches(tok) || cloudRegex.matches(tok)) {
        return@mapNotNull null
      }
      if (tok.startsWith("TX") || tok.startsWith("TN")) return@mapNotNull null
      val decoded = decodePhenomenon(tok) ?: return@mapNotNull null
      if (tok.contains("TS") || tok.contains("FZ") || tok.contains("SQ") || tok.contains("FG") || tok.startsWith("+")) {
        hazardous = true
      }
      decoded
    }
    val phenomenaSummary = when {
      phenomList.isNotEmpty() -> phenomList.joinToString(", ")
      isCavok -> "Без явлений"
      else -> "Без особых явлений"
    }

    return TafForecastPeriod(
      type = seg.type,
      badgeLabel = seg.badgeLabel,
      validityLabel = seg.validityLabel,
      windSummary = windSummary,
      visibilitySummary = visibilitySummary,
      cloudsSummary = cloudsSummary,
      phenomenaSummary = phenomenaSummary,
      isHazardous = hazardous,
      rawSegment = seg.rawSegment,
      windSpeedKt = parsedWindKt,
      gustKt = parsedGustKt,
      visibilityMeters = parsedVisMeters,
      isWindAlert = windAlert,
      isVisibilityAlert = visAlert,
    )
  }

  private fun parseStatuteMilesToMeters(smToken: String): Int? {
    val clean = smToken.removeSuffix("SM").removePrefix("P").removePrefix("M")
    val miles = if (clean.contains("/")) {
      val parts = clean.split("/")
      val num = parts.getOrNull(0)?.toDoubleOrNull() ?: return null
      val den = parts.getOrNull(1)?.toDoubleOrNull()?.takeIf { it > 0 } ?: return null
      num / den
    } else {
      clean.toDoubleOrNull() ?: return null
    }
    return (miles * 1609.34).roundToInt().coerceAtMost(9999)
  }

  private fun decodePhenomenon(token: String): String? {
    for ((code, name) in phenomDictionary) {
      if (token == code) return "$name ($code)"
    }
    if (Regex("^[+-]?(VC)?(MI|PR|BC|DR|BL|SH|TS|FZ)?(DZ|RA|SN|SG|IC|PL|GR|GS|UP|BR|FG|FU|VA|DU|SA|HZ|PY|PO|SQ|FC|SS|DS)+$").matches(token)) {
      return token
    }
    return null
  }

  private fun toKnots(value: Int, srcUnit: String): Int = when (srcUnit) {
    "MPS" -> (value / KT_TO_MPS).roundToInt()
    "KMH" -> (value * KMH_TO_KT).roundToInt()
    else -> value
  }

  private fun formatValidityRange(token: String): String {
    val m = periodRangeRegex.matchEntire(token) ?: return token
    val d1 = m.groupValues[1]
    val h1 = m.groupValues[2]
    val d2 = m.groupValues[3]
    val h2 = m.groupValues[4]
    return if (d1 == d2) {
      "$h1:00Z – $h2:00Z ($d1)"
    } else {
      "$d1 $h1:00Z – $d2 $h2:00Z"
    }
  }
}
