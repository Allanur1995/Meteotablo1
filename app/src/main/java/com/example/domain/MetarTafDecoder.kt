package com.example.domain

import com.example.data.AirportCatalog
import com.example.model.AirportStation
import java.util.Locale
import kotlin.math.roundToInt

enum class DecodedTokenSeverity {
  NORMAL,
  CAUTION,
  HAZARD,
}

data class DecodedCodeItem(
  val rawCode: String,
  val category: String,
  val shortValue: String,
  val fullExplanation: String,
  val severity: DecodedTokenSeverity = DecodedTokenSeverity.NORMAL,
)

data class DecodedMessageResult(
  val messageType: String,
  val rawText: String,
  val plainSummaryParagraph: String,
  val items: List<DecodedCodeItem>,
)

object MetarTafDecoder {

  private const val KT_TO_MPS = 0.514444
  private const val KMH_TO_KT = 0.539957
  private const val FEET_TO_METERS = 0.3048

  private val windRegex = Regex("^(\\d{3}|VRB)(\\d{2,3})(G(\\d{2,3}))?(KT|MPS|KMH)$")
  private val varWindRegex = Regex("^(\\d{3})V(\\d{3})$")
  private val obsTimeRegex = Regex("^(\\d{2})(\\d{2})(\\d{2})Z$")
  private val tafPeriodRegex = Regex("^(\\d{2})(\\d{2})/(\\d{2})(\\d{2})$")
  private val fmRegex = Regex("^FM(\\d{2})(\\d{2})(\\d{2})$")
  private val probRegex = Regex("^PROB(30|40)$")
  private val visMetersRegex = Regex("^(\\d{4})(N|NE|E|SE|S|SW|W|NW|NDV)?$")
  private val visSmRegex = Regex("^([PM]?\\d+(?:/\\d+)?)SM$")
  private val rvrRegex = Regex("^R(\\d{2}[LRC]?)/([PM]?)(\\d{4})(?:V([PM]?)(\\d{4}))?([UDN]?)$")
  private val rwyStateRegex = Regex("^R(\\d{2}[LRC]?)/(CLRD|\\d{4})(\\d{2}|//)$")
  private val cloudRegex = Regex("^(FEW|SCT|BKN|OVC|VV)(\\d{3}|///)(CB|TCU)?$")
  private val tempDewRegex = Regex("^(M?\\d{2}|//)/(M?\\d{2}|//)$")
  private val tafTempRegex = Regex("^(TX|TN)(M?\\d{2})/(\\d{2})(\\d{2})Z$")
  private val qnhHpaRegex = Regex("^Q(\\d{4})$")
  private val altInHgRegex = Regex("^A(\\d{4})$")
  private val qfeRmkRegex = Regex("^QFE(\\d{3,4})(?:/(\\d{4}))?$")
  private val windShearAltRegex = Regex("^WS(\\d{3})/(\\d{3})(\\d{2,3})(G\\d{2,3})?(KT|MPS)$")
  private val wxRegex = Regex("^([+-]|VC)?((?:MI|PR|BC|DR|BL|SH|TS|FZ)?(?:DZ|RA|SN|SG|IC|PL|GR|GS|UP|BR|FG|FU|VA|DU|SA|HZ|PY|PO|SQ|FC|SS|DS|NSW))+$")
  private val recentWxRegex = Regex("^RE((?:MI|PR|BC|DR|BL|SH|TS|FZ)?(?:DZ|RA|SN|SG|IC|PL|GR|GS|UP|BR|FG|FU|VA|DU|SA|HZ|SQ|FC|SS|DS)+)$")

  fun decodeMetar(
    rawMetarInput: String,
    stationHint: AirportStation? = null,
  ): DecodedMessageResult {
    return decodeTokens(
      rawInput = rawMetarInput,
      defaultType = "METAR",
      stationHint = stationHint,
    )
  }

  fun decodeTaf(
    rawTafInput: String,
    stationHint: AirportStation? = null,
  ): DecodedMessageResult {
    return decodeTokens(
      rawInput = rawTafInput,
      defaultType = "TAF",
      stationHint = stationHint,
    )
  }

  fun formatPlainTextReport(
    rawMetar: String,
    rawTaf: String,
    stationHint: AirportStation? = null,
  ): String {
    val metar = decodeMetar(rawMetar, stationHint)
    val taf = decodeTaf(rawTaf, stationHint)
    return buildString {
      appendLine("═══ ОТКРЫТАЯ РАСШИФРОВКА METAR ═══")
      appendLine("Код: ${metar.rawText}")
      appendLine("Сводка простым языком:")
      appendLine(metar.plainSummaryParagraph)
      appendLine()
      metar.items.forEach { item ->
        appendLine("• [${item.rawCode}] (${item.category}): ${item.fullExplanation}")
      }
      if (taf.rawText.isNotBlank() && !taf.rawText.contains("нет данных", ignoreCase = true)) {
        appendLine()
        appendLine("═══ ОТКРЫТАЯ РАСШИФРОВКА TAF ═══")
        appendLine("Код: ${taf.rawText}")
        appendLine("Прогноз простым языком:")
        appendLine(taf.plainSummaryParagraph)
        appendLine()
        taf.items.forEach { item ->
          appendLine("• [${item.rawCode}] (${item.category}): ${item.fullExplanation}")
        }
      }
    }.trim()
  }

  private fun decodeTokens(
    rawInput: String,
    defaultType: String,
    stationHint: AirportStation?,
  ): DecodedMessageResult {
    val clean = rawInput.trim().replace(Regex("\\s+"), " ").trimEnd('=')
    if (clean.isBlank() || clean.contains("нет данных", ignoreCase = true)) {
      return DecodedMessageResult(
        messageType = defaultType,
        rawText = clean,
        plainSummaryParagraph = "Данные $defaultType отсутствуют или не загружены.",
        items = emptyList(),
      )
    }

    val tokens = clean.split(" ").filter { it.isNotBlank() }.map { it.uppercase(Locale.US) }
    val items = mutableListOf<DecodedCodeItem>()
    var inRemarks = false
    var icaoFound = false
    var detectedType = defaultType

    var i = 0
    while (i < tokens.size) {
      val token = tokens[i]

      if (token == "RMK") {
        inRemarks = true
        items.add(
          DecodedCodeItem(
            rawCode = token,
            category = "Ремарки (RMK)",
            shortValue = "Начало дополнительных замечаний",
            fullExplanation = "RMK — разделитель, после которого следуют национальные и местные замечания (например QFE, порывы ветра, облачность).",
          ),
        )
        i++
        continue
      }

      if (inRemarks) {
        items.add(decodeRemarkToken(token))
        i++
        continue
      }

      // Check compound WS Rxx / WS ALL RWY
      if (token == "WS" && i + 1 < tokens.size) {
        if (tokens[i + 1] == "ALL" && tokens.getOrNull(i + 2) == "RWY") {
          items.add(
            DecodedCodeItem(
              rawCode = "WS ALL RWY",
              category = "Сдвиг ветра",
              shortValue = "Опасный сдвиг ветра на всех ВПП",
              fullExplanation = "WS ALL RWY — зафиксирован или прогнозируется опасный сдвиг ветра в приземном слое на всех взлётно-посадочных полосах.",
              severity = DecodedTokenSeverity.HAZARD,
            ),
          )
          i += 3
          continue
        }
        val rwyTok = tokens[i + 1]
        if (Regex("^R\\d{2}[LRC]?$").matches(rwyTok)) {
          val rwyName = rwyTok.removePrefix("R")
          items.add(
            DecodedCodeItem(
              rawCode = "WS $rwyTok",
              category = "Сдвиг ветра",
              shortValue = "Сдвиг ветра на ВПП $rwyName",
              fullExplanation = "WS $rwyTok — сдвиг ветра (резкое изменение направления или скорости ветра на траектории захода/взлёта) на ВПП $rwyName.",
              severity = DecodedTokenSeverity.HAZARD,
            ),
          )
          i += 2
          continue
        }
      }

      // Check AT / FM / TL time modifiers inside TREND
      if ((token == "AT" || token == "FM" || token == "TL") && Regex("^\\d{4}$").matches(tokens.getOrNull(i + 1).orEmpty())) {
        val hhmm = tokens[i + 1]
        val hh = hhmm.substring(0, 2)
        val mm = hhmm.substring(2, 4)
        val prefixDesc = when (token) {
          "AT" -> "ровно в $hh:${mm}Z"
          "FM" -> "начиная с $hh:${mm}Z"
          else -> "до $hh:${mm}Z"
        }
        items.add(
          DecodedCodeItem(
            rawCode = "$token $hhmm",
            category = "Время изменения",
            shortValue = prefixDesc,
            fullExplanation = "$token $hhmm — уточнение времени ожидаемого изменения погоды: $prefixDesc (время UTC).",
          ),
        )
        i += 2
        continue
      }

      val item = when {
        token == "METAR" -> {
          detectedType = "METAR"
          DecodedCodeItem(
            rawCode = token,
            category = "Тип сводки",
            shortValue = "Регулярная метеосводка аэродрома",
            fullExplanation = "METAR — регулярная фактическая авиационная метеорологическая сводка, выпускаемая по расписанию (обычно каждые 30 или 60 минут).",
          )
        }
        token == "SPECI" -> {
          detectedType = "SPECI"
          DecodedCodeItem(
            rawCode = token,
            category = "Тип сводки",
            shortValue = "Специальная внеочередная сводка",
            fullExplanation = "SPECI — специальная метеосводка, выпущенная вне расписания из-за существенного ухудшения или улучшения погоды.",
            severity = DecodedTokenSeverity.CAUTION,
          )
        }
        token == "TAF" -> {
          detectedType = "TAF"
          DecodedCodeItem(
            rawCode = token,
            category = "Тип прогноза",
            shortValue = "Прогноз погоды по аэродрому (TAF)",
            fullExplanation = "TAF (Terminal Aerodrome Forecast) — официальный авиационный прогноз погоды по аэродрому на период обычно от 9 до 30 часов.",
          )
        }
        token == "AMD" -> DecodedCodeItem(
          rawCode = token,
          category = "Статус выпуска",
          shortValue = "Скорректированный прогноз",
          fullExplanation = "AMD (Amended) — скорректированный (уточнённый) прогноз TAF, заменяющий предыдущий выпуск.",
          severity = DecodedTokenSeverity.CAUTION,
        )
        token == "COR" -> DecodedCodeItem(
          rawCode = token,
          category = "Статус выпуска",
          shortValue = "Исправленная сводка",
          fullExplanation = "COR (Correction) — исправленная сводка/прогноз (устранена ошибка передачи или наблюдения).",
        )
        token == "RTD" -> DecodedCodeItem(
          rawCode = token,
          category = "Статус выпуска",
          shortValue = "Задержанная сводка (RTD)",
          fullExplanation = "RTD (Retarded) — задержанная метеосводка, переданная позже установленного срока.",
        )
        token == "AUTO" -> DecodedCodeItem(
          rawCode = token,
          category = "Режим наблюдения",
          shortValue = "Автоматическая метеостанция",
          fullExplanation = "AUTO — сводка сформирована полностью автоматической станцией без участия или коррекции метеоролога.",
        )
        token == "NIL" -> DecodedCodeItem(
          rawCode = token,
          category = "Статус данных",
          shortValue = "Сводка отсутствует (NIL)",
          fullExplanation = "NIL — данные сводки или прогноза отсутствуют.",
          severity = DecodedTokenSeverity.CAUTION,
        )
        token == "CNL" -> DecodedCodeItem(
          rawCode = token,
          category = "Статус прогноза",
          shortValue = "Прогноз отменён (CNL)",
          fullExplanation = "CNL (Cancelled) — ранее выпущенный прогноз TAF официально отменён.",
          severity = DecodedTokenSeverity.HAZARD,
        )
        !icaoFound && Regex("^[A-Z]{4}$").matches(token) && token !in setOf("CAVOK", "NOSIG", "TEMPO", "BECMG") -> {
          icaoFound = true
          val matched = stationHint?.takeIf { it.icao == token } ?: AirportCatalog.findKnown(token)
          val airportLabel = matched?.let { "${it.name} (${it.city}, выс. ${it.elevationMeters} м)" }
            ?: "Индекс ИКАО $token"
          DecodedCodeItem(
            rawCode = token,
            category = "Аэродром (ICAO)",
            shortValue = airportLabel,
            fullExplanation = if (matched != null) {
              "$token — четырёхбуквенный код ИКАО аэродрома: ${matched.name}, г. ${matched.city}, превышение ${matched.elevationMeters} м."
            } else {
              "$token — четырёхбуквенный международный идентификатор ИКАО метеостанции/аэродрома."
            },
          )
        }
        obsTimeRegex.matches(token) -> decodeObsTimeToken(token)
        tafPeriodRegex.matches(token) -> decodeTafPeriodToken(token)
        fmRegex.matches(token) -> decodeFmToken(token)
        probRegex.matches(token) -> decodeProbToken(token)
        token == "TEMPO" -> DecodedCodeItem(
          rawCode = token,
          category = "Группа изменений",
          shortValue = "Временные колебания погоды",
          fullExplanation = "TEMPO — ожидаются временные флуктуации метеоусловий продолжительностью каждый раз менее 1 часа (и суммарно менее половины периода).",
          severity = DecodedTokenSeverity.CAUTION,
        )
        token == "BECMG" -> DecodedCodeItem(
          rawCode = token,
          category = "Группа изменений",
          shortValue = "Постепенный устойчивый переход",
          fullExplanation = "BECMG (Becoming) — регулярное или нерегулярное постепенное изменение метеоусловий, которые после указанного окна станут постоянными.",
          severity = DecodedTokenSeverity.CAUTION,
        )
        token == "NOSIG" -> DecodedCodeItem(
          rawCode = token,
          category = "Тренд (TREND)",
          shortValue = "Без существенных изменений",
          fullExplanation = "NOSIG (No Significant Change) — существенных изменений погоды в ближайшие 2 часа после времени наблюдения не ожидается.",
        )
        windRegex.matches(token) -> decodeWindToken(token)
        varWindRegex.matches(token) -> decodeVariableWindToken(token)
        windShearAltRegex.matches(token) -> decodeWindShearAltToken(token)
        token == "CAVOK" -> DecodedCodeItem(
          rawCode = token,
          category = "Видимость и облака",
          shortValue = "Видимость ≥ 10 км, нет значимых облаков и явлений",
          fullExplanation = "CAVOK (Ceiling And Visibility OK): видимость 10 км и более, отсутствуют облака ниже 1500 м (5000 ft) и кучево-дождевые (CB/TCU), нет опасных явлений погоды.",
        )
        visMetersRegex.matches(token) -> decodeVisibilityMetersToken(token)
        visSmRegex.matches(token) -> decodeVisibilitySmToken(token)
        rvrRegex.matches(token) -> decodeRvrToken(token)
        rwyStateRegex.matches(token) -> decodeRunwayStateToken(token)
        cloudRegex.matches(token) -> decodeCloudToken(token)
        token in setOf("NSC", "SKC", "CLR", "NCD") -> decodeSkyClearToken(token)
        tempDewRegex.matches(token) -> decodeTempDewToken(token)
        tafTempRegex.matches(token) -> decodeTafTempToken(token)
        qnhHpaRegex.matches(token) -> decodeQnhToken(token)
        altInHgRegex.matches(token) -> decodeAltInHgToken(token)
        token == "NSW" -> DecodedCodeItem(
          rawCode = token,
          category = "Явления погоды",
          shortValue = "Окончание особых явлений погоды",
          fullExplanation = "NSW (No Significant Weather) — прекращение ранее наблюдавшихся или прогнозировавшихся особых явлений погоды.",
        )
        recentWxRegex.matches(token) -> decodeRecentWeatherToken(token)
        wxRegex.matches(token) -> decodeWeatherPhenomenaToken(token)
        else -> DecodedCodeItem(
          rawCode = token,
          category = "Доп. группа",
          shortValue = token,
          fullExplanation = "$token — специальная/дополнительная группа сводки (нестандартный или региональный код).",
        )
      }
      items.add(item)
      i++
    }

    val summary = buildPlainSummaryParagraph(detectedType, items)
    return DecodedMessageResult(
      messageType = detectedType,
      rawText = clean,
      plainSummaryParagraph = summary,
      items = items,
    )
  }

  private fun decodeObsTimeToken(token: String): DecodedCodeItem {
    val m = obsTimeRegex.matchEntire(token)!!
    val day = m.groupValues[1]
    val hh = m.groupValues[2]
    val mm = m.groupValues[3]
    return DecodedCodeItem(
      rawCode = token,
      category = "Дата и время (UTC)",
      shortValue = "$day-е число, $hh:$mm UTC",
      fullExplanation = "$token — время наблюдения/выпуска: $day-й день текущего месяца в $hh часов $mm минут по всемирному координированному времени (UTC / Zulu).",
    )
  }

  private fun decodeTafPeriodToken(token: String): DecodedCodeItem {
    val m = tafPeriodRegex.matchEntire(token)!!
    val d1 = m.groupValues[1]
    val h1 = m.groupValues[2]
    val d2 = m.groupValues[3]
    val h2 = m.groupValues[4]
    val short = "с $d1-го $h1:00Z до $d2-го $h2:00Z"
    return DecodedCodeItem(
      rawCode = token,
      category = "Период действия",
      shortValue = short,
      fullExplanation = "$token — временной интервал прогноза: с $h1:00 UTC $d1-го числа по $h2:00 UTC $d2-го числа.",
    )
  }

  private fun decodeFmToken(token: String): DecodedCodeItem {
    val m = fmRegex.matchEntire(token)!!
    val day = m.groupValues[1]
    val hh = m.groupValues[2]
    val mm = m.groupValues[3]
    return DecodedCodeItem(
      rawCode = token,
      category = "Смена с времени (FM)",
      shortValue = "С $day-го $hh:${mm}Z",
      fullExplanation = "$token (From) — быстрое и постоянное изменение всех погодных параметров начиная с $hh:$mm UTC $day-го числа.",
      severity = DecodedTokenSeverity.CAUTION,
    )
  }

  private fun decodeProbToken(token: String): DecodedCodeItem {
    val pct = probRegex.matchEntire(token)!!.groupValues[1]
    return DecodedCodeItem(
      rawCode = token,
      category = "Вероятность",
      shortValue = "Вероятность $pct%",
      fullExplanation = "$token — прогнозируемое изменение погоды ожидается с вероятностью $pct%.",
      severity = DecodedTokenSeverity.CAUTION,
    )
  }

  private fun decodeWindToken(token: String): DecodedCodeItem {
    val m = windRegex.matchEntire(token)!!
    val dir = m.groupValues[1]
    val spd = m.groupValues[2].toIntOrNull() ?: 0
    val gust = m.groupValues[4].takeIf { it.isNotEmpty() }?.toIntOrNull()
    val unit = m.groupValues[5]

    val speedKt = toKnots(spd, unit)
    val speedMps = (speedKt * KT_TO_MPS).roundToInt()
    val gustKt = gust?.let { toKnots(it, unit) }
    val gustMps = gustKt?.let { (it * KT_TO_MPS).roundToInt() }

    if (dir == "000" && spd == 0) {
      return DecodedCodeItem(
        rawCode = token,
        category = "Приземный ветер",
        shortValue = "Штиль (0 узлов / 0 м/с)",
        fullExplanation = "$token — полный штиль у земли (направление не определяется, скорость 0).",
      )
    }

    val dirLabel = if (dir == "VRB") "переменный по направлению (VRB)" else "из направления $dir° (${compassSectorName(dir.toIntOrNull() ?: 0)})"
    val spdLabel = "$speedKt узлов ($speedMps м/с)"
    val gustText = if (gustKt != null) ", порывы до $gustKt узлов ($gustMps м/с)" else ""
    val severity = when {
      (gustKt ?: speedKt) >= 25 || speedKt >= 20 -> DecodedTokenSeverity.HAZARD
      (gustKt ?: speedKt) >= 15 -> DecodedTokenSeverity.CAUTION
      else -> DecodedTokenSeverity.NORMAL
    }
    return DecodedCodeItem(
      rawCode = token,
      category = "Приземный ветер",
      shortValue = "${if (dir == "VRB") "VRB" else "$dir°"} · $spdLabel${gustKt?.let { ", G$it kt" } ?: ""}",
      fullExplanation = "$token — ветер $dirLabel, средняя скорость $spdLabel$gustText (исходно в $unit).",
      severity = severity,
    )
  }

  private fun decodeVariableWindToken(token: String): DecodedCodeItem {
    val m = varWindRegex.matchEntire(token)!!
    val from = m.groupValues[1]
    val to = m.groupValues[2]
    return DecodedCodeItem(
      rawCode = token,
      category = "Сектор ветра",
      shortValue = "Меняется от $from° до $to°",
      fullExplanation = "$token — направление ветра неустойчивое и колеблется в секторе от $from° до $to° (размах ≥ 60° при скорости > 3 kt).",
      severity = DecodedTokenSeverity.CAUTION,
    )
  }

  private fun decodeWindShearAltToken(token: String): DecodedCodeItem {
    val m = windShearAltRegex.matchEntire(token)!!
    val altFt = (m.groupValues[1].toIntOrNull() ?: 0) * 100
    val altM = (altFt * FEET_TO_METERS).roundToInt()
    val dir = m.groupValues[2]
    val spd = m.groupValues[3]
    val unit = m.groupValues[5]
    return DecodedCodeItem(
      rawCode = token,
      category = "Сдвиг ветра (LLWS)",
      shortValue = "На $altFt ft ($altM м): $dir° $spd $unit",
      fullExplanation = "$token — неконвективный сдвиг ветра на высоте $altFt футов ($altM м): ветер $dir° скоростью $spd $unit.",
      severity = DecodedTokenSeverity.HAZARD,
    )
  }

  private fun decodeVisibilityMetersToken(token: String): DecodedCodeItem {
    val m = visMetersRegex.matchEntire(token)!!
    val meters = m.groupValues[1].toIntOrNull() ?: 0
    val suffix = m.groupValues[2]
    if (meters >= 9999) {
      return DecodedCodeItem(
        rawCode = token,
        category = "Видимость (MOR)",
        shortValue = "10 км и более (отличная)",
        fullExplanation = "$token — преобладающая горизонтальная видимость у земли составляет 10 км и более.",
      )
    }
    val dirSuffixText = when (suffix) {
      "NDV" -> " (без указания направленности — датчик)"
      "" -> ""
      else -> " в направлении $suffix"
    }
    val severity = when {
      meters < 1500 -> DecodedTokenSeverity.HAZARD
      meters < 5000 -> DecodedTokenSeverity.CAUTION
      else -> DecodedTokenSeverity.NORMAL
    }
    val kmStr = if (meters >= 1000 && meters % 1000 == 0) " (${meters / 1000} км)" else ""
    return DecodedCodeItem(
      rawCode = token,
      category = "Видимость (MOR)",
      shortValue = "$meters м$kmStr$dirSuffixText",
      fullExplanation = "$token — горизонтальная метеорологическая оптическая дальность видимости равна $meters метров$kmStr$dirSuffixText.",
      severity = severity,
    )
  }

  private fun decodeVisibilitySmToken(token: String): DecodedCodeItem {
    val clean = token.removeSuffix("SM")
    val qual = when {
      clean.startsWith("P") -> "более "
      clean.startsWith("M") -> "менее "
      else -> ""
    }
    val valStr = clean.removePrefix("P").removePrefix("M")
    val miles = parseFraction(valStr) ?: 0.0
    val approxMeters = (miles * 1609.34).roundToInt()
    val severity = when {
      approxMeters < 1500 -> DecodedTokenSeverity.HAZARD
      approxMeters < 5000 -> DecodedTokenSeverity.CAUTION
      else -> DecodedTokenSeverity.NORMAL
    }
    return DecodedCodeItem(
      rawCode = token,
      category = "Видимость (SM)",
      shortValue = "$qual$valStr сухопутных миль (~$approxMeters м)",
      fullExplanation = "$token — горизонтальная видимость составляет $qual$valStr статутных миль (примерно $approxMeters метров).",
      severity = severity,
    )
  }

  private fun decodeRvrToken(token: String): DecodedCodeItem {
    val m = rvrRegex.matchEntire(token)!!
    val rwy = m.groupValues[1]
    val p1 = when (m.groupValues[2]) {
      "P" -> "более "
      "M" -> "менее "
      else -> ""
    }
    val v1 = m.groupValues[3].toIntOrNull() ?: 0
    val p2 = m.groupValues[4]
    val v2 = m.groupValues[5]
    val trend = when (m.groupValues[6]) {
      "U" -> ", тенденция к увеличению (U)"
      "D" -> ", тенденция к уменьшению (D)"
      "N" -> ", без изменений (N)"
      else -> ""
    }
    val rangeText = if (v2.isNotEmpty()) {
      "колеблется от $p1$v1 м до ${if (p2 == "P") "более " else if (p2 == "M") "менее " else ""}$v2 м"
    } else {
      "$p1$v1 м"
    }
    val severity = when {
      v1 < 550 -> DecodedTokenSeverity.HAZARD
      v1 < 1200 -> DecodedTokenSeverity.CAUTION
      else -> DecodedTokenSeverity.NORMAL
    }
    return DecodedCodeItem(
      rawCode = token,
      category = "Видимость на ВПП (RVR)",
      shortValue = "ВПП $rwy: $rangeText",
      fullExplanation = "$token — дальность видимости на ВПП $rwy составляет $rangeText$trend.",
      severity = severity,
    )
  }

  private fun decodeRunwayStateToken(token: String): DecodedCodeItem {
    val m = rwyStateRegex.matchEntire(token)!!
    val rwyRaw = m.groupValues[1]
    val rwyDesc = when (rwyRaw) {
      "88" -> "для всех ВПП"
      "99" -> "повтор предыдущего отчёта (ВПП)"
      else -> "ВПП $rwyRaw"
    }
    val stateCode = m.groupValues[2]
    val frictionStr = m.groupValues[3]
    val frictionDesc = when {
      frictionStr == "//" -> "сцепление не измерялось"
      else -> {
        val code = frictionStr.toIntOrNull()
        when {
          code == null -> "код сцепления $frictionStr"
          code in 10..90 -> "коэффициент сцепления ${String.format(Locale.US, "%.2f", code / 100.0)} (${brakingQualityLabel(code)})"
          code == 91 -> "торможение плохое (0.25)"
          code == 92 -> "торможение ниже среднего (0.28)"
          code == 93 -> "торможение среднее (0.33)"
          code == 94 -> "торможение выше среднего (0.38)"
          code == 95 -> "торможение хорошее (0.45)"
          else -> "код $frictionStr"
        }
      }
    }
    if (stateCode == "CLRD") {
      return DecodedCodeItem(
        rawCode = token,
        category = "Состояние ВПП",
        shortValue = "$rwyDesc чистая · $frictionDesc",
        fullExplanation = "$token — покрытие $rwyDesc очищено/сухое (CLRD), $frictionDesc.",
      )
    }
    val deposit = when (stateCode[0]) {
      '0' -> "чисто и сухо"
      '1' -> "влажно"
      '2' -> "мокро или лужи"
      '3' -> "иней или изморозь"
      '4' -> "сухой снег"
      '5' -> "мокрый снег"
      '6' -> "слякоть"
      '7' -> "лёд"
      '8' -> "уплотнённый снег"
      '9' -> "замёрзшие колеи/неровности"
      else -> "состояние не сообщается"
    }
    val severity = if (stateCode[0] in listOf('6', '7', '8', '9')) DecodedTokenSeverity.HAZARD else DecodedTokenSeverity.NORMAL
    return DecodedCodeItem(
      rawCode = token,
      category = "Состояние ВПП",
      shortValue = "$rwyDesc: $deposit · $frictionDesc",
      fullExplanation = "$token — $rwyDesc: тип покрытия «$deposit», $frictionDesc.",
      severity = severity,
    )
  }

  private fun decodeCloudToken(token: String): DecodedCodeItem {
    val m = cloudRegex.matchEntire(token)!!
    val cover = m.groupValues[1]
    val heightCode = m.groupValues[2]
    val modifier = m.groupValues[3]

    val coverText = when (cover) {
      "FEW" -> "Незначительная (1–2 октанта)"
      "SCT" -> "Рассеянная (3–4 октанта)"
      "BKN" -> "Разорванная (5–7 октантов)"
      "OVC" -> "Сплошная (8 октантов)"
      "VV" -> "Вертикальная видимость (небо скрыто)"
      else -> cover
    }
    val heightFt = heightCode.toIntOrNull()?.times(100)
    val heightM = heightFt?.let { (it * FEET_TO_METERS).roundToInt() }
    val altText = if (heightFt != null) {
      "на высоте $heightFt ft (~$heightM м)"
    } else {
      "высота не измерена (///)"
    }
    val modText = when (modifier) {
      "CB" -> ", кучево-дождевая (CB — гроза/турбулентность)"
      "TCU" -> ", мощно-кучевая (TCU)"
      else -> ""
    }
    val severity = when {
      modifier == "CB" -> DecodedTokenSeverity.HAZARD
      cover in setOf("BKN", "OVC", "VV") && (heightFt ?: 99999) <= 1000 -> DecodedTokenSeverity.HAZARD
      cover in setOf("BKN", "OVC", "VV") && (heightFt ?: 99999) <= 2000 -> DecodedTokenSeverity.CAUTION
      modifier == "TCU" -> DecodedTokenSeverity.CAUTION
      else -> DecodedTokenSeverity.NORMAL
    }
    return DecodedCodeItem(
      rawCode = token,
      category = "Облачность",
      shortValue = "$cover: ${heightFt?.let { "$it ft" } ?: "///"} $modifier".trim(),
      fullExplanation = "$token — $coverText $altText$modText.",
      severity = severity,
    )
  }

  private fun decodeSkyClearToken(token: String): DecodedCodeItem {
    val explanation = when (token) {
      "NSC" -> "NSC (No Significant Cloud) — значимая облачность ниже 1500 м (5000 ft) отсутствует, нет CB/TCU."
      "SKC" -> "SKC (Sky Clear) — ясное небо, облаков нет (0 октантов)."
      "CLR" -> "CLR (Clear) — на автоматической станции не зафиксировано облаков ниже 12 000 ft."
      else -> "NCD (No Cloud Detected) — автоматическим датчиком облачность не обнаружена."
    }
    return DecodedCodeItem(
      rawCode = token,
      category = "Облачность",
      shortValue = "Ясно / без значимой облачности",
      fullExplanation = explanation,
    )
  }

  private fun decodeTempDewToken(token: String): DecodedCodeItem {
    val m = tempDewRegex.matchEntire(token)!!
    val t = parseSignedTemp(m.groupValues[1])
    val dp = parseSignedTemp(m.groupValues[2])
    val tStr = t?.let { formatSignedC(it) } ?: "//"
    val dpStr = dp?.let { formatSignedC(it) } ?: "//"
    val spread = if (t != null && dp != null) t - dp else null
    val spreadNote = when {
      spread == null -> ""
      spread <= 2 -> " (дефицит $spread °C — высокая влажность, риск тумана)"
      else -> " (дефицит точки росы $spread °C)"
    }
    val severity = if (spread != null && spread <= 2) DecodedTokenSeverity.CAUTION else DecodedTokenSeverity.NORMAL
    return DecodedCodeItem(
      rawCode = token,
      category = "Температура / Точка росы",
      shortValue = "T $tStr · DP $dpStr",
      fullExplanation = "$token — температура воздуха $tStr, температура точки росы $dpStr$spreadNote.",
      severity = severity,
    )
  }

  private fun decodeTafTempToken(token: String): DecodedCodeItem {
    val m = tafTempRegex.matchEntire(token)!!
    val isMax = m.groupValues[1] == "TX"
    val temp = parseSignedTemp(m.groupValues[2])
    val day = m.groupValues[3]
    val hh = m.groupValues[4]
    val label = if (isMax) "Максимальная" else "Минимальная"
    val tempStr = temp?.let { formatSignedC(it) } ?: "--"
    return DecodedCodeItem(
      rawCode = token,
      category = "Прогноз температуры (TAF)",
      shortValue = "$label $tempStr ($day $hh:00Z)",
      fullExplanation = "$token — прогнозируемая ${label.lowercase(Locale("ru"))} температура воздуха $tempStr ожидается к $hh:00 UTC $day-го числа.",
    )
  }

  private fun decodeQnhToken(token: String): DecodedCodeItem {
    val hpa = qnhHpaRegex.matchEntire(token)!!.groupValues[1].toInt()
    val inHg = UnitConverter.convertHpaToInHg(hpa)
    val mmHg = UnitConverter.convertHpaToMmHg(hpa)
    return DecodedCodeItem(
      rawCode = token,
      category = "Давление QNH",
      shortValue = "$hpa гПа · ${String.format(Locale.US, "%.2f", inHg)} inHg",
      fullExplanation = "$token — приведённое к среднему уровню моря давление QNH = $hpa гПа (${String.format(Locale.US, "%.2f", inHg)} дюймов рт. ст. / $mmHg мм рт. ст.).",
    )
  }

  private fun decodeAltInHgToken(token: String): DecodedCodeItem {
    val rawHundreds = altInHgRegex.matchEntire(token)!!.groupValues[1].toInt()
    val inHg = rawHundreds / 100.0
    val hpa = UnitConverter.convertInHgToHpa(inHg).roundToInt()
    val mmHg = UnitConverter.convertHpaToMmHg(hpa)
    return DecodedCodeItem(
      rawCode = token,
      category = "Давление (Altimeter)",
      shortValue = "${String.format(Locale.US, "%.2f", inHg)} inHg · $hpa гПа",
      fullExplanation = "$token — установка высотомера QNH = ${String.format(Locale.US, "%.2f", inHg)} inHg (эквивалент $hpa гПа / $mmHg мм рт. ст.).",
    )
  }

  private fun decodeWeatherPhenomenaToken(token: String): DecodedCodeItem {
    val (decoded, isHazard) = decodeWeatherCodePlain(token)
    return DecodedCodeItem(
      rawCode = token,
      category = "Явления погоды",
      shortValue = decoded,
      fullExplanation = "$token — $decoded.",
      severity = if (isHazard) DecodedTokenSeverity.HAZARD else DecodedTokenSeverity.CAUTION,
    )
  }

  private fun decodeRecentWeatherToken(token: String): DecodedCodeItem {
    val inner = recentWxRegex.matchEntire(token)!!.groupValues[1]
    val (decoded, isHazard) = decodeWeatherCodePlain(inner)
    return DecodedCodeItem(
      rawCode = token,
      category = "Недавняя погода",
      shortValue = "Недавно: $decoded",
      fullExplanation = "$token — недавнее (окончившееся перед выпуском сводки) явление погоды: $decoded.",
      severity = if (isHazard) DecodedTokenSeverity.CAUTION else DecodedTokenSeverity.NORMAL,
    )
  }

  private fun decodeRemarkToken(token: String): DecodedCodeItem {
    qfeRmkRegex.matchEntire(token)?.let { m ->
      val rawP = m.groupValues[1].toIntOrNull()
      if (rawP != null) {
        val desc = if (rawP < 900) {
          val hpa = (rawP / 0.750062).roundToInt()
          "Давление на уровне ВПП (QFE) = $rawP мм рт. ст. (~$hpa гПа)"
        } else {
          val mm = UnitConverter.convertHpaToMmHg(rawP)
          "Давление на уровне ВПП (QFE) = $rawP гПа ($mm мм рт. ст.)"
        }
        return DecodedCodeItem(
          rawCode = token,
          category = "Ремарка QFE",
          shortValue = desc,
          fullExplanation = "$token — местное давление на аэродроме (QFE): $desc.",
        )
      }
    }
    return when {
      token == "NOSIG" -> DecodedCodeItem(
        rawCode = token,
        category = "Ремарка / Тренд",
        shortValue = "Без существенных изменений",
        fullExplanation = "NOSIG — значительных изменений в ближайшие 2 часа не ожидается.",
      )
      token == "AO1" -> DecodedCodeItem(
        rawCode = token,
        category = "Ремарка станции",
        shortValue = "Автостанция без датчика типа осадков",
        fullExplanation = "AO1 — автоматизированная станция без дискриминатора осадков.",
      )
      token == "AO2" -> DecodedCodeItem(
        rawCode = token,
        category = "Ремарка станции",
        shortValue = "Автостанция с датчиком типа осадков",
        fullExplanation = "AO2 — автоматизированная станция с датчиком определения типа осадков (дождь/снег).",
      )
      token.startsWith("SLP") && token.length == 6 -> DecodedCodeItem(
        rawCode = token,
        category = "Ремарка давления (SLP)",
        shortValue = "Давление на уровне моря",
        fullExplanation = "$token — давление на уровне моря с точностью до десятых долей гПа.",
      )
      else -> DecodedCodeItem(
        rawCode = token,
        category = "Ремарка",
        shortValue = token,
        fullExplanation = "$token — дополнительная группа раздела ремарок (RMK).",
      )
    }
  }

  private fun decodeWeatherCodePlain(token: String): Pair<String, Boolean> {
    var rest = token
    val intensity = when {
      rest.startsWith("+") -> {
        rest = rest.drop(1)
        "Сильные/интенсивные: "
      }
      rest.startsWith("-") -> {
        rest = rest.drop(1)
        "Слабые: "
      }
      rest.startsWith("VC") -> {
        rest = rest.drop(2)
        "В окрестностях аэродрома: "
      }
      else -> ""
    }

    val descriptor = when {
      rest.startsWith("TS") -> { rest = rest.drop(2); "гроза с осадками: " }
      rest.startsWith("SH") -> { rest = rest.drop(2); "ливневые: " }
      rest.startsWith("FZ") -> { rest = rest.drop(2); "переохлаждённые (обледенение): " }
      rest.startsWith("BL") -> { rest = rest.drop(2); "низовой перенос / метель: " }
      rest.startsWith("DR") -> { rest = rest.drop(2); "поземок: " }
      rest.startsWith("MI") -> { rest = rest.drop(2); "поземный: " }
      rest.startsWith("BC") -> { rest = rest.drop(2); "клочья: " }
      rest.startsWith("PR") -> { rest = rest.drop(2); "частичный: " }
      else -> ""
    }

    val parts = mutableListOf<String>()
    while (rest.length >= 2) {
      val chunk = rest.take(2)
      rest = rest.drop(2)
      parts.add(
        when (chunk) {
          "RA" -> "дождь"
          "SN" -> "снег"
          "DZ" -> "морось"
          "SG" -> "снежные зёрна"
          "IC" -> "ледяные иглы"
          "PL" -> "ледяная крупа"
          "GR" -> "град"
          "GS" -> "мелкий град / снежная крупа"
          "UP" -> "неопознанные осадки"
          "FG" -> "туман"
          "BR" -> "дымка"
          "HZ" -> "сухая мгла"
          "FU" -> "дым"
          "VA" -> "вулканический пепел"
          "DU" -> "пыль в воздухе"
          "SA" -> "песок"
          "PY" -> "водяная пыль / брызги"
          "PO" -> "пыльные/песчаные вихри"
          "SQ" -> "шквал"
          "FC" -> "смерч / воронкообразное облако"
          "SS" -> "песчаная буря"
          "DS" -> "пыльная буря"
          else -> chunk
        },
      )
    }

    val bodyText = if (parts.isEmpty() && descriptor.isNotEmpty()) {
      descriptor.trimEnd(':', ' ')
    } else {
      (descriptor + parts.joinToString(" и ")).trimEnd(':', ' ')
    }
    val text = (intensity + bodyText).trim().replaceFirstChar { it.uppercaseChar() }
    val isHazard = token.contains("TS") || token.contains("FZ") || token.contains("FG") ||
      token.contains("GR") || token.contains("SQ") || token.contains("SS") ||
      token.contains("DS") || token.contains("FC") || token.startsWith("+")
    return text to isHazard
  }

  private fun buildPlainSummaryParagraph(
    messageType: String,
    items: List<DecodedCodeItem>,
  ): String {
    if (items.isEmpty()) return "Нет данных для расшифровки."
    val sentences = mutableListOf<String>()
    val airport = items.firstOrNull { it.category.startsWith("Аэродром") }?.shortValue
    val time = items.firstOrNull { it.category.startsWith("Дата и время") }?.shortValue
    val header = listOfNotNull(
      if (messageType == "TAF") "Прогноз TAF" else "Фактическая сводка $messageType",
      airport?.let { "для $it" },
      time?.let { "($it)" },
    ).joinToString(" ")
    sentences.add("$header.")

    items.firstOrNull { it.category.startsWith("Период действия") }?.let {
      sentences.add("Срок действия прогноза: ${it.shortValue}.")
    }
    items.firstOrNull { it.category.startsWith("Приземный ветер") }?.let {
      sentences.add("Ветер: ${it.shortValue}.")
    }
    items.firstOrNull { it.category.startsWith("Видимость") }?.let {
      sentences.add("Видимость: ${it.shortValue}.")
    }
    val wxItems = items.filter { it.category == "Явления погоды" }
    if (wxItems.isNotEmpty()) {
      sentences.add("Явления: ${wxItems.joinToString("; ") { it.shortValue }}.")
    }
    val cloudItems = items.filter { it.category == "Облачность" }
    if (cloudItems.isNotEmpty()) {
      sentences.add("Облачность: ${cloudItems.joinToString("; ") { it.shortValue }}.")
    }
    items.firstOrNull { it.category.startsWith("Температура") }?.let {
      sentences.add("Температура и точка росы: ${it.shortValue}.")
    }
    items.firstOrNull { it.category.startsWith("Давление") }?.let {
      sentences.add("Давление: ${it.shortValue}.")
    }
    val changeCount = items.count {
      it.category == "Группа изменений" || it.category.startsWith("Смена с времени")
    }
    if (changeCount > 0) {
      sentences.add("В прогнозе указано групп изменения условий: $changeCount.")
    }
    items.firstOrNull { it.rawCode == "NOSIG" }?.let {
      sentences.add("В ближайшие 2 часа значительных изменений не ожидается (NOSIG).")
    }
    return sentences.joinToString(" ")
  }

  private fun parseSignedTemp(raw: String): Int? = when {
    raw == "//" -> null
    raw.startsWith("M") -> raw.drop(1).toIntOrNull()?.let { -it }
    else -> raw.toIntOrNull()
  }

  private fun formatSignedC(t: Int): String = if (t > 0) "+$t °C" else "$t °C"

  private fun parseFraction(str: String): Double? {
    return if (str.contains("/")) {
      val parts = str.split("/")
      val a = parts.getOrNull(0)?.toDoubleOrNull() ?: return null
      val b = parts.getOrNull(1)?.toDoubleOrNull()?.takeIf { it != 0.0 } ?: return null
      a / b
    } else {
      str.toDoubleOrNull()
    }
  }

  private fun toKnots(value: Int, srcUnit: String): Int = when (srcUnit) {
    "MPS" -> (value / KT_TO_MPS).roundToInt()
    "KMH" -> (value * KMH_TO_KT).roundToInt()
    else -> value
  }

  private fun compassSectorName(deg: Int): String {
    val norm = ((deg % 360) + 360) % 360
    return when {
      norm < 23 || norm >= 338 -> "северный"
      norm < 68 -> "северо-восточный"
      norm < 113 -> "восточный"
      norm < 158 -> "юго-восточный"
      norm < 203 -> "южный"
      norm < 248 -> "юго-западный"
      norm < 293 -> "западный"
      else -> "северо-западный"
    }
  }

  private fun brakingQualityLabel(code: Int): String = when {
    code >= 40 -> "хорошее торможение"
    code >= 36 -> "от среднего к хорошему"
    code >= 30 -> "среднее торможение"
    code >= 26 -> "от плохого к среднему"
    else -> "плохое торможение"
  }
}
