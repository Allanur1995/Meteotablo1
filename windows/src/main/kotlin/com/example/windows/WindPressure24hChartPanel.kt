package com.example.windows

import com.example.data.AirportCatalog
import com.example.domain.D3TrendChartPoint
import com.example.domain.D3TrendsChartDataBuilder
import com.example.domain.D3TrendsDashboardModel
import com.example.domain.UnitConverter
import com.example.model.PressureUnit
import com.example.model.UnitSystem
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GradientPaint
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridLayout
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.awt.geom.Path2D
import java.util.Locale
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.SwingConstants
import javax.swing.table.DefaultTableModel
import kotlin.math.roundToInt

class WindPressure24hChartPanel(
  private val onBackToBoard: () -> Unit,
  private val onSelectAirport: (String) -> Unit,
  private val onRefreshHistory: (String) -> Unit,
  private val onSelectUnitSystem: (UnitSystem) -> Unit,
  private val onOpenExport: () -> Unit,
) : JPanel(BorderLayout(8, 8)) {

  private val headerPanel = JPanel(BorderLayout(8, 4))
  private val titleLabel = JLabel("24ч История и Тренды ветра / QNH", SwingConstants.LEFT).apply {
    font = Font("Segoe UI", Font.BOLD, 15)
  }
  private val subtitleLabel = JLabel("", SwingConstants.LEFT).apply {
    font = Font("Consolas", Font.PLAIN, 11)
  }
  private val modeToggleBtn = JButton("Режим: Ветер + Видимость")
  private val backBtn = JButton("← На табло")
  private val refreshBtn = JButton("Обновить 24ч")
  private val unitToggleBtn = JButton("Metric / Imperial")
  private val exportBtn = JButton("Экспорт TXT / HTML")

  private val airportChipsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 6, 4))
  private val windKpiValue = JLabel("--", SwingConstants.LEFT).apply {
    font = Font("Consolas", Font.BOLD, 14)
  }
  private val visKpiValue = JLabel("--", SwingConstants.LEFT).apply {
    font = Font("Consolas", Font.BOLD, 14)
  }
  private val qnhKpiValue = JLabel("--", SwingConstants.LEFT).apply {
    font = Font("Consolas", Font.BOLD, 14)
  }
  private val countKpiValue = JLabel("--", SwingConstants.LEFT).apply {
    font = Font("Consolas", Font.BOLD, 14)
  }
  private val kpiRow = JPanel(GridLayout(1, 4, 8, 4))
  private val windKpiCard = JPanel(BorderLayout(2, 2))
  private val visKpiCard = JPanel(BorderLayout(2, 2))
  private val qnhKpiCard = JPanel(BorderLayout(2, 2))
  private val countKpiCard = JPanel(BorderLayout(2, 2))

  private val chartCanvas = DualAxisChartCanvas()
  private val historyModel = object : DefaultTableModel(
    arrayOf("UTC", "Ветер", "Порывы", "Видимость", "ВНГО", "QNH", "T / DP", "METAR"),
    0,
  ) {
    override fun isCellEditable(row: Int, column: Int): Boolean = false
  }
  private val historyTable = JTable(historyModel).apply {
    rowHeight = 24
    font = Font("Consolas", Font.PLAIN, 11)
    tableHeader.font = Font("Segoe UI", Font.BOLD, 11)
  }

  private var currentActiveIcao: String = "UTAM"
  private var currentUnitSystem: UnitSystem = UnitSystem.IMPERIAL
  private var lastState: DesktopUiState? = null
  private var showVisibilityMode: Boolean = true

  init {
    border = BorderFactory.createEmptyBorder(8, 8, 8, 8)

    val titleStack = JPanel(GridLayout(2, 1)).apply {
      isOpaque = false
      add(titleLabel)
      add(subtitleLabel)
    }
    val actionRow = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 2)).apply {
      isOpaque = false
      add(modeToggleBtn)
      add(unitToggleBtn)
      add(refreshBtn)
      add(exportBtn)
      add(backBtn)
    }
    headerPanel.add(titleStack, BorderLayout.WEST)
    headerPanel.add(actionRow, BorderLayout.EAST)

    windKpiCard.add(JLabel("Ветер за 24ч").apply { font = Font("Segoe UI", Font.BOLD, 11) }, BorderLayout.NORTH)
    windKpiCard.add(windKpiValue, BorderLayout.CENTER)
    visKpiCard.add(JLabel("Видимость").apply { font = Font("Segoe UI", Font.BOLD, 11) }, BorderLayout.NORTH)
    visKpiCard.add(visKpiValue, BorderLayout.CENTER)
    qnhKpiCard.add(JLabel("Давление QNH").apply { font = Font("Segoe UI", Font.BOLD, 11) }, BorderLayout.NORTH)
    qnhKpiCard.add(qnhKpiValue, BorderLayout.CENTER)
    countKpiCard.add(JLabel("Наблюдений 24ч").apply { font = Font("Segoe UI", Font.BOLD, 11) }, BorderLayout.NORTH)
    countKpiCard.add(countKpiValue, BorderLayout.CENTER)

    kpiRow.isOpaque = false
    kpiRow.add(windKpiCard)
    kpiRow.add(visKpiCard)
    kpiRow.add(qnhKpiCard)
    kpiRow.add(countKpiCard)

    val topBlock = JPanel(BorderLayout(6, 6)).apply {
      isOpaque = false
      add(headerPanel, BorderLayout.NORTH)
      add(airportChipsPanel, BorderLayout.CENTER)
      add(kpiRow, BorderLayout.SOUTH)
    }

    val tableScroll = JScrollPane(historyTable).apply {
      preferredSize = Dimension(900, 175)
    }

    add(topBlock, BorderLayout.NORTH)
    add(chartCanvas, BorderLayout.CENTER)
    add(tableScroll, BorderLayout.SOUTH)

    backBtn.addActionListener { onBackToBoard() }
    refreshBtn.addActionListener { onRefreshHistory(currentActiveIcao) }
    exportBtn.addActionListener { onOpenExport() }
    modeToggleBtn.addActionListener {
      showVisibilityMode = !showVisibilityMode
      lastState?.let { applyState(it) }
    }
    unitToggleBtn.addActionListener {
      val next = if (currentUnitSystem == UnitSystem.METRIC) UnitSystem.IMPERIAL else UnitSystem.METRIC
      onSelectUnitSystem(next)
    }
  }

  fun applyState(state: DesktopUiState) {
    lastState = state
    val pal = state.palette
    background = pal.bezelSurface
    headerPanel.background = pal.panelSurface
    headerPanel.border = BorderFactory.createCompoundBorder(
      BorderFactory.createLineBorder(pal.border, 1),
      BorderFactory.createEmptyBorder(6, 10, 6, 10),
    )

    val airportTabs = (state.favoriteIcaos + state.station.icao)
      .map { it.trim().uppercase() }
      .filter { it.length == 4 }
      .distinct()
    val activeIcao = state.selectedD3TrendIcao.trim().uppercase().let { sel ->
      if (sel in airportTabs) sel else airportTabs.firstOrNull() ?: state.station.icao
    }
    currentActiveIcao = activeIcao
    currentUnitSystem = UnitConverter.resolveUnitSystem(state.windUnit, state.pressureUnit)
      ?: UnitSystem.IMPERIAL

    val targetStation = if (activeIcao.equals(state.station.icao, ignoreCase = true)) {
      state.station
    } else {
      AirportCatalog.findOrDefault(activeIcao)
    }
    val entries = state.weatherHistoryByIcaoMap[activeIcao].orEmpty()
    val fallback = if (activeIcao.equals(state.station.icao, ignoreCase = true)) {
      state.weather
    } else {
      state.mapWeatherByIcao[activeIcao]
    }
    val model = D3TrendsChartDataBuilder.build(
      station = targetStation,
      historyEntries = entries,
      liveFallbackWeather = fallback,
      windUnit = state.windUnit,
      pressureUnit = state.pressureUnit,
    )

    titleLabel.text = "24ч История METAR и Тренды · ${model.icao} (${model.city})"
    titleLabel.foreground = pal.textPrimary
    subtitleLabel.text = if (showVisibilityMode) {
      "Ось слева: Ветер/Порывы (${state.windUnit.label}) · Ось справа: Видимость (км) · ${model.points.size} точек"
    } else {
      "Ось слева: Ветер/Порывы (${state.windUnit.label}) · Ось справа: QNH (${state.pressureUnit.label}) · ${model.points.size} точек"
    }
    subtitleLabel.foreground = pal.textSecondary

    val isLoadingTarget = state.loadingHistoryIcao?.equals(activeIcao, ignoreCase = true) == true || state.isLoadingHistory
    refreshBtn.isEnabled = !isLoadingTarget
    refreshBtn.text = if (isLoadingTarget) "Загрузка..." else "Обновить 24ч"
    modeToggleBtn.text = if (showVisibilityMode) "Тренд: Ветер + Видимость" else "Тренд: Ветер + QNH"
    unitToggleBtn.text = if (currentUnitSystem == UnitSystem.METRIC) "Metric (hPa, м/с)" else "Imperial (inHg, kt)"

    listOf(modeToggleBtn, backBtn, refreshBtn, unitToggleBtn, exportBtn).forEach { btn ->
      DesktopThemeHelper.styleButton(
        btn = btn,
        pal = pal,
        isActive = btn == modeToggleBtn && showVisibilityMode,
        accentBorder = if (btn == modeToggleBtn && showVisibilityMode) pal.headwindPositive else null,
        padding = java.awt.Insets(4, 10, 4, 10),
      )
    }

    airportChipsPanel.background = pal.panelSurface
    airportChipsPanel.border = BorderFactory.createCompoundBorder(
      BorderFactory.createLineBorder(pal.border, 1),
      BorderFactory.createEmptyBorder(3, 6, 3, 6),
    )
    airportChipsPanel.removeAll()
    airportTabs.forEach { icao ->
      val selected = icao == activeIcao
      val isFav = icao in state.favoriteIcaos
      val chip = JButton(if (isFav) "★ $icao" else icao).apply {
        font = Font("Consolas", Font.BOLD, 12)
        if (selected) {
          putClientProperty("preserveForeground", true)
          foreground = pal.metarAmber
        } else {
          putClientProperty("preserveForeground", false)
        }
        DesktopThemeHelper.styleButton(
          btn = this,
          pal = pal,
          isActive = selected,
          accentBorder = if (selected) pal.metarAmber else null,
          padding = java.awt.Insets(4, 10, 4, 10),
        )
        addActionListener { onSelectAirport(icao) }
      }
      airportChipsPanel.add(chip)
    }
    airportChipsPanel.revalidate()
    airportChipsPanel.repaint()

    listOf(windKpiCard, visKpiCard, qnhKpiCard, countKpiCard).forEach { card ->
      card.background = pal.fieldBackground
      card.border = BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(pal.border, 1),
        BorderFactory.createEmptyBorder(6, 10, 6, 10),
      )
      (card.getComponent(0) as? JLabel)?.foreground = pal.textSecondary
    }
    windKpiValue.text = model.formattedWindRange()
    windKpiValue.foreground = pal.metarAmber
    visKpiValue.text = model.formattedVisibilityRange()
    visKpiValue.foreground = pal.headwindPositive
    qnhKpiValue.text = "${model.formattedPressureRange()} · Δ ${model.formattedPressureDelta()}"
    qnhKpiValue.foreground = pal.accentCyan
    countKpiValue.text = "${entries.size} сводок (24ч)"
    countKpiValue.foreground = pal.textPrimary

    chartCanvas.updateModel(model, pal, showVisibilityMode)

    historyTable.background = pal.fieldBackground
    historyTable.foreground = pal.textPrimary
    historyTable.gridColor = pal.border
    historyTable.selectionBackground = pal.buttonActiveBackground
    historyTable.selectionForeground = pal.textPrimary
    historyTable.tableHeader?.let { hdr ->
      hdr.background = pal.secondaryFieldBackground
      hdr.foreground = pal.textPrimary
    }
    (historyTable.parent as? javax.swing.JViewport)?.background = pal.panelSurface
    historyModel.rowCount = 0
    entries.sortedByDescending { it.observedAtMillis }.forEach { entry ->
      val spd = UnitConverter.formatWindSpeedValue(entry.windSpeedKt, state.windUnit)
      val gust = entry.gustKt?.let { "${UnitConverter.formatWindSpeedValue(it, state.windUnit)} ${state.windUnit.label}" } ?: "—"
      val vis = entry.visibilityMeters?.let { if (it >= 9999) "≥10 км" else "${it} м" } ?: "—"
      val ceil = entry.ceilingFeet?.let { "$it ft" } ?: "—"
      val qnh = when (state.pressureUnit) {
        PressureUnit.HPA -> entry.qnhHpa?.let { "$it гПа" } ?: "—"
        PressureUnit.INHG -> entry.qnhInHg?.let { String.format(Locale.US, "%.2f дм", it) } ?: "—"
      }
      val tdp = "${entry.temperatureC ?: "--"}/${entry.dewPointC ?: "--"} °C"
      historyModel.addRow(
        arrayOf(
          entry.observationTimeUtc,
          "$spd ${state.windUnit.label}",
          gust,
          vis,
          ceil,
          qnh,
          tdp,
          entry.rawMetar,
        ),
      )
    }
  }
}

private class DualAxisChartCanvas : JPanel() {
  private var model: D3TrendsDashboardModel? = null
  private var palette: DesktopPalette = DayDesktopPalette
  private var hoveredIndex: Int? = null
  private var visibilityMode: Boolean = true

  init {
    preferredSize = Dimension(880, 290)
    isOpaque = true
    addMouseMotionListener(object : MouseMotionAdapter() {
      override fun mouseMoved(e: MouseEvent) {
        updateHover(e.x)
      }
    })
    addMouseListener(object : MouseAdapter() {
      override fun mouseExited(e: MouseEvent) {
        hoveredIndex = null
        repaint()
      }
    })
  }

  fun updateModel(newModel: D3TrendsDashboardModel, newPalette: DesktopPalette, showVisibility: Boolean) {
    model = newModel
    palette = newPalette
    visibilityMode = showVisibility
    background = newPalette.fieldBackground
    border = BorderFactory.createLineBorder(newPalette.border, 1)
    repaint()
  }

  private fun updateHover(mouseX: Int) {
    val pts = model?.points.orEmpty()
    if (pts.isEmpty()) return
    val left = 56f
    val right = (width - 64f).coerceAtLeast(left + 20f)
    val span = (right - left).coerceAtLeast(1f)
    val rel = ((mouseX - left) / span).coerceIn(0f, 1f)
    val idx = if (pts.size == 1) 0 else (rel * (pts.size - 1)).roundToInt().coerceIn(0, pts.lastIndex)
    if (hoveredIndex != idx) {
      hoveredIndex = idx
      repaint()
    }
  }

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)
    val g2 = g.create() as Graphics2D
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

      val m = model
      val pts = m?.points.orEmpty()
      if (m == null || pts.isEmpty()) {
        g2.color = palette.textMuted
        g2.font = Font("Segoe UI", Font.PLAIN, 13)
        val msg = "Нажмите «Обновить 24ч» для загрузки архивных сводок ветра, видимости и давления"
        val fm = g2.fontMetrics
        g2.drawString(msg, ((width - fm.stringWidth(msg)) / 2).coerceAtLeast(16), height / 2)
        return
      }

      val left = 58f
      val right = (width - 64f).coerceAtLeast(left + 40f)
      val top = 30f
      val bottom = (height - 32f).coerceAtLeast(top + 40f)
      val plotW = right - left
      val plotH = bottom - top

      val maxWind = pts.maxOf { maxOf(it.windDisplay, it.gustDisplay ?: it.windDisplay) }.let {
        (it * 1.25).coerceAtLeast(10.0)
      }
      val qnhVals = pts.mapNotNull { it.pressureDisplay }
      val isInHg = m.pressureUnit == PressureUnit.INHG
      var qMin = qnhVals.minOrNull() ?: if (isInHg) 29.70 else 1005.0
      var qMax = qnhVals.maxOrNull() ?: if (isInHg) 30.20 else 1022.0
      val qPad = if (isInHg) maxOf(0.06, (qMax - qMin) * 0.25) else maxOf(3.0, (qMax - qMin) * 0.25)
      qMin -= qPad * 0.6
      qMax += qPad * 0.6
      val qSpan = (qMax - qMin).coerceAtLeast(0.05)

      val visColor = Color(0x10, 0xB9, 0x81)

      // Horizontal Grid + Axis Labels
      g2.font = Font("Consolas", Font.PLAIN, 10)
      for (i in 0..4) {
        val ratio = i / 4f
        val y = bottom - ratio * plotH
        g2.color = Color(palette.border.red, palette.border.green, palette.border.blue, 90)
        g2.stroke = BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, floatArrayOf(4f, 4f), 0f)
        g2.drawLine(left.toInt(), y.toInt(), right.toInt(), y.toInt())

        val windTick = (maxWind * ratio).roundToInt()
        g2.color = palette.metarAmber
        g2.drawString("$windTick", 12, (y + 4).toInt())

        if (visibilityMode) {
          val visTick = (10.0 * ratio)
          g2.color = visColor
          g2.drawString("${String.format(Locale.US, "%.1f", visTick)}км", (right + 6).toInt(), (y + 4).toInt())
        } else {
          val qTick = qMin + qSpan * ratio
          val qStr = if (isInHg) String.format(Locale.US, "%.2f", qTick) else "${qTick.roundToInt()}"
          g2.color = palette.accentCyan
          g2.drawString(qStr, (right + 6).toInt(), (y + 4).toInt())
        }
      }

      if (visibilityMode) {
        // VFR 5 km dashed reference
        val vfrY = (bottom - 0.5f * plotH)
        g2.color = Color(visColor.red, visColor.green, visColor.blue, 140)
        g2.stroke = BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, floatArrayOf(6f, 4f), 0f)
        g2.drawLine(left.toInt(), vfrY.toInt(), right.toInt(), vfrY.toInt())
      } else {
        val isa = if (isInHg) 29.92 else 1013.25
        if (isa in qMin..qMax) {
          val isaY = (bottom - ((isa - qMin) / qSpan) * plotH).toFloat()
          g2.color = Color(palette.headwindPositive.red, palette.headwindPositive.green, palette.headwindPositive.blue, 140)
          g2.stroke = BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, floatArrayOf(6f, 4f), 0f)
          g2.drawLine(left.toInt(), isaY.toInt(), right.toInt(), isaY.toInt())
        }
      }

      fun xAt(i: Int): Float = if (pts.size == 1) (left + right) / 2f else left + (i.toFloat() / pts.lastIndex) * plotW
      fun yWind(v: Double): Float = (bottom - (v / maxWind).coerceIn(0.0, 1.0) * plotH).toFloat()
      fun yQnh(v: Double): Float = (bottom - ((v - qMin) / qSpan).coerceIn(0.0, 1.0) * plotH).toFloat()
      fun yVis(km: Double): Float = (bottom - (km / 10.0).coerceIn(0.0, 1.0) * plotH).toFloat()

      // Wind Area + Line
      val areaPath = Path2D.Float()
      val windPath = Path2D.Float()
      pts.forEachIndexed { i, pt ->
        val x = xAt(i)
        val y = yWind(pt.windDisplay)
        if (i == 0) {
          areaPath.moveTo(x, bottom)
          areaPath.lineTo(x, y)
          windPath.moveTo(x, y)
        } else {
          areaPath.lineTo(x, y)
          windPath.lineTo(x, y)
        }
      }
      areaPath.lineTo(xAt(pts.lastIndex), bottom)
      areaPath.closePath()
      g2.paint = GradientPaint(
        0f,
        top,
        Color(palette.metarAmber.red, palette.metarAmber.green, palette.metarAmber.blue, 80),
        0f,
        bottom,
        Color(palette.metarAmber.red, palette.metarAmber.green, palette.metarAmber.blue, 6),
      )
      if (pts.size >= 2) g2.fill(areaPath)

      g2.color = palette.metarAmber
      g2.stroke = BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
      if (pts.size >= 2) g2.draw(windPath)

      // Gust Dashed Line
      val gustIndices = pts.indices.filter { pts[it].gustDisplay != null }
      if (gustIndices.size >= 2) {
        val gustPath = Path2D.Float()
        gustIndices.forEachIndexed { k, idx ->
          val x = xAt(idx)
          val y = yWind(pts[idx].gustDisplay!!)
          if (k == 0) gustPath.moveTo(x, y) else gustPath.lineTo(x, y)
        }
        g2.color = palette.tailwindNegative
        g2.stroke = BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, floatArrayOf(5f, 4f), 0f)
        g2.draw(gustPath)
      }

      if (visibilityMode) {
        val visIndices = pts.indices.filter { pts[it].visibilityKm != null }
        if (visIndices.size >= 2) {
          val visPath = Path2D.Float()
          visIndices.forEachIndexed { k, idx ->
            val x = xAt(idx)
            val y = yVis(pts[idx].visibilityKm!!)
            if (k == 0) visPath.moveTo(x, y) else visPath.lineTo(x, y)
          }
          g2.color = visColor
          g2.stroke = BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
          g2.draw(visPath)
        }
      } else {
        val qnhIndices = pts.indices.filter { pts[it].pressureDisplay != null }
        if (qnhIndices.size >= 2) {
          val qPath = Path2D.Float()
          qnhIndices.forEachIndexed { k, idx ->
            val x = xAt(idx)
            val y = yQnh(pts[idx].pressureDisplay!!)
            if (k == 0) qPath.moveTo(x, y) else qPath.lineTo(x, y)
          }
          g2.color = palette.accentCyan
          g2.stroke = BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
          g2.draw(qPath)
        }
      }

      // Dots & Time Labels
      val stepLabel = (pts.size / 6).coerceAtLeast(1)
      pts.forEachIndexed { i, pt ->
        val x = xAt(i)
        val yW = yWind(pt.windDisplay)
        g2.color = palette.metarAmber
        g2.fillOval((x - 3).toInt(), (yW - 3).toInt(), 6, 6)
        if (visibilityMode) {
          pt.visibilityKm?.let { km ->
            val yV = yVis(km)
            g2.color = visColor
            g2.fillOval((x - 3).toInt(), (yV - 3).toInt(), 6, 6)
          }
        } else {
          pt.pressureDisplay?.let { q ->
            val yQ = yQnh(q)
            g2.color = palette.accentCyan
            g2.fillOval((x - 3).toInt(), (yQ - 3).toInt(), 6, 6)
          }
        }
        if (i % stepLabel == 0 || i == pts.lastIndex) {
          g2.color = palette.textMuted
          g2.font = Font("Consolas", Font.PLAIN, 10)
          g2.drawString(pt.observationTimeUtc, (x - 16).toInt().coerceIn(8, width - 56), (bottom + 18).toInt())
        }
      }

      // Legend Header
      g2.font = Font("Segoe UI", Font.BOLD, 11)
      g2.color = palette.metarAmber
      g2.drawString("— Ветер (${m.windUnit.label})", left.toInt(), 16)
      g2.color = palette.tailwindNegative
      g2.drawString("-- Порывы", (left + 120).toInt(), 16)
      if (visibilityMode) {
        g2.color = visColor
        g2.drawString("— Видимость (км)", (left + 210).toInt(), 16)
      } else {
        g2.color = palette.accentCyan
        g2.drawString("— QNH (${m.pressureUnit.label})", (left + 210).toInt(), 16)
      }

      // Hover Crosshair & Tooltip
      val hIdx = hoveredIndex?.takeIf { it in pts.indices }
      if (hIdx != null) {
        val pt = pts[hIdx]
        val hx = xAt(hIdx)
        g2.color = palette.borderStrong
        g2.stroke = BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, floatArrayOf(3f, 3f), 0f)
        g2.drawLine(hx.toInt(), top.toInt(), hx.toInt(), bottom.toInt())

        val gStr = pt.gustDisplay?.let { " (G${it.roundToInt()})" }.orEmpty()
        val rightTip = if (visibilityMode) {
          "Вид. ${D3TrendsDashboardModel.formatVisibility(pt.visibilityMeters)}"
        } else {
          val qStr = pt.pressureDisplay?.let {
            if (isInHg) String.format(Locale.US, "%.2f", it) else "${it.roundToInt()}"
          } ?: "--"
          "QNH $qStr ${m.pressureUnit.label}"
        }
        val tip = "${pt.observationTimeUtc} · Ветер ${pt.windDisplay.roundToInt()} ${m.windUnit.label}$gStr · $rightTip"
        g2.font = Font("Consolas", Font.BOLD, 11)
        val fm = g2.fontMetrics
        val boxW = fm.stringWidth(tip) + 16
        val boxX = (hx - boxW / 2f).toInt().coerceIn(left.toInt(), (right - boxW).toInt().coerceAtLeast(left.toInt()))
        g2.color = palette.panelSurface
        g2.fillRoundRect(boxX, 4, boxW, 20, 6, 6)
        g2.color = palette.metarAmber
        g2.drawRoundRect(boxX, 4, boxW, 20, 6, 6)
        g2.color = palette.textPrimary
        g2.drawString(tip, boxX + 8, 18)
      }
    } finally {
      g2.dispose()
    }
  }
}
