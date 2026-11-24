package com.example.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.example.domain.AirportMapProjection
import com.example.domain.FlightCategory
import com.example.domain.UnitConverter
import com.example.domain.WeatherExportPayload
import com.example.domain.WeatherReportFormatter
import com.example.model.AuthorBrand
import com.example.model.PressureUnit
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlin.math.abs

object AndroidWeatherReportExporter {

  private const val PAGE_WIDTH = 595
  private const val PAGE_HEIGHT = 842
  private const val MARGIN_X = 34f
  private const val CONTENT_WIDTH = PAGE_WIDTH - MARGIN_X * 2f

  fun exportTextToUri(
    context: Context,
    uri: Uri,
    payload: WeatherExportPayload,
  ): Result<Unit> = runCatching {
    val stream = context.contentResolver.openOutputStream(uri)
      ?: error("Не удалось открыть файл для записи")
    stream.use { out ->
      writeTextSummary(out, payload)
    }
  }

  fun exportPdfToUri(
    context: Context,
    uri: Uri,
    payload: WeatherExportPayload,
  ): Result<Unit> = runCatching {
    val stream = context.contentResolver.openOutputStream(uri)
      ?: error("Не удалось открыть PDF-файл для записи")
    stream.use { out ->
      writePdfSummary(out, payload)
    }
  }

  fun writeTextSummary(
    outputStream: OutputStream,
    payload: WeatherExportPayload,
  ) {
    val text = WeatherReportFormatter.buildTextSummary(payload)
    outputStream.write(text.toByteArray(StandardCharsets.UTF_8))
    outputStream.flush()
  }

  fun writePdfSummary(
    outputStream: OutputStream,
    payload: WeatherExportPayload,
  ) {
    val renderedNative = runCatching {
      writeNativePdfDocument(outputStream, payload)
    }
    if (renderedNative.isSuccess) return

    val error = renderedNative.exceptionOrNull()
    val isMissingNativeBackend = error is IllegalStateException ||
      error is UnsatisfiedLinkError ||
      error is NoClassDefFoundError
    if (!isMissingNativeBackend && error != null) {
      throw error
    }
    writeMinimalPdfFallback(outputStream, payload)
  }

  private fun writeNativePdfDocument(
    outputStream: OutputStream,
    payload: WeatherExportPayload,
  ) {
    val pdf = PdfDocument()
    var currentPage: PdfDocument.Page? = null
    try {
      var pageNumber = 1
      var page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
      currentPage = page
      var canvas = page.canvas
      var cursorY = 36f

      val bgHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(17, 26, 39)
      }
      val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(244, 247, 250)
      }
      val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(198, 210, 224)
        style = Paint.Style.STROKE
        strokeWidth = 1f
      }
      val amberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 152, 26)
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textSize = 18f
      }
      val headerTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textSize = 13f
      }
      val headerSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(203, 213, 225)
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        textSize = 9.5f
      }
      val sectionTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(15, 23, 42)
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textSize = 11.5f
      }
      val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(71, 85, 105)
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textSize = 9.5f
      }
      val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(15, 23, 42)
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        textSize = 10f
      }
      val monoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(15, 23, 42)
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        textSize = 9.5f
      }

      fun ensureSpace(neededHeight: Float) {
        if (cursorY + neededHeight > PAGE_HEIGHT - 38f) {
          drawPageFooter(canvas, pageNumber)
          pdf.finishPage(page)
          currentPage = null
          pageNumber++
          page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
          currentPage = page
          canvas = page.canvas
          cursorY = 36f
        }
      }
      val station = payload.station
      val weather = payload.weather
      val category = AirportMapProjection.evaluateFlightCategory(weather)

      // 1. Cockpit Header Banner
      val headerRect = RectF(MARGIN_X, cursorY, PAGE_WIDTH - MARGIN_X, cursorY + 72f)
      canvas.drawRoundRect(headerRect, 6f, 6f, bgHeaderPaint)
      canvas.drawText("${station.icao} · МЕТЕОСВОДКА", MARGIN_X + 14f, cursorY + 24f, amberPaint)
      canvas.drawText(
        "${station.name} (${station.city}) · превышение ${station.elevationMeters} м",
        MARGIN_X + 14f,
        cursorY + 42f,
        headerTitlePaint,
      )
      canvas.drawText(
        "Наблюдение: ${weather.observationTimeUtc} (местн. ${weather.localObsTime} ${station.timezoneLabel}) · Источник: ${weather.sourceLabel}",
        MARGIN_X + 14f,
        cursorY + 58f,
        headerSubPaint,
      )

      val catColor = when (category) {
        FlightCategory.VFR -> Color.rgb(22, 163, 74)
        FlightCategory.MVFR -> Color.rgb(217, 119, 6)
        FlightCategory.IFR -> Color.rgb(220, 38, 38)
        FlightCategory.UNKNOWN -> Color.rgb(100, 116, 139)
      }
      val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = catColor }
      val badgeRect = RectF(PAGE_WIDTH - MARGIN_X - 78f, cursorY + 12f, PAGE_WIDTH - MARGIN_X - 12f, cursorY + 36f)
      canvas.drawRoundRect(badgeRect, 4f, 4f, badgePaint)
      val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textSize = 11f
        textAlign = Paint.Align.CENTER
      }
      canvas.drawText(category.code, badgeRect.centerX(), badgeRect.centerY() + 4f, badgeTextPaint)

      cursorY += 86f

      // 2. Current Weather Metrics Box
      val windUnit = payload.windUnit
      val windVal = UnitConverter.formatWindSpeedValue(weather.wind.speedKt, windUnit)
      val gustText = weather.wind.gustKt?.let { " G${UnitConverter.formatWindSpeedValue(it, windUnit)}" }.orEmpty()
      val dirText = when {
        weather.wind.isCalm -> "Штиль"
        weather.wind.isVariable -> "VRB"
        weather.wind.directionDeg != null -> String.format(Locale.US, "%03d°", weather.wind.directionDeg)
        else -> "---"
      }
      val windSummary = if (weather.wind.isCalm) "Штиль (0 ${windUnit.label})" else "$dirText $windVal$gustText ${windUnit.label}"

      val visSummary = when {
        weather.isCavok -> "CAVOK (> 10 км)"
        weather.visibilityMeters != null -> "${weather.visibilityMeters} м"
        else -> "Н/Д"
      }
      val cloudsSummary = if (weather.clouds.isNotEmpty()) {
        weather.clouds.joinToString(", ") { "${it.cover} ${it.baseFeet} ft" }
      } else if (weather.isCavok) {
        "CAVOK"
      } else {
        "NSC"
      }
      val (qnhVal, qnhUnitLbl) = UnitConverter.formatPressurePrimary(
        hpa = weather.qnhHpa,
        inHg = weather.qnhInHg,
        unit = payload.pressureUnit,
        locale = Locale.US,
      )
      val (qfeVal, qfeUnitLbl) = UnitConverter.formatPressurePrimary(
        hpa = weather.qfeHpa,
        inHg = weather.qfeInHg,
        unit = payload.pressureUnit,
        locale = Locale.US,
      )
      val tempSummary = "${weather.temperatureC?.toString() ?: "--"}°C / ${weather.dewPointC?.toString() ?: "--"}°C"

      val metricRows = listOf(
        "Ветер" to windSummary,
        "Видимость (MOR)" to visSummary,
        "Облачность" to cloudsSummary,
        "Явления погоды" to weather.weatherPhenomena,
        "Температура / Точка росы" to tempSummary,
        "Давление QNH / QFE" to "$qnhVal $qnhUnitLbl  /  $qfeVal $qfeUnitLbl (QNH ${weather.qnhHpa ?: "---"} гПа)",
      )

      val metricsBoxH = 26f + metricRows.size * 18f + 8f
      ensureSpace(metricsBoxH)
      val metricsRect = RectF(MARGIN_X, cursorY, PAGE_WIDTH - MARGIN_X, cursorY + metricsBoxH)
      canvas.drawRoundRect(metricsRect, 5f, 5f, cardBgPaint)
      canvas.drawRoundRect(metricsRect, 5f, 5f, cardBorderPaint)
      canvas.drawText("ТЕКУЩИЕ МЕТЕОУСЛОВИЯ (METAR)", MARGIN_X + 10f, cursorY + 18f, sectionTitlePaint)
      var rowY = cursorY + 36f
      metricRows.forEach { (k, v) ->
        canvas.drawText(k, MARGIN_X + 12f, rowY, labelPaint)
        canvas.drawText(v, MARGIN_X + 185f, rowY, bodyPaint)
        rowY += 18f
      }
      cursorY += metricsBoxH + 10f

      // 3. Runway Wind Components
      val rwyLines = listOf(payload.primaryRunwayCalc, payload.reciprocalRunwayCalc).map { calc ->
        val head = UnitConverter.convertWindFromKnots(calc.headwindKt, windUnit).toInt()
        val headText = if (head >= 0) "встр. $head" else "попутн. ${abs(head)}"
        val cross = UnitConverter.convertWindFromKnots(abs(calc.crosswindKt), windUnit).toInt()
        val rvr = calc.rvrMeters?.let { "$it м" } ?: "---"
        "ВПП ${calc.designator} (${String.format(Locale.US, "%03d", calc.headingDeg)}°): $headText ${windUnit.label} · боковой $cross ${windUnit.label} · RVR $rvr"
      }
      val rwyBoxH = 26f + rwyLines.size * 18f + 8f
      ensureSpace(rwyBoxH)
      val rwyRect = RectF(MARGIN_X, cursorY, PAGE_WIDTH - MARGIN_X, cursorY + rwyBoxH)
      canvas.drawRoundRect(rwyRect, 5f, 5f, cardBgPaint)
      canvas.drawRoundRect(rwyRect, 5f, 5f, cardBorderPaint)
      canvas.drawText("РАСЧЁТ ВЕТРА ПО АКТИВНЫМ ВПП", MARGIN_X + 10f, cursorY + 18f, sectionTitlePaint)
      var rwyY = cursorY + 36f
      rwyLines.forEach { line ->
        canvas.drawText(line, MARGIN_X + 12f, rwyY, bodyPaint)
        rwyY += 18f
      }
      cursorY += rwyBoxH + 10f

      // 4. Trends + Vector QNH Sparkline
      val hasChartPoints = payload.qnhTrend24h.points.size >= 2
      val trendBoxH = if (hasChartPoints) 142f else 78f
      ensureSpace(trendBoxH)
      val trendRect = RectF(MARGIN_X, cursorY, PAGE_WIDTH - MARGIN_X, cursorY + trendBoxH)
      canvas.drawRoundRect(trendRect, 5f, 5f, cardBgPaint)
      canvas.drawRoundRect(trendRect, 5f, 5f, cardBorderPaint)
      canvas.drawText("ТРЕНДЫ ВЕТРА, QNH И ТЕМПЕРАТУРЫ (24Ч)", MARGIN_X + 10f, cursorY + 18f, sectionTitlePaint)

      val wTrend = payload.windTrend
      val wDelta = UnitConverter.convertWindFromKnots(wTrend.deltaKt.toDouble(), windUnit).toInt()
      val wSign = if (wDelta > 0) "+" else ""
      canvas.drawText(
        "Ветер: ${wTrend.direction.arrow} ${wTrend.direction.label} ($wSign$wDelta ${windUnit.label}, набл.: ${wTrend.entries.size})",
        MARGIN_X + 12f,
        cursorY + 36f,
        bodyPaint,
      )
      val qTrend = payload.qnhTrend24h
      val qDelta = qTrend.delta24hHpa?.let { d -> if (d > 0) "+$d гПа" else "$d гПа" } ?: "--"
      val qRange = if (qTrend.minHpa != null && qTrend.maxHpa != null) "${qTrend.minHpa}–${qTrend.maxHpa} гПа" else "--"
      val tRange = if (qTrend.minTempC != null && qTrend.maxTempC != null) "${qTrend.minTempC}…${qTrend.maxTempC}°C" else "--"
      canvas.drawText(
        "QNH 24ч: Δ $qDelta (диапазон $qRange)  ·  Температура 24ч: $tRange",
        MARGIN_X + 12f,
        cursorY + 54f,
        bodyPaint,
      )

      if (hasChartPoints) {
        val chartBox = RectF(MARGIN_X + 14f, cursorY + 66f, PAGE_WIDTH - MARGIN_X - 14f, cursorY + trendBoxH - 12f)
        drawQnhSparkline(canvas, chartBox, qTrend.points, payload.pressureUnit)
      }
      cursorY += trendBoxH + 10f

      // 5. Raw METAR & TAF Block
      val rawLines = buildList {
        addAll(wrapText("METAR: ${weather.rawMetar}", monoPaint, CONTENT_WIDTH - 24f))
        add("")
        addAll(wrapText("TAF: ${weather.rawTaf}", monoPaint, CONTENT_WIDTH - 24f))
      }
      val rawBoxH = 26f + rawLines.size * 13.5f + 10f
      ensureSpace(rawBoxH.coerceAtMost(PAGE_HEIGHT - 80f))
      val rawRect = RectF(MARGIN_X, cursorY, PAGE_WIDTH - MARGIN_X, cursorY + rawBoxH)
      canvas.drawRoundRect(rawRect, 5f, 5f, cardBgPaint)
      canvas.drawRoundRect(rawRect, 5f, 5f, cardBorderPaint)
      canvas.drawText("ИСХОДНЫЕ СВОДКИ METAR / TAF", MARGIN_X + 10f, cursorY + 18f, sectionTitlePaint)
      var rawY = cursorY + 35f
      rawLines.forEach { line ->
        canvas.drawText(line, MARGIN_X + 12f, rawY, monoPaint)
        rawY += 13.5f
      }
      cursorY += rawBoxH + 10f

      // 6. Recent 24h Observations Table (if available)
      val historyRows = payload.weatherHistory24h
        .sortedByDescending { it.observedAtMillis }
        .take(8)
      if (historyRows.isNotEmpty()) {
        val histBoxH = 26f + historyRows.size * 15f + 10f
        ensureSpace(histBoxH)
        val histRect = RectF(MARGIN_X, cursorY, PAGE_WIDTH - MARGIN_X, cursorY + histBoxH)
        canvas.drawRoundRect(histRect, 5f, 5f, cardBgPaint)
        canvas.drawRoundRect(histRect, 5f, 5f, cardBorderPaint)
        canvas.drawText("ЖУРНАЛ НАБЛЮДЕНИЙ 24Ч (ПОСЛЕДНИЕ ${historyRows.size})", MARGIN_X + 10f, cursorY + 18f, sectionTitlePaint)
        var hy = cursorY + 35f
        historyRows.forEach { e ->
          val w = UnitConverter.formatWindSpeedValue(e.windSpeedKt, windUnit)
          val g = e.gustKt?.let { "G${UnitConverter.formatWindSpeedValue(it, windUnit)}" }.orEmpty()
          val d = e.windDirectionDeg?.let { String.format(Locale.US, "%03d°", it) } ?: "VRB"
          val vis = if (e.isCavok) "CAVOK" else "${e.visibilityMeters ?: "--"}м"
          val (qV, qU) = UnitConverter.formatPressurePrimary(e.qnhHpa, e.qnhInHg, payload.pressureUnit, Locale.US)
          val rowText = "${e.observationTimeUtc} [${e.flightCategoryCode}] · $d $w$g ${windUnit.label} · Вид $vis · QNH $qV $qU · ${e.temperatureC ?: "--"}°C"
          canvas.drawText(rowText, MARGIN_X + 12f, hy, monoPaint)
          hy += 15f
        }
      }

      drawPageFooter(canvas, pageNumber)
      currentPage?.let { pdf.finishPage(it) }
      currentPage = null
      pdf.writeTo(outputStream)
      outputStream.flush()
    } finally {
      runCatching { pdf.close() }
    }
  }

  private fun writeMinimalPdfFallback(
    outputStream: OutputStream,
    payload: WeatherExportPayload,
  ) {
    val summaryLines = WeatherReportFormatter.buildTextSummary(payload)
      .lines()
      .take(45)
    val streamContent = buildString {
      append("BT\n/F1 10 Tf\n36 800 Td\n14 TL\n")
      summaryLines.forEachIndexed { idx, line ->
        val safe = line.map { ch ->
          when (ch) {
            '\\', '(', ')' -> "\\$ch"
            in ' '..'~' -> ch.toString()
            else -> "?"
          }
        }.joinToString("")
        if (idx == 0) append("($safe) Tj\n") else append("T* ($safe) Tj\n")
      }
      append("ET\n")
    }
    val streamBytes = streamContent.toByteArray(StandardCharsets.US_ASCII)
    val objects = listOf(
      "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n",
      "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n",
      "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $PAGE_WIDTH $PAGE_HEIGHT] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>\nendobj\n",
      "4 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Courier >>\nendobj\n",
      "5 0 obj\n<< /Length ${streamBytes.size} >>\nstream\n$streamContent\nendstream\nendobj\n",
    )

    val out = StringBuilder("%PDF-1.4\n")
    val offsets = mutableListOf(0)
    objects.forEach { obj ->
      offsets.add(out.toString().toByteArray(StandardCharsets.US_ASCII).size)
      out.append(obj)
    }
    val xrefOffset = out.toString().toByteArray(StandardCharsets.US_ASCII).size
    out.append("xref\n0 ${objects.size + 1}\n")
    out.append("0000000000 65535 f \n")
    offsets.drop(1).forEach { off ->
      out.append(String.format(Locale.US, "%010d 00000 n \n", off))
    }
    out.append("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\n")
    out.append("startxref\n$xrefOffset\n%%EOF\n")
    outputStream.write(out.toString().toByteArray(StandardCharsets.US_ASCII))
    outputStream.flush()
  }

  private fun drawQnhSparkline(
    canvas: Canvas,
    rect: RectF,
    points: List<com.example.model.QnhHistoryPoint>,
    pressureUnit: PressureUnit,
  ) {
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.rgb(203, 213, 225)
      style = Paint.Style.STROKE
      strokeWidth = 1f
    }
    val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.rgb(217, 119, 6)
      style = Paint.Style.STROKE
      strokeWidth = 2.2f
    }
    val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(217, 119, 6) }
    val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.rgb(100, 116, 139)
      textSize = 8.5f
      typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
    }

    canvas.drawRoundRect(rect, 3f, 3f, bgPaint)
    canvas.drawRoundRect(rect, 3f, 3f, borderPaint)

    val minVal = points.minOf { it.qnhHpa }.toFloat()
    val maxVal = points.maxOf { it.qnhHpa }.toFloat()
    val span = (maxVal - minVal).coerceAtLeast(2f)

    val leftPad = 48f
    val rightPad = 14f
    val topPad = 10f
    val bottomPad = 12f
    val plotW = (rect.width() - leftPad - rightPad).coerceAtLeast(10f)
    val plotH = (rect.height() - topPad - bottomPad).coerceAtLeast(10f)

    val maxLabel = UnitConverter.formatPressurePrimary(maxVal.toInt(), null, pressureUnit, Locale.US).first
    val minLabel = UnitConverter.formatPressurePrimary(minVal.toInt(), null, pressureUnit, Locale.US).first
    canvas.drawText(maxLabel, rect.left + 6f, rect.top + 13f, axisPaint)
    canvas.drawText(minLabel, rect.left + 6f, rect.bottom - 5f, axisPaint)

    val path = Path()
    val stepX = if (points.size > 1) plotW / (points.size - 1) else 0f
    points.forEachIndexed { idx, pt ->
      val x = rect.left + leftPad + idx * stepX
      val norm = ((pt.qnhHpa - minVal) / span).coerceIn(0f, 1f)
      val y = rect.top + topPad + plotH * (1f - norm)
      if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
      canvas.drawCircle(x, y, 2.5f, dotPaint)
    }
    canvas.drawPath(path, linePaint)
  }

  private fun drawPageFooter(canvas: Canvas, pageNumber: Int) {
    val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.rgb(100, 116, 139)
      textSize = 8.5f
    }
    canvas.drawText(
      "${AuthorBrand.APP_TITLE} v${AuthorBrand.APP_VERSION} · ${AuthorBrand.COPYRIGHT_LINE}",
      MARGIN_X,
      PAGE_HEIGHT - 16f,
      footerPaint,
    )
    footerPaint.textAlign = Paint.Align.RIGHT
    canvas.drawText("Стр. $pageNumber", PAGE_WIDTH - MARGIN_X, PAGE_HEIGHT - 16f, footerPaint)
  }

  private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
    if (text.isBlank()) return emptyList()
    val words = text.split(Regex("\\s+"))
    val lines = mutableListOf<String>()
    var current = StringBuilder()
    words.forEach { word ->
      val candidate = if (current.isEmpty()) word else "$current $word"
      if (paint.measureText(candidate) <= maxWidth) {
        current = StringBuilder(candidate)
      } else {
        if (current.isNotEmpty()) lines.add(current.toString())
        current = StringBuilder(word)
      }
    }
    if (current.isNotEmpty()) lines.add(current.toString())
    return lines
  }
}
