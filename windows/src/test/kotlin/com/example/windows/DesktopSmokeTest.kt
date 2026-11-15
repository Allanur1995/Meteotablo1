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
}
