package com.example.windows

import com.example.model.AuthorBrand
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.GradientPaint
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import javax.swing.JPanel
import javax.swing.JWindow
import javax.swing.Timer
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class DesktopSplashPanel(
  val appTitle: String = AuthorBrand.APP_TITLE,
  val subtitle: String = AuthorBrand.SUBTITLE,
  val authorCredit: String = AuthorBrand.COPYRIGHT_LINE,
) : JPanel() {

  var progress: Float = 0.2f
    set(value) {
      field = value.coerceIn(0f, 1f)
      repaint()
    }

  init {
    preferredSize = Dimension(480, 340)
    isOpaque = true
  }

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)
    val g2 = (g.create() as Graphics2D).apply {
      setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
      setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
    }
    try {
      val w = width.toFloat()
      val h = height.toFloat()

      // Deep aviation gradient background
      g2.paint = GradientPaint(0f, 0f, Color(0x06, 0x0B, 0x12), 0f, h, Color(0x0E, 0x1B, 0x2E))
      g2.fillRect(0, 0, width, height)

      // Subtle outer bezel border
      g2.color = Color(0x2D, 0x3E, 0x54)
      g2.stroke = BasicStroke(1.5f)
      g2.drawRect(1, 1, width - 3, height - 3)

      // Background radar rings
      val cx = w / 2f
      val logoCy = h * 0.38f
      g2.stroke = BasicStroke(1f)
      g2.color = Color(0x1F, 0x33, 0x4D, 110)
      listOf(72f, 118f, 168f).forEach { r ->
        g2.draw(Ellipse2D.Float(cx - r, logoCy - r, r * 2f, r * 2f))
      }

      // Custom Meteotablo runway & compass logo
      val diameter = min(w, h) * 0.42f
      drawBrandLogo(g2, cx, logoCy, diameter)

      // Wordmark: Meteotablo
      val titleFont = Font(Font.SANS_SERIF, Font.BOLD, 30)
      g2.font = titleFont
      val fmTitle = g2.fontMetrics
      val titleW = fmTitle.stringWidth(appTitle)
      val titleY = (logoCy + diameter * 0.68f).toInt()
      g2.color = Color(0xF4, 0xF8, 0xFF)
      g2.drawString(appTitle, (cx - titleW / 2f).toInt(), titleY)

      // Accent rule
      g2.paint = GradientPaint(cx - 32f, 0f, Color(0x60, 0xA5, 0xFA), cx + 32f, 0f, Color(0xFF, 0x98, 0x1A))
      g2.fill(RoundRectangle2D.Float(cx - 32f, titleY + 10f, 64f, 3f, 2f, 2f))

      // Subtitle
      g2.font = Font(Font.SANS_SERIF, Font.PLAIN, 13)
      val fmSub = g2.fontMetrics
      val subW = fmSub.stringWidth(subtitle)
      g2.color = Color(0x9F, 0xB3, 0xC8)
      g2.drawString(subtitle, (cx - subW / 2f).toInt(), titleY + 32)

      // Compiled author copyright line
      g2.font = Font(Font.SANS_SERIF, Font.PLAIN, 11)
      val fmAuthor = g2.fontMetrics
      val authorW = fmAuthor.stringWidth(authorCredit)
      g2.color = Color(0x6C, 0x82, 0x9C)
      g2.drawString(authorCredit, (cx - authorW / 2f).toInt(), titleY + 52)

      // Progress track & indicator
      val barW = 240f
      val barH = 4f
      val barX = cx - barW / 2f
      val barY = h - 34f
      g2.color = Color(0x13, 0x22, 0x36)
      g2.fill(RoundRectangle2D.Float(barX, barY, barW, barH, 3f, 3f))

      val fillW = maxOf(14f, barW * progress)
      g2.paint = GradientPaint(barX, 0f, Color(0x60, 0xA5, 0xFA), barX + fillW, 0f, Color(0xFF, 0x98, 0x1A))
      g2.fill(RoundRectangle2D.Float(barX, barY, fillW, barH, 3f, 3f))
    } finally {
      g2.dispose()
    }
  }

  private fun drawBrandLogo(g2: Graphics2D, cx: Float, cy: Float, diameter: Float) {
    val outerR = diameter * 0.48f
    val dialR = diameter * 0.42f

    // Dial fill & bezel
    g2.color = Color(0x10, 0x1C, 0x2D)
    g2.fill(Ellipse2D.Float(cx - dialR, cy - dialR, dialR * 2f, dialR * 2f))
    g2.color = Color(0x33, 0x4E, 0x70)
    g2.stroke = BasicStroke(diameter * 0.018f)
    g2.draw(Ellipse2D.Float(cx - dialR, cy - dialR, dialR * 2f, dialR * 2f))
    g2.color = Color(0x60, 0xA5, 0xFA, 140)
    g2.stroke = BasicStroke(diameter * 0.012f)
    g2.draw(Ellipse2D.Float(cx - outerR, cy - outerR, outerR * 2f, outerR * 2f))

    // Compass ticks
    for (deg in 0 until 360 step 10) {
      val rad = Math.toRadians(deg - 90.0)
      val isMajor = deg % 30 == 0
      val innerFactor = if (deg % 90 == 0) 0.78f else if (isMajor) 0.84f else 0.90f
      g2.color = if (isMajor) Color(0xE2, 0xEC, 0xF8) else Color(0x5D, 0x76, 0x96)
      g2.stroke = BasicStroke(if (isMajor) diameter * 0.012f else diameter * 0.006f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
      g2.drawLine(
        (cx + dialR * innerFactor * cos(rad)).toInt(),
        (cy + dialR * innerFactor * sin(rad)).toInt(),
        (cx + dialR * 0.96f * cos(rad)).toInt(),
        (cy + dialR * 0.96f * sin(rad)).toInt(),
      )
    }

    // Wind-direction amber arrow
    val savedTx: AffineTransform = g2.transform
    g2.rotate(Math.toRadians(-12.0), cx.toDouble(), cy.toDouble())
    val halfLen = dialR * 0.64f
    val shaftH = diameter * 0.044f
    val headLen = diameter * 0.14f
    val headHalfW = diameter * 0.095f
    val arrow = Path2D.Float().apply {
      moveTo(cx - halfLen, cy - shaftH / 2f)
      lineTo(cx + halfLen - headLen, cy - shaftH / 2f)
      lineTo(cx + halfLen - headLen, cy - headHalfW)
      lineTo(cx + halfLen, cy)
      lineTo(cx + halfLen - headLen, cy + headHalfW)
      lineTo(cx + halfLen - headLen, cy + shaftH / 2f)
      lineTo(cx - halfLen, cy + shaftH / 2f)
      closePath()
    }
    g2.paint = GradientPaint(cx - halfLen, cy, Color(0xFF, 0x98, 0x1A), cx + halfLen, cy, Color(0xFF, 0xB7, 0x4D))
    g2.fill(arrow)
    g2.transform = savedTx

    // Angled runway strip
    g2.rotate(Math.toRadians(42.0), cx.toDouble(), cy.toDouble())
    val rwyW = diameter * 0.14f
    val rwyL = dialR * 1.16f
    val rwyRect = RoundRectangle2D.Float(cx - rwyW / 2f, cy - rwyL / 2f, rwyW, rwyL, diameter * 0.02f, diameter * 0.02f)
    g2.color = Color(0xF1, 0xF5, 0xF9)
    g2.fill(rwyRect)
    g2.color = Color(0x94, 0xA3, 0xB8)
    g2.stroke = BasicStroke(diameter * 0.008f)
    g2.draw(rwyRect)

    g2.color = Color(0x16, 0x22, 0x33)
    g2.stroke = BasicStroke(diameter * 0.018f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    val dashCount = 5
    val usable = rwyL * 0.78f
    val step = usable / dashCount
    val dashLen = step * 0.56f
    val startY = cy - usable / 2f + (step - dashLen) / 2f
    for (i in 0 until dashCount) {
      val y0 = startY + i * step
      g2.drawLine(cx.toInt(), y0.toInt(), cx.toInt(), (y0 + dashLen).toInt())
    }
    g2.transform = savedTx
  }
}

object DesktopSplashLauncher {
  fun showAndLaunch(
    durationMillis: Int = 1150,
    onReady: () -> Unit,
  ) {
    val window = JWindow()
    val panel = DesktopSplashPanel()
    window.contentPane = panel
    window.pack()
    window.setLocationRelativeTo(null)
    window.isVisible = true

    val startAt = System.currentTimeMillis()
    var completed = false
    val timer = Timer(30, null)
    timer.addActionListener {
      val elapsed = (System.currentTimeMillis() - startAt).toFloat()
      val fraction = (elapsed / durationMillis.toFloat()).coerceIn(0f, 1f)
      panel.progress = fraction
      if (fraction >= 1f && !completed) {
        completed = true
        timer.stop()
        window.isVisible = false
        window.dispose()
        onReady()
      }
    }
    timer.start()
  }
}
