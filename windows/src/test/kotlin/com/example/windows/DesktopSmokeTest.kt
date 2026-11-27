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
    assertEquals("http://217.174.229.206:8181/cgi-bin/wdr", defCfg.url)
    assertEquals("meteo", defCfg.username)
    assertEquals("meteo", defCfg.password)

    serverStorage.saveConfig(
      ServerConfig(
        url = "217.174.229.206:8181/cgi-bin/wdr",
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
    assertEquals("http://217.174.229.206:8181/cgi-bin/wdr", reloadedCfg.url)
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
      assertEquals(4000, controller.state.weather.clouds.first().baseFeet)

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

  @Test
  fun `desktop wind history caches per airport computes trend and paints sparkline`() {
    val tmpFile = File.createTempFile("meteotablo_win_wind_hist", ".properties")
    tmpFile.deleteOnExit()
    tmpFile.delete()

    val historyStorage = DesktopWindHistoryStorage(tmpFile)
    val controller = DesktopController(windHistoryStorage = historyStorage, autoFetch = false)
    try {
      controller.selectStation("UTAA")
      controller.applyCustomMetar("METAR UTAA 150900Z 09005KT CAVOK 22/08 Q1016 NOSIG", "TAF UTAA")
      assertEquals(com.example.model.WindTrendDirection.INSUFFICIENT, controller.state.windTrend.direction)

      controller.applyCustomMetar("METAR UTAA 150930Z 09015KT CAVOK 23/08 Q1015 NOSIG", "TAF UTAA")
      assertEquals(com.example.model.WindTrendDirection.INCREASING, controller.state.windTrend.direction)
      assertEquals(10, controller.state.windTrend.deltaKt)

      controller.applyCustomMetar("METAR UTAA 151000Z 09006KT CAVOK 24/08 Q1015 NOSIG", "TAF UTAA")
      assertEquals(com.example.model.WindTrendDirection.DECREASING, controller.state.windTrend.direction)
      assertEquals(-9, controller.state.windTrend.deltaKt)

      // Paint sparkline onto Graphics2D
      val panel = WindTrendSparklinePanel()
      panel.updateData(controller.state.windTrend, WindSpeedUnit.KT, DayDesktopPalette)
      panel.setSize(280, 74)
      val image = java.awt.image.BufferedImage(280, 74, java.awt.image.BufferedImage.TYPE_INT_ARGB)
      val g2 = image.createGraphics()
      try {
        panel.paint(g2)
      } finally {
        g2.dispose()
      }
    } finally {
      controller.shutdown()
    }

    val reloaded = DesktopController(windHistoryStorage = DesktopWindHistoryStorage(tmpFile), autoFetch = false)
    try {
      reloaded.selectStation("UTAA")
      assertEquals(3, reloaded.state.windTrend.entries.size)
      assertEquals(com.example.model.WindTrendDirection.DECREASING, reloaded.state.windTrend.direction)
    } finally {
      reloaded.shutdown()
    }
  }

  @Test
  fun `server config normalization preserves php endpoints and builds retrofit base url`() {
    assertEquals(
      "http://217.174.229.206:8181/",
      ServerConfig.normalizeServerUrl("217.174.229.206:8181"),
    )
    assertEquals(
      "http://217.174.229.206:8181/cgi-bin/wlogin",
      ServerConfig.normalizeServerUrl("217.174.229.206:8181/cgi-bin/wlogin"),
    )
    assertEquals(
      "http://217.174.229.206:8181/index.php",
      ServerConfig.normalizeServerUrl("217.174.229.206:8181/index.php"),
    )
    assertEquals(
      "http://217.174.229.206:8181/meteo/svodka.php?station=UTAM",
      ServerConfig.normalizeServerUrl("http://217.174.229.206:8181/meteo/svodka.php?station=UTAM"),
    )
    assertEquals(
      "http://217.174.229.206:8181/meteo/",
      ServerConfig.retrofitBaseUrl("http://217.174.229.206:8181/meteo/svodka.php?station=UTAM"),
    )
    assertEquals(
      "http://217.174.229.206:8181/",
      ServerConfig.retrofitBaseUrl("http://217.174.229.206:8181/index.php"),
    )
  }

  @Test
  fun `custom meteo server client extracts frames meta refresh and multiline metar`() {
    val base = okhttp3.HttpUrl.Builder().scheme("http").host("217.174.229.206").port(8181).build()
    val htmlWithRefresh = """
      <html><head><meta http-equiv="refresh" content="0; url=/tablo/main.php"></head></html>
    """.trimIndent()
    assertEquals(
      "http://217.174.229.206:8181/tablo/main.php",
      com.example.data.CustomMeteoServerClient.extractMetaRefreshUrl(htmlWithRefresh, base),
    )

    val framesHtml = """
      <frameset cols="200,*"><frame src="nav.php"><iframe src="/meteo/utam.php"></iframe></frameset>
    """.trimIndent()
    val frames = com.example.data.CustomMeteoServerClient.extractFrameUrls(framesHtml, base)
    assertEquals(2, frames.size)
    assertTrue(frames.any { it.endsWith("/meteo/utam.php") })

    val tableHtml = """
      <table><tr><td>METAR<br>UTAM 231100Z 09012MPS 6000 BKN030 22/08 Q1017 NOSIG=</td></tr>
      <tr><td>TAF UTAM 231000Z 2312/2412 09010MPS 9999 SCT040=</td></tr></table>
    """.trimIndent()
    val (metar, taf) = com.example.data.CustomMeteoServerClient.extractMetarAndTaf(tableHtml, "UTAM")
    assertTrue(metar != null && metar.contains("UTAM 231100Z") && metar.contains("09012MPS"))
    assertTrue(taf != null && taf.contains("TAF UTAM 231000Z"))
  }

  @Test
  fun `desktop airport map panel computes mercator tiles caches pin weather and paints cleanly`() {
    val controller = DesktopController(autoFetch = false)
    try {
      controller.selectStation("UTAA")
      controller.applyCustomMetar("METAR UTAA 231100Z 08014KT CAVOK 26/07 Q1019 NOSIG", "TAF UTAA")
      assertTrue(controller.state.mapWeatherByIcao.containsKey("UTAA"))
      assertTrue(controller.state.mapStations.any { it.icao == "UTAM" })

      val cam = com.example.domain.AirportMapProjection.cameraForRegion(
        preset = com.example.domain.MapRegionPreset.TURKMENISTAN,
        stations = controller.state.mapStations,
        widthPx = 800f,
        heightPx = 500f,
      )
      val tiles = com.example.domain.AirportMapProjection.visibleTiles(cam, 800f, 500f)
      assertTrue(tiles.isNotEmpty())
      val panned = com.example.domain.AirportMapProjection.panCamera(cam, 120f, -60f)
      assertTrue(panned.centerLon != cam.centerLon || panned.centerLat != cam.centerLat)

      val mapPanel = AirportMapDesktopPanel(
        onBackToBoard = {},
        onOpenStationOnBoard = {},
        onRefreshAllStations = {},
        onRefreshSingleStation = {},
        onToggleFavorite = {},
      )
      mapPanel.setLiveTileFetchingEnabled(false)
      mapPanel.applyState(controller.state)
      mapPanel.selectPin("UTAA")
      mapPanel.setSize(980, 560)
      mapPanel.doLayout()
      val image = java.awt.image.BufferedImage(980, 560, java.awt.image.BufferedImage.TYPE_INT_ARGB)
      val g2 = image.createGraphics()
      try {
        mapPanel.paint(g2)
      } finally {
        g2.dispose()
      }
    } finally {
      controller.shutdown()
    }
  }

  @Test
  fun `desktop controller supports metric-imperial units auto-refresh vfr alerts coordinates and 24h trends panel`() {
    val controller = DesktopController(autoFetch = false)
    try {
      // 1. UnitSystem toggle (Metric <-> Imperial)
      controller.applyUnitSystem(com.example.model.UnitSystem.METRIC)
      assertEquals(WindSpeedUnit.MPS, controller.state.windUnit)
      assertEquals(PressureUnit.HPA, controller.state.pressureUnit)
      controller.applyUnitSystem(com.example.model.UnitSystem.IMPERIAL)
      assertEquals(WindSpeedUnit.KT, controller.state.windUnit)
      assertEquals(PressureUnit.INHG, controller.state.pressureUnit)

      // 2. Auto-refresh config (5 / 15 / 30m + disable/enable)
      controller.setAutoRefreshInterval(5)
      assertEquals(5, controller.state.autoRefreshIntervalMinutes)
      controller.setAutoRefreshEnabled(false)
      assertFalse(controller.state.autoRefreshEnabled)
      controller.setAutoRefreshInterval(30)
      assertEquals(30, controller.state.autoRefreshIntervalMinutes)

      // 3. Invalid coordinates handling
      controller.fetchWeatherByCoordinates(120.0, 50.0)
      assertTrue(controller.state.statusBanner?.contains("Некорректные координаты") == true)

      // 4. VFR minimums breach detection
      controller.selectStation("UTAA")
      controller.applyCustomMetar("METAR UTAA 251000Z 27024KT 1800 BR BKN007 10/08 Q1008", "TAF UTAA")
      controller.updateAlertConfig(
        controller.state.alertConfig.copy(
          enabled = true,
          vfrAlertsEnabled = true,
          vfrMinVisibilityMeters = 5000,
          vfrMinCeilingFeet = 1500,
          vfrMaxWindKt = 20,
          vfrMonitorAllFavorites = true,
        ),
      )
      controller.checkVfrMinimumsNow()
      assertTrue(controller.state.weatherAlerts.any { it.icao == "UTAA" })

      // 5. 24h Trends chart panel state binding & painting
      val trendsPanel = WindPressure24hChartPanel(
        onBackToBoard = {},
        onSelectAirport = {},
        onRefreshHistory = {},
        onSelectUnitSystem = {},
        onOpenExport = {},
      )
      trendsPanel.applyState(controller.state)
      trendsPanel.setSize(960, 560)
      trendsPanel.doLayout()
      val image = java.awt.image.BufferedImage(960, 560, java.awt.image.BufferedImage.TYPE_INT_ARGB)
      val g2 = image.createGraphics()
      try {
        trendsPanel.paint(g2)
      } finally {
        g2.dispose()
      }

      // 6. Decoder + Report formatter export
      val decoded = com.example.domain.MetarTafDecoder.decodeMetar(controller.state.weather.rawMetar, controller.state.station)
      assertTrue(decoded.items.isNotEmpty())
      val payload = com.example.domain.WeatherExportPayload(
        station = controller.state.station,
        weather = controller.state.weather,
        primaryRunwayCalc = controller.state.primaryRunwayCalc,
        reciprocalRunwayCalc = controller.state.reciprocalRunwayCalc,
        windUnit = controller.state.windUnit,
        pressureUnit = controller.state.pressureUnit,
        windTrend = controller.state.windTrend,
        qnhTrend24h = controller.state.qnhTrend24h,
        weatherHistory24h = controller.state.weatherHistory24h,
      )
      val summaryText = com.example.domain.WeatherReportFormatter.buildTextSummary(payload)
      assertTrue(summaryText.contains("UTAA"))
      assertTrue(summaryText.contains("DA "))
      assertFalse(summaryText.contains("Allanur"))
      assertTrue(com.example.domain.WeatherReportFormatter.buildPrintableHtml(payload).contains("<html lang=\"ru\">"))
    } finally {
      controller.shutdown()
    }
  }

  @Test
  fun `desktop recent searches storage persists last 5 ICAOs and wind rose pulses on fetch`() {
    val tmpFile = File.createTempFile("meteotablo_win_recent", ".properties")
    tmpFile.deleteOnExit()
    tmpFile.delete()

    val recentStorage = DesktopRecentSearchesStorage(tmpFile)
    val controller1 = DesktopController(recentSearchesStorage = recentStorage, autoFetch = false)
    try {
      listOf("UTAM", "UTAA", "UTAK", "UTAN", "UTAT", "UTAV").forEach { icao ->
        controller1.selectStation(icao)
      }
      controller1.selectStation("UTAA")
      assertEquals(listOf("UTAA", "UTAV", "UTAT", "UTAN", "UTAK"), controller1.state.recentSearches)

      val prevSeq = controller1.state.metarFetchSeq
      controller1.applyCustomMetar("METAR UTAA 261000Z 27012KT CAVOK 22/07 Q1018", "TAF UTAA")
      assertEquals(prevSeq + 1L, controller1.state.metarFetchSeq)

      val rose = RunwayWindRosePanel {}
      rose.updateData(
        controller1.state.primaryRunwayCalc,
        controller1.state.weather.wind,
        controller1.state.windUnit,
        DayDesktopPalette,
        controller1.state.metarFetchSeq,
      )
      assertTrue(rose.pulseDeg < 0.0)
      rose.setSize(280, 235)
      val image = java.awt.image.BufferedImage(280, 235, java.awt.image.BufferedImage.TYPE_INT_ARGB)
      val g2 = image.createGraphics()
      try {
        rose.paint(g2)
      } finally {
        g2.dispose()
      }
      rose.removeNotify()
    } finally {
      controller1.shutdown()
    }

    val controller2 = DesktopController(recentSearchesStorage = DesktopRecentSearchesStorage(tmpFile), autoFetch = false)
    try {
      assertEquals(listOf("UTAA", "UTAV", "UTAT", "UTAN", "UTAK"), controller2.state.recentSearches)
      controller2.clearRecentSearches()
      assertTrue(controller2.state.recentSearches.isEmpty())
      assertTrue(DesktopRecentSearchesStorage(tmpFile).loadRecentSearches().isEmpty())
    } finally {
      controller2.shutdown()
    }
  }

  @Test
  fun `desktop weather cache storage persists METAR offline and DesktopThemeHelper fixes Night button contrast`() {
    val cacheFile = File.createTempFile("meteotablo_win_cache", ".properties")
    cacheFile.deleteOnExit()
    cacheFile.delete()

    val cacheRepo = DesktopWeatherCacheStorage(cacheFile)
    val utam = com.example.data.AirportCatalog.findOrDefault("UTAM")
    val validWeather = com.example.domain.AviationWeatherParser.parse(
      rawMetarInput = "METAR UTAM 270800Z 29014G22KT 9999 SCT030 20/07 Q1019 NOSIG",
      rawTafInput = "TAF UTAM 270500Z 2706/2806 29012KT CAVOK",
      station = utam,
      sourceLabel = "ведомственный сервер",
    )

    kotlinx.coroutines.runBlocking {
      cacheRepo.saveWeather(utam, validWeather, 123456789L)
    }
    val reloadedRepo = DesktopWeatherCacheStorage(cacheFile)
    val loadedRecord = kotlinx.coroutines.runBlocking {
      reloadedRepo.getCachedWeather("UTAM")
    }
    assertTrue(loadedRecord != null)
    assertEquals(validWeather.rawMetar, loadedRecord!!.rawMetar)
    assertEquals(validWeather.rawTaf, loadedRecord.rawTaf)

    // Controller preload + sanitized server log
    val controller = DesktopController(weatherCacheRepository = reloadedRepo, autoFetch = false)
    try {
      Thread.sleep(200)
      assertTrue(controller.state.weather.rawMetar.contains("270800Z"))
      assertTrue(controller.state.isOfflineCachedWeather)

      controller.updateServerConfig(
        com.example.data.ServerConfig(
          url = "http://217.174.229.206:8181/cgi-bin/wdr",
          username = "secretuser",
          password = "secretpassword",
        ),
        fetchNow = false,
      )
      val allLogs = controller.state.logs.joinToString("\n") { it.message }
      assertFalse(allLogs.contains("217.174.229.206"))
      assertFalse(allLogs.contains("secretuser"))
      assertFalse(allLogs.contains("secretpassword"))
    } finally {
      controller.shutdown()
    }

    // Verify Night mode DesktopThemeHelper applies BasicButtonUI with dark background & bright text
    val btn = javax.swing.JButton("Тест")
    val combo = javax.swing.JComboBox(arrayOf("UTAM", "UTAA"))
    val panel = javax.swing.JPanel().apply {
      add(btn)
      add(combo)
    }
    DesktopThemeHelper.applyThemeTree(panel, NightDesktopPalette)
    assertTrue(btn.ui is javax.swing.plaf.basic.BasicButtonUI)
    assertTrue(btn.isOpaque)
    assertEquals(NightDesktopPalette.buttonBackground, btn.background)
    assertEquals(NightDesktopPalette.textPrimary, btn.foreground)
    assertTrue(combo.ui is javax.swing.plaf.basic.BasicComboBoxUI)
    assertEquals(NightDesktopPalette.fieldBackground, combo.background)
    assertEquals(NightDesktopPalette.textPrimary, combo.foreground)
  }
}

