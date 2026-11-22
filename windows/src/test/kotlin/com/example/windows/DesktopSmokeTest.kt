package com.example.windows

import com.example.data.DataSourceMode
import com.example.data.ServerConfig
import com.example.model.PressureUnit
import com.example.model.WindSpeedUnit
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopSmokeTest {

  @Test
  fun `desktop storage persists favorites and server credentials`() {
    val tmpFile = File.createTempFile("meteotablo_win_test", ".properties")
    tmpFile.deleteOnExit()
    tmpFile.delete()

    val favStorage = DesktopFavoriteStorage(tmpFile)
    val initialFavs = favStorage.loadFavorites()
    assertTrue(initialFavs.containsAll(listOf("UTAM", "UTAA", "UTAK", "UTAN", "UTAT", "UTAV", "UTAE")))

    favStorage.saveFavorites(initialFavs - "UTAK")
    val reloadedFavs = DesktopFavoriteStorage(tmpFile).loadFavorites()
    assertFalse(reloadedFavs.contains("UTAK"))
    assertTrue(reloadedFavs.contains("UTAE"))

    val serverStorage = DesktopServerConfigStorage(tmpFile)
    val defCfg = serverStorage.loadConfig()
    assertEquals("http://217.174.229.206:8181/", defCfg.url)
    assertEquals("meteo", defCfg.username)
    assertEquals("meteo", defCfg.password)

    serverStorage.saveConfig(
      ServerConfig(
        url = "217.174.229.206:8181",
        username = "meteo",
        password = "meteo2",
        useCustomServer = true,
        fallbackToNoaa = true,
        sourceMode = DataSourceMode.AUTO_INTERNET_FIRST,
        useAviationWeatherGov = true,
        useNoaaTgftp = false,
        useVatsimMetar = true,
      ),
    )
    val reloadedCfg = DesktopServerConfigStorage(tmpFile).loadConfig()
    assertEquals("http://217.174.229.206:8181/", reloadedCfg.url)
    assertEquals("meteo2", reloadedCfg.password)
    assertEquals(DataSourceMode.AUTO_INTERNET_FIRST, reloadedCfg.resolvedMode())
    assertFalse(reloadedCfg.useNoaaTgftp)
    assertTrue(reloadedCfg.useVatsimMetar)
  }

  @Test
  fun `desktop controller switches all turkmenistan stations and applies metar`() {
    val controller = DesktopController(autoFetch = false)
    try {
      listOf("UTAM", "UTAA", "UTAK", "UTAN", "UTAT", "UTAV", "UTAE").forEach { icao ->
        controller.selectStation(icao)
        assertEquals(icao, controller.state.station.icao)
        assertTrue(controller.state.weather.rawMetar.contains(icao))
      }

      controller.selectStation("UTAA")
      controller.applyCustomMetar(
        rawMetar = "METAR UTAA 141700Z 08008KT 8000 SCT040 24/09 Q1018 NOSIG",
        rawTaf = "TAF UTAA 141630Z 1418/1518 09006MPS 9999 SCT040",
      )
      assertEquals(1018, controller.state.weather.qnhHpa)
      assertEquals(24, controller.state.weather.temperatureC)
      assertEquals(8, controller.state.weather.wind.speedKt)

      controller.toggleWindUnit()
      assertEquals(WindSpeedUnit.MPS, controller.state.windUnit)
      controller.togglePressureUnit()
      assertEquals(PressureUnit.INHG, controller.state.pressureUnit)

      controller.setDataSourceMode(DataSourceMode.INTERNET_ONLY, fetchNow = false)
      assertEquals(DataSourceMode.INTERNET_ONLY, controller.state.serverConfig.resolvedMode())
      controller.setDataSourceMode(DataSourceMode.AUTO_IP_FIRST, fetchNow = false)
      assertEquals(DataSourceMode.AUTO_IP_FIRST, controller.state.serverConfig.resolvedMode())
    } finally {
      controller.shutdown()
    }
  }

  @Test
  fun `desktop splash panel renders Meteotablo branding cleanly`() {
    val panel = DesktopSplashPanel()
    assertEquals("Meteotablo", panel.appTitle)
    assertTrue(panel.authorCredit.contains("Allanur Saparow"))
    assertTrue(com.example.model.AuthorBrand.isExpectedBrand("Meteotablo"))
    assertFalse(com.example.model.AuthorBrand.isExpectedBrand("OtherApp"))
    panel.setSize(480, 340)
    panel.progress = 0.65f
    val image = java.awt.image.BufferedImage(480, 340, java.awt.image.BufferedImage.TYPE_INT_ARGB)
    val g2 = image.createGraphics()
    try {
      panel.paint(g2)
    } finally {
      g2.dispose()
    }
  }

  @Test
  fun `desktop preset storage saves and recalls airport and runway setups`() {
    val tmpFile = File.createTempFile("meteotablo_win_presets", ".properties")
    tmpFile.deleteOnExit()
    tmpFile.delete()

    val presetStorage = DesktopWeatherPresetStorage(tmpFile)
    val controller1 = DesktopController(presetStorage = presetStorage, autoFetch = false)
    try {
      controller1.selectStation("UTAM")
      controller1.selectRunwayPair(1)
      controller1.setWindUnit(WindSpeedUnit.MPS)
      controller1.setPressureUnit(PressureUnit.INHG)
      controller1.saveCurrentSetup("Мары 35R")
      assertEquals(1, controller1.state.savedPresets.size)
      assertEquals("Мары 35R", controller1.state.savedPresets.first().name)
    } finally {
      controller1.shutdown()
    }

    val controller2 = DesktopController(presetStorage = DesktopWeatherPresetStorage(tmpFile), autoFetch = false)
    try {
      controller2.selectStation("UTAE")
      controller2.setWindUnit(WindSpeedUnit.KT)
      controller2.setPressureUnit(PressureUnit.HPA)
      val savedId = controller2.state.savedPresets.first().id
      controller2.applySavedPreset(savedId)
      assertEquals("UTAM", controller2.state.station.icao)
      assertEquals(1, controller2.state.selectedRunwayPairIndex)
      assertEquals("35R", controller2.state.activeRunwayPair.primaryDesignator)
      assertEquals(WindSpeedUnit.MPS, controller2.state.windUnit)
      assertEquals(PressureUnit.INHG, controller2.state.pressureUnit)

      controller2.deleteSavedPreset(savedId)
      assertTrue(controller2.state.savedPresets.isEmpty())
    } finally {
      controller2.shutdown()
    }
  }

  @Test
  fun `desktop controller detects significant weather changes for favorite airports and persists alerts`() {
    val tmpFile = File.createTempFile("meteotablo_win_alerts", ".properties")
    tmpFile.deleteOnExit()
    tmpFile.delete()

    val alertStorage = DesktopWeatherAlertStorage(tmpFile)
    val notifiedAlerts = mutableListOf<com.example.model.WeatherChangeAlert>()
    val fakeNotifier = object : DesktopWeatherNotifier {
      override fun notify(alert: com.example.model.WeatherChangeAlert) {
        notifiedAlerts.add(alert)
      }
    }

    val controller = DesktopController(
      favoriteStorage = DesktopFavoriteStorage(tmpFile),
      alertStorage = alertStorage,
      weatherNotifier = fakeNotifier,
      autoFetch = false,
    )
    try {
      controller.selectStation("UTAA")
      // 1. Baseline weather
      controller.applyCustomMetar(
        rawMetar = "METAR UTAA 151000Z 09006KT CAVOK 24/08 Q1016 NOSIG",
        rawTaf = "TAF UTAA",
      )
      assertTrue(notifiedAlerts.isEmpty())

      // 2. Significant deterioration
      controller.applyCustomMetar(
        rawMetar = "METAR UTAA 151030Z 31024G35KT 1200 +TSRA BKN005CB 19/17 Q1009",
        rawTaf = "TAF UTAA",
      )
      assertEquals(1, notifiedAlerts.size)
      assertEquals("UTAA", notifiedAlerts.first().icao)
      assertEquals(1, controller.state.unreadAlertCount)

      // 3. Persistence across reload
      val reloaded = DesktopWeatherAlertStorage(tmpFile).loadAlerts()
      assertEquals(1, reloaded.size)
      assertEquals("UTAA", reloaded.first().icao)

      controller.markAllAlertsRead()
      assertEquals(0, controller.state.unreadAlertCount)
    } finally {
      controller.shutdown()
    }
  }
}
