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
    val controller = DesktopController(
      favoriteStorage = DesktopFavoriteStorage(),
      serverConfigStorage = DesktopServerConfigStorage(),
      autoFetch = true,
    )
    MeteoDesktopFrame(controller).isVisible = true
  }
}
