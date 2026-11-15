package com.example.windows

import com.example.domain.UnitConverter
import com.example.model.RunwayCalculation
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
import java.awt.geom.RoundRectangle2D
import java.util.Locale
import javax.swing.JPanel
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class RunwayWindRosePanel(
  private val onToggleUnit: () -> Unit,
) : JPanel() {

  private var calculation: RunwayCalculation? = null
  private var wind: WindData? = null
  private var windUnit: WindSpeedUnit = WindSpeedUnit.KT
  private var palette: DesktopPalette = DayDesktopPalette

  init {
    preferredSize = Dimension(280, 235)
    minimumSize = Dimension(220, 185)
    cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent?) {
        onToggleUnit()
      }
    })
  }

  fun updateData(
    calculation: RunwayCalculation,
    wind: WindData,
    windUnit: WindSpeedUnit,
    palette: DesktopPalette,
  ) {
    this.calculation = calculation
    this.wind = wind
    this.windUnit = windUnit
    this.palette = palette
    background = palette.panelSurface
    repaint()
  }

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)
    val calc = calculation ?: return
    val w = wind ?: return
    val g2 = g.create() as Graphics2D
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

    val width = width.toDouble()
    val height = height.toDouble()
    g2.color = palette.panelSurface
    g2.fillRect(0, 0, width.toInt(), height.toInt())

    val crosswindVal = UnitConverter.convertWindFromKnots(calc.crosswindKt, windUnit)
    val headwindVal = UnitConverter.convertWindFromKnots(calc.headwindKt, windUnit)
    val speedMain = UnitConverter.formatWindSpeedValue(w.speedKt, windUnit)
    val gustMain = w.gustKt?.let { UnitConverter.formatWindSpeedValue(it, windUnit) }

    val windDirHeader = when {
      w.isCalm -> "Штиль"
      w.isVariable -> "VRB"
      w.directionDeg != null -> String.format(Locale.US, "%03d°", w.directionDeg)
      else -> "---°"
    }
    val windSpeedFooter = when {
      w.isCalm -> "0 ${windUnit.label}"
      gustMain != null -> "$speedMain G$gustMain ${windUnit.label}"
      else -> "$speedMain ${windUnit.label}"
    }
    val isTailwind = headwindVal < -0.049
    val crosswindText = String.format(Locale.US, "%.1f", crosswindVal)
    val headwindText = if (isTailwind) {
      String.format(Locale.US, "-%.1f", abs(headwindVal))
    } else {
      String.format(Locale.US, "%.1f", abs(headwindVal))
    }
    val frictionText = calc.frictionCoeff?.let { String.format(Locale.US, "%.2f", it) } ?: "--"

    // Corner readouts
    drawCornerBadge(g2, 8, 6, crosswindText, "Бок.", palette.textPrimary)
    drawCornerBadge(
      g2,
      (width - 66).toInt(),
      6,
      headwindText,
      if (isTailwind) "Попут." else "Вст.",
      if (isTailwind) palette.tailwindNegative else palette.textPrimary,
    )
    drawCornerBadge(g2, (width - 66).toInt(), (height - 46).toInt(), frictionText, "К.Сц.", palette.textPrimary)

    // Compass ring
    val cx = width / 2.0
    val cy = height / 2.0 + 4.0
    val radius = minOf(width, height) * 0.35
    val tickOuter = radius * 1.07

    g2.color = palette.compassStroke
    g2.stroke = BasicStroke(1.6f)
    g2.drawOval((cx - radius).toInt(), (cy - radius).toInt(), (radius * 2).toInt(), (radius * 2).toInt())

    for (deg in 0 until 360 step 10) {
      val rad = (deg - 90.0) * PI / 180.0
      val inner = if (deg % 30 == 0) radius * 0.90 else radius * 0.95
      val outer = if (deg % 30 == 0) tickOuter else radius
      g2.stroke = BasicStroke(if (deg % 30 == 0) 1.5f else 0.9f)
      g2.draw(
        Line2D.Double(
          cx + cos(rad) * inner,
          cy + sin(rad) * inner,
          cx + cos(rad) * outer,
          cy + sin(rad) * outer,
        ),
      )
    }

    // Cardinal marks
    g2.font = Font("Segoe UI", Font.BOLD, 10)
    g2.color = palette.textMuted
    drawCenteredText(g2, "N", cx, cy - radius - 9)
    drawCenteredText(g2, "E", cx + radius + 11, cy)
    drawCenteredText(g2, "S", cx, cy + radius + 11)
    drawCenteredText(g2, "W", cx - radius - 12, cy)

    // Gust / variable sector arc
    if (w.variableFromDeg != null && w.variableToDeg != null && !w.isCalm) {
      val from = w.variableFromDeg!!
      val to = w.variableToDeg!!
      val sweep = ((to - from + 360) % 360).let { if (it == 0) 360 else it }
      g2.color = Color(palette.windArrow.red, palette.windArrow.green, palette.windArrow.blue, 55)
      val arcRadius = radius * 0.92
      val startAngleSwing = 90.0 - from
      g2.fill(
        Arc2D.Double(
          cx - arcRadius,
          cy - arcRadius,
          arcRadius * 2,
          arcRadius * 2,
          startAngleSwing,
          -sweep.toDouble(),
          Arc2D.PIE,
        ),
      )
    }

    // Runway strip rotated to heading
    val oldTx = g2.transform
    g2.rotate(calc.headingDeg * PI / 180.0, cx, cy)
    val rwW = radius * 0.34
    val rwH = radius * 1.54
    val rwRect = RoundRectangle2D.Double(cx - rwW / 2.0, cy - rwH / 2.0, rwW, rwH, 4.0, 4.0)
    g2.color = palette.runwayFill
    g2.fill(rwRect)
    g2.color = palette.runwayStroke
    g2.stroke = BasicStroke(1.8f)
    g2.draw(rwRect)

    // Centerline dashed
    g2.stroke = BasicStroke(
      1.3f,
      BasicStroke.CAP_BUTT,
      BasicStroke.JOIN_MITER,
      10f,
      floatArrayOf(5f, 5f),
      0f,
    )
    g2.draw(Line2D.Double(cx, cy - rwH * 0.42, cx, cy + rwH * 0.42))
    g2.transform = oldTx

    // Wind arrow (direction wind is blowing FROM -> toward center)
    if (!w.isCalm && w.directionDeg != null) {
      val fromRad = (w.directionDeg!! - 90.0) * PI / 180.0
      val startX = cx + cos(fromRad) * (radius * 0.96)
      val startY = cy + sin(fromRad) * (radius * 0.96)
      val endX = cx + cos(fromRad) * (radius * 0.46)
      val endY = cy + sin(fromRad) * (radius * 0.46)

      g2.color = palette.windArrow
      g2.stroke = BasicStroke(3.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
      g2.draw(Line2D.Double(startX, startY, endX, endY))

      // Arrowhead pointing inward toward center
      val inwardAngle = fromRad + PI
      val headLen = 9.0
      val wing = 0.48
      val arrow = Path2D.Double().apply {
        moveTo(endX, endY)
        lineTo(endX - cos(inwardAngle - wing) * headLen, endY - sin(inwardAngle - wing) * headLen)
        lineTo(endX - cos(inwardAngle + wing) * headLen, endY - sin(inwardAngle + wing) * headLen)
        closePath()
      }
      g2.fill(arrow)
    }

    // Center Wind Box
    val boxW = 86.0
    val boxH = 44.0
    val box = RoundRectangle2D.Double(cx - boxW / 2.0, cy - boxH / 2.0, boxW, boxH, 6.0, 6.0)
    g2.color = palette.fieldBackground
    g2.fill(box)
    g2.color = palette.borderStrong
    g2.stroke = BasicStroke(1.3f)
    g2.draw(box)

    g2.color = palette.textPrimary
    g2.font = Font("Segoe UI", Font.BOLD, 13)
    drawCenteredText(g2, windDirHeader, cx, cy - 7)
    g2.color = palette.metarAmber
    g2.font = Font("Segoe UI", Font.BOLD, 12)
    drawCenteredText(g2, windSpeedFooter, cx, cy + 11)

    g2.dispose()
  }

  private fun drawCornerBadge(
    g2: Graphics2D,
    x: Int,
    y: Int,
    value: String,
    label: String,
    valueColor: Color,
  ) {
    val badgeW = 58
    val badgeH = 22
    g2.color = palette.fieldBackground
    g2.fillRoundRect(x, y, badgeW, badgeH, 4, 4)
    g2.color = palette.border
    g2.stroke = BasicStroke(1f)
    g2.drawRoundRect(x, y, badgeW, badgeH, 4, 4)

    g2.color = valueColor
    g2.font = Font("Segoe UI", Font.BOLD, 12)
    drawCenteredText(g2, value, x + badgeW / 2.0, y + 11.5)

    g2.color = palette.textSecondary
    g2.font = Font("Segoe UI", Font.PLAIN, 11)
    drawCenteredText(g2, label, x + badgeW / 2.0, y + badgeH + 10.0)
  }

  private fun drawCenteredText(g2: Graphics2D, text: String, cx: Double, cy: Double) {
    val fm = g2.fontMetrics
    val tx = cx - fm.stringWidth(text) / 2.0
    val ty = cy + (fm.ascent - fm.descent) / 2.0
    g2.drawString(text, tx.toFloat(), ty.toFloat())
  }
}
