package com.example.windows

import com.example.model.AuthorBrand
import com.example.model.WeatherAlertSeverity
import com.example.model.WeatherChangeAlert
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage
import javax.swing.SwingUtilities

interface DesktopWeatherNotifier {
  fun notify(alert: WeatherChangeAlert)
  fun dispose() {}
}

object NoOpDesktopWeatherNotifier : DesktopWeatherNotifier {
  override fun notify(alert: WeatherChangeAlert) {}
}

class SystemTrayWeatherNotifier(
  private val onNotificationClicked: ((String) -> Unit)? = null,
) : DesktopWeatherNotifier {

  private var trayIcon: TrayIcon? = null

  @Synchronized
  private fun ensureTrayIcon(): TrayIcon? {
    if (trayIcon != null) return trayIcon
    return runCatching {
      if (!SystemTray.isSupported()) return null
      val tray = SystemTray.getSystemTray()
      val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
      val g: Graphics2D = image.createGraphics()
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g.color = Color(0x0E, 0x7C, 0xD6)
      g.fillOval(1, 1, 14, 14)
      g.color = Color.WHITE
      g.fillPolygon(intArrayOf(8, 13, 3), intArrayOf(3, 12, 12), 3)
      g.dispose()

      val icon = TrayIcon(image, AuthorBrand.APP_TITLE).apply {
        isImageAutoSize = true
        addActionListener {
          val cmd = actionCommand
          if (!cmd.isNullOrBlank()) {
            SwingUtilities.invokeLater { onNotificationClicked?.invoke(cmd) }
          }
        }
      }
      tray.add(icon)
      trayIcon = icon
      icon
    }.getOrNull()
  }

  override fun notify(alert: WeatherChangeAlert) {
    SwingUtilities.invokeLater {
      val icon = ensureTrayIcon() ?: return@invokeLater
      icon.actionCommand = alert.icao
      val type = when (alert.severity) {
        WeatherAlertSeverity.CRITICAL -> TrayIcon.MessageType.ERROR
        WeatherAlertSeverity.WARNING -> TrayIcon.MessageType.WARNING
        WeatherAlertSeverity.INFO -> TrayIcon.MessageType.INFO
      }
      runCatching {
        icon.displayMessage(
          "${AuthorBrand.APP_TITLE}: ${alert.icao} (${alert.observationTimeUtc})",
          alert.summary,
          type,
        )
      }
    }
  }

  override fun dispose() {
    runCatching {
      val icon = trayIcon ?: return
      if (SystemTray.isSupported()) {
        SystemTray.getSystemTray().remove(icon)
      }
      trayIcon = null
    }
  }
}
