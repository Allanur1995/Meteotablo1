package com.example.windows

import com.example.domain.UnitConverter
import com.example.model.WindData
import com.example.model.WindSpeedUnit
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Arc2D
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import java.util.Locale
import javax.swing.JPanel
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

class WindRoseDetailPanel(
  private val onToggleUnit: () -> Unit,
) : JPanel() {

  private var wind: WindData? = null
  private var windUnit: WindSpeedUnit = WindSpeedUnit.KT
  private var palette: DesktopPalette = DayDesktopPalette

  init {
    preferredSize = Dimension(280, 280)
    minimumSize = Dimension(240, 240)
    cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    toolTipText = "Нажмите для переключения единиц (kt / м/с / км/ч)"
    addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent?) {
        onToggleUnit()
      }
    })
  }

  fun updateData(wind: WindData, windUnit: WindSpeedUnit, palette: DesktopPalette) {
    this.wind = wind
    this.windUnit = windUnit
    this.palette = palette
    background = palette.fieldBackground
    repaint()
  }

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)
    val w = wind ?: return
    val g2 = g.create() as Graphics2D
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

    val width = width.toDouble()
    val height = height.toDouble()
    g2.color = palette.fieldBackground
    g2.fillRect(0, 0, width.toInt(), height.toInt())

    val cx = width / 2.0
    val cy = height / 2.0
    val outerRadius = minOf(width, height) * 0.40
    val hubRadius = outerRadius * 0.24
    val usableSpan = outerRadius - hubRadius

    val maxScale = when (windUnit) {
      WindSpeedUnit.KT -> 40.0
      WindSpeedUnit.MPS -> 20.0
      WindSpeedUnit.KPH -> 80.0
    }
    val ringSteps = when (windUnit) {
      WindSpeedUnit.KT -> listOf(10, 20, 30, 40)
      WindSpeedUnit.MPS -> listOf(5, 10, 15, 20)
      WindSpeedUnit.KPH -> listOf(20, 40, 60, 80)
    }

    // Concentric speed rings
    ringSteps.forEachIndexed { idx, stepVal ->
      val ratio = (idx + 1).toDouble() / ringSteps.size
      val r = hubRadius + usableSpan * ratio
      g2.color = palette.border
      g2.stroke = BasicStroke(if (idx == ringSteps.lastIndex) 1.5f else 0.9f)
      g2.drawOval((cx - r).toInt(), (cy - r).toInt(), (r * 2).toInt(), (r * 2).toInt())

      g2.color = palette.textMuted
      g2.font = Font("Segoe UI", Font.PLAIN, 10)
      g2.drawString(stepVal.toString(), (cx + 4).toFloat(), (cy - r + 11).toFloat())
    }

    // Compass ticks
    for (deg in 0 until 360 step 10) {
      val rad = (deg - 90.0) * PI / 180.0
      val inner = if (deg % 30 == 0) outerRadius * 0.93 else outerRadius * 0.96
      g2.color = palette.compassStroke
      g2.stroke = BasicStroke(if (deg % 30 == 0) 1.4f else 0.8f)
      g2.draw(
        Line2D.Double(
          cx + cos(rad) * inner,
          cy + sin(rad) * inner,
          cx + cos(rad) * (outerRadius * 1.03),
          cy + sin(rad) * (outerRadius * 1.03),
        ),
      )
    }

    // Cardinals
    g2.font = Font("Segoe UI", Font.BOLD, 11)
    g2.color = palette.textPrimary
    drawCentered(g2, "N 360", cx, cy - outerRadius - 12)
    drawCentered(g2, "090", cx + outerRadius + 16, cy)
    drawCentered(g2, "180", cx, cy + outerRadius + 13)
    drawCentered(g2, "270", cx - outerRadius - 16, cy)

    val speedVal = UnitConverter.convertWindFromKnots(w.speedKt, windUnit)
    val gustVal = w.gustKt?.let { UnitConverter.convertWindFromKnots(it, windUnit) } ?: 0.0
    val speedRatio = (speedVal / maxScale).coerceIn(0.0, 1.0)
    val gustRatio = (gustVal / maxScale).coerceIn(0.0, 1.0)

    if (!w.isCalm && w.directionDeg != null) {
      val dir = w.directionDeg!!
      if (w.variableFromDeg != null && w.variableToDeg != null) {
        val sweep = ((w.variableToDeg!! - w.variableFromDeg!! + 360) % 360).let { if (it == 0) 360 else it }
        g2.color = Color(palette.windArrow.red, palette.windArrow.green, palette.windArrow.blue, 60)
        val r = hubRadius + usableSpan * speedRatio
        g2.fill(Arc2D.Double(cx - r, cy - r, r * 2, r * 2, 90.0 - w.variableFromDeg!!, -sweep.toDouble(), Arc2D.PIE))
      }

      val rad = (dir - 90.0) * PI / 180.0
      if (gustRatio > speedRatio) {
        val gR = hubRadius + usableSpan * gustRatio
        g2.color = Color(palette.metarAmber.red, palette.metarAmber.green, palette.metarAmber.blue, 140)
        g2.stroke = BasicStroke(
          2.2f,
          BasicStroke.CAP_ROUND,
          BasicStroke.JOIN_ROUND,
          10f,
          floatArrayOf(5f, 4f),
          0f,
        )
        g2.draw(Line2D.Double(cx, cy, cx + cos(rad) * gR, cy + sin(rad) * gR))
      }

      val tipR = (hubRadius + usableSpan * speedRatio).coerceAtLeast(hubRadius + 16.0)
      val tipX = cx + cos(rad) * tipR
      val tipY = cy + sin(rad) * tipR
      g2.color = palette.windArrow
      g2.stroke = BasicStroke(3.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
      g2.draw(Line2D.Double(cx, cy, tipX, tipY))

      val arrow = Path2D.Double().apply {
        moveTo(tipX, tipY)
        lineTo(tipX - cos(rad - 0.42) * 10.0, tipY - sin(rad - 0.42) * 10.0)
        lineTo(tipX - cos(rad + 0.42) * 10.0, tipY - sin(rad + 0.42) * 10.0)
        closePath()
      }
      g2.fill(arrow)
    }

    // Center Hub
    g2.color = palette.panelSurface
    g2.fillOval((cx - hubRadius).toInt(), (cy - hubRadius).toInt(), (hubRadius * 2).toInt(), (hubRadius * 2).toInt())
    g2.color = palette.borderStrong
    g2.stroke = BasicStroke(1.5f)
    g2.drawOval((cx - hubRadius).toInt(), (cy - hubRadius).toInt(), (hubRadius * 2).toInt(), (hubRadius * 2).toInt())

    val dirLabel = when {
      w.isCalm -> "ШТИЛЬ"
      w.isVariable -> "VRB"
      w.directionDeg != null -> String.format(Locale.US, "%03d°", w.directionDeg)
      else -> "---"
    }
    g2.color = palette.textPrimary
    g2.font = Font("Segoe UI", Font.BOLD, 12)
    drawCentered(g2, dirLabel, cx, cy - 8)
    g2.color = palette.metarAmber
    g2.font = Font("Segoe UI", Font.BOLD, 12)
    drawCentered(g2, "${speedVal.roundToInt()} ${windUnit.label}", cx, cy + 8)

    g2.dispose()
  }

  private fun drawCentered(g2: Graphics2D, text: String, cx: Double, cy: Double) {
    val fm = g2.fontMetrics
    g2.drawString(text, (cx - fm.stringWidth(text) / 2.0).toFloat(), (cy + (fm.ascent - fm.descent) / 2.0).toFloat())
  }
}
