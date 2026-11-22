package com.example.windows

import javax.swing.SwingUtilities
import javax.swing.UIManager

fun main() {
  System.setProperty("awt.useSystemAAFontSettings", "on")
  System.setProperty("swing.aatext", "true")
  System.setProperty("file.encoding", "UTF-8")

  SwingUtilities.invokeLater {
    runCatching {
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
    }
    var controllerRef: DesktopController? = null
    val controller = DesktopController(
      favoriteStorage = DesktopFavoriteStorage(),
      serverConfigStorage = DesktopServerConfigStorage(),
      presetStorage = DesktopWeatherPresetStorage(),
      alertStorage = DesktopWeatherAlertStorage(),
      weatherNotifier = SystemTrayWeatherNotifier { icao ->
        controllerRef?.openAlertStation(icao)
      },
      autoFetch = true,
    )
    controllerRef = controller
    DesktopSplashLauncher.showAndLaunch {
      MeteoDesktopFrame(controller).isVisible = true
    }
  }
}
