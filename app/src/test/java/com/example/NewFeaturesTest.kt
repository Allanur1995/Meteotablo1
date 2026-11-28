package com.example

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.example.data.AirportCatalog
import com.example.data.AirportRunwaysSerializer
import com.example.data.AutoRefreshInterval
import com.example.data.InMemoryUnitPreferencesStorage
import com.example.data.SharedPreferencesAirportRunwaysStorage
import com.example.data.WeatherRadarRepository
import com.example.domain.AviationWeatherParser
import com.example.domain.D3TrendsChartDataBuilder
import com.example.domain.DispatcherBriefingHelper
import com.example.domain.FlightCategory
import com.example.domain.UnitConverter
import com.example.model.AircraftTypePreset
import com.example.model.PressureUnit
import com.example.model.WeatherAlertConfig
import com.example.model.WeatherHistoryEntry
import com.example.model.WindSpeedUnit
import com.example.network.AviationWeatherNetworkManager
import com.example.ui.MeteoTabloScreen
import com.example.ui.WeatherRadarScreen
import com.example.ui.components.DispatcherBriefingCard
import com.example.ui.components.RunwayManagerDialog
import com.example.ui.components.SigmetAlertsDialog
import com.example.ui.theme.MeteoTabloTheme
import com.example.viewmodel.MeteoViewModel
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NewFeaturesTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `default auto refresh interval is 60 seconds and main board displays 60s badge`() {
    val vm = MeteoViewModel(
      unitPreferencesStorage = InMemoryUnitPreferencesStorage(),
      autoFetch = false,
    )
    assertEquals(1, AutoRefreshInterval.ONE_MIN.minutes)
    assertEquals("60с", AutoRefreshInterval.ONE_MIN.shortLabel)
    assertEquals(1, vm.uiState.value.autoRefreshIntervalMinutes)
    assertTrue(vm.uiState.value.autoRefreshEnabled)

    composeTestRule.setContent {
      MeteoTabloTheme(isNightMode = false) {
        MeteoTabloScreen(viewModel = vm, onFullscreenChanged = {})
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("toolbar_auto_refresh_badge")
      .performScrollTo()
      .assertTextContains("АВТО 60с")
  }

  @Test
  fun `aircraft presets include B737 B738 B773 and crosswind limit checks work`() {
    assertEquals(35, AircraftTypePreset.B737.defaultMaxCrosswindKt)
    assertEquals(36, AircraftTypePreset.B738.defaultMaxCrosswindKt)
    assertEquals(38, AircraftTypePreset.B773.defaultMaxCrosswindKt)

    val cfg = WeatherAlertConfig(
      crosswindWarningEnabled = true,
      aircraftTypeId = AircraftTypePreset.B738.id,
      maxCrosswindKt = AircraftTypePreset.B738.defaultMaxCrosswindKt,
    )
    assertEquals("B738", cfg.resolvedAircraftName())
    assertFalse(cfg.isCrosswindExceeded(34.0))
    assertTrue(cfg.isCrosswindExceeded(37.0))
    assertTrue(cfg.isCrosswindExceeded(25.0, gustCrosswindKt = 38.0))
  }

  @Test
  fun `runway manager saves persists resets and updates airport magnetic headings`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val runwaysStorage = SharedPreferencesAirportRunwaysStorage(context)
    runwaysStorage.clearForAirport("UTAA")

    val pair = AirportRunwaysSerializer.buildRunwayPair("09L", 92)
    assertEquals("09L", pair.primaryDesignator)
    assertEquals(92, pair.primaryHeadingDeg)
    assertEquals("27R", pair.reciprocalDesignator)
    assertEquals(272, pair.reciprocalHeadingDeg)

    val vm = MeteoViewModel(airportRunwaysStorage = runwaysStorage, autoFetch = false)
    vm.selectStation("UTAA")
    val initialCount = vm.uiState.value.station.runways.size

    composeTestRule.setContent {
      val uiState by vm.uiState.collectAsState()
      MeteoTabloTheme(isNightMode = false) {
        RunwayManagerDialog(
          station = uiState.station,
          selectedRunwayIndex = uiState.selectedRunwayPairIndex,
          onSelectRunwayPair = { vm.selectRunwayPair(it) },
          onAddRunwayPair = { d, h -> vm.addRunwayPair(d, h) },
          onRemoveRunwayPair = { vm.removeRunwayPair(it) },
          onResetRunwaysToDefault = { vm.resetRunwaysToDefault() },
          onDismiss = {},
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("runway_manager_dialog").assertIsDisplayed()
    composeTestRule.onNodeWithTag("runway_designator_input").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("runway_designator_input").performTextReplacement("09L")
    composeTestRule.onNodeWithTag("runway_heading_input").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("runway_heading_input").performTextReplacement("092")
    composeTestRule.onNodeWithTag("add_runway_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    assertEquals(initialCount + 1, vm.uiState.value.station.runways.size)
    assertEquals("09L", vm.uiState.value.station.runways.last().primaryDesignator)
    assertEquals(92, vm.uiState.value.station.runways.last().primaryHeadingDeg)

    // Verify persistence across ViewModel instances
    val vmReloaded = MeteoViewModel(
      airportRunwaysStorage = SharedPreferencesAirportRunwaysStorage(context),
      autoFetch = false,
    )
    vmReloaded.selectStation("UTAA")
    assertEquals(initialCount + 1, vmReloaded.uiState.value.station.runways.size)
    assertEquals("09L", vmReloaded.uiState.value.station.runways.last().primaryDesignator)

    vmReloaded.resetRunwaysToDefault()
    assertEquals(initialCount, vmReloaded.uiState.value.station.runways.size)
  }

  @Test
  fun `dispatcher briefing evaluates category hazards and renders briefing card`() {
    val station = AirportCatalog.findOrDefault("UTAA")
    val stormyWeather = AviationWeatherParser.parse(
      rawMetarInput = "METAR UTAA 281000Z 21028G42KT 1200 +TSRA BKN004CB 18/17 Q1004",
      rawTafInput = "TAF UTAA 280900Z 2810/2910 21022G35KT 1500 TSRA",
      station = station,
      sourceLabel = "test",
    )
    val cfg = WeatherAlertConfig(
      crosswindWarningEnabled = true,
      aircraftTypeId = AircraftTypePreset.CESSNA_172.id,
      maxCrosswindKt = AircraftTypePreset.CESSNA_172.defaultMaxCrosswindKt,
    )
    val summary = DispatcherBriefingHelper.evaluate(station, stormyWeather, cfg)
    assertEquals(FlightCategory.IFR, summary.flightCategory)
    assertEquals(1, summary.tempDewSpreadC)
    assertEquals(14, summary.gustSpreadKt)
    assertTrue(summary.hazards.any { it.contains("Гроз") || it.contains("TS") })
    assertTrue(summary.hazards.any { it.contains("Превышение бок. ветра") })

    composeTestRule.setContent {
      MeteoTabloTheme(isNightMode = true) {
        DispatcherBriefingCard(
          station = station,
          weather = stormyWeather,
          windUnit = WindSpeedUnit.KT,
          pressureUnit = PressureUnit.HPA,
          alertConfig = cfg,
          activeSigmets = emptyList(),
          isLoadingSigmets = false,
          onOpenSigmetDialog = {},
          onOpenManageRunways = {},
          onSelectRunwayPair = {},
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("dispatcher_briefing_card").assertIsDisplayed()
    composeTestRule.onNodeWithTag("dispatcher_sigmet_chip").assertIsDisplayed()
    composeTestRule.onNodeWithTag("dispatcher_manage_runways_chip").assertIsDisplayed()
    composeTestRule.onNodeWithText("IFR", substring = false).assertIsDisplayed()
  }

  @Test
  fun `sigmet parser filters near airport and dialog displays metric and imperial altitudes`() {
    val station = AirportCatalog.findOrDefault("UTAA")
    val json = """
      [
        {
          "icaoId": "UTAA",
          "firId": "UTAK",
          "firName": "ASHGABAT FIR",
          "hazard": "TS",
          "qualifier": "EMBD",
          "validTimeFrom": 1764320000,
          "validTimeTo": 1764334400,
          "altitudeLow1": 10000,
          "altitudeHi1": 35000,
          "coords": [
            {"lat": 37.9, "lon": 58.3},
            {"lat": 38.4, "lon": 59.0}
          ],
          "rawSigmet": "UTAK SIGMET 1 VALID 280900/281300 UTAA- EMBD TS OBS WI 38N058E"
        }
      ]
    """.trimIndent()
    val sigmets = AviationWeatherNetworkManager.parseSigmetsJson(json, station = station)
    assertEquals(1, sigmets.size)
    val alert = sigmets.first()
    assertTrue(alert.altitudeRangeLabel(PressureUnit.HPA).contains("м"))
    assertTrue(alert.altitudeRangeLabel(PressureUnit.INHG).contains("FL100"))

    composeTestRule.setContent {
      MeteoTabloTheme(isNightMode = false) {
        SigmetAlertsDialog(
          station = station,
          pressureUnit = PressureUnit.HPA,
          sigmets = sigmets,
          isLoading = false,
          onRefresh = {},
          onDismiss = {},
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("sigmet_dialog").assertIsDisplayed()
    composeTestRule.onNodeWithTag("sigmet_item_0").assertIsDisplayed()
    composeTestRule.onNodeWithText("SIGMET · Гроза / Конвекция", substring = true).assertIsDisplayed()
  }

  @Test
  fun `rainviewer json parser and WeatherRadarScreen render timeline safely`() {
    val rainViewerJson = """
      {
        "host": "https://tilecache.rainviewer.com",
        "radar": {
          "past": [
            {"time": 1764320400, "path": "/v2/radar/1764320400"},
            {"time": 1764321000, "path": "/v2/radar/1764321000"}
          ],
          "nowcast": [
            {"time": 1764321600, "path": "/v2/radar/1764321600"}
          ]
        }
      }
    """.trimIndent()
    val timeline = WeatherRadarRepository.parseRainViewerJson(rainViewerJson, nowMillis = 1000L)
    assertEquals("https://tilecache.rainviewer.com", timeline.host)
    assertEquals(2, timeline.frames.size)
    assertFalse(timeline.frames[0].isNowcast)
    val zoomedReq = timeline.frames[0].resolveTileRequest(timeline.host, z = 9, x = 340, y = 195)
    assertEquals(7, zoomedReq.fetchZ)
    assertEquals(4, zoomedReq.subGridSize)
    assertEquals(85, zoomedReq.fetchX)
    assertEquals(48, zoomedReq.fetchY)

    val libreTimeline = WeatherRadarRepository.parseRainViewerJson(
      jsonBody = rainViewerJson,
      nowMillis = 1000L,
      sourceMode = com.example.data.RadarSourceMode.LIBREWXR,
      defaultHost = WeatherRadarRepository.LIBREWXR_HOST,
    )
    assertEquals(3, libreTimeline.frames.size)
    assertTrue(libreTimeline.frames[2].isNowcast)

    val nasaSnap = WeatherRadarRepository.buildNasaGibsSnapshot(nowMillis = 1000L)
    assertEquals(com.example.data.RadarSourceMode.NASA_GIBS, nasaSnap.activeSourceMode)
    assertTrue(nasaSnap.frames.first().tileUrl(nasaSnap.host, 5, 12, 9).contains("GoogleMapsCompatible_Level6/5/9/12.png"))

    val owmSnap = WeatherRadarRepository.buildOpenWeatherSnapshot("demo_key", nowMillis = 1000L)
    assertTrue(owmSnap.frames.first().tileUrl(owmSnap.host, 5, 12, 9, "demo_key").contains("appid=demo_key"))

    val station = AirportCatalog.findOrDefault("UTAA")
    var backedOut = false
    composeTestRule.setContent {
      MeteoTabloTheme(isNightMode = true) {
        WeatherRadarScreen(
          stations = AirportCatalog.presets,
          activeStation = station,
          isNightMode = true,
          isFullscreen = false,
          onSelectStationOnBoard = {},
          onBack = { backedOut = true },
          initialTimeline = libreTimeline,
          autoLoadTimeline = false,
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("weather_radar_screen").assertIsDisplayed()
    composeTestRule.onNodeWithTag("radar_source_api_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("radar_play_pause_button").assertIsDisplayed().performClick()
    composeTestRule.onNodeWithTag("radar_timeline_slider").assertIsDisplayed()
    composeTestRule.onNodeWithTag("radar_back_button").performClick()
    assertTrue(backedOut)
  }

  @Test
  fun `metric and imperial converters format altitudes visibility rvr and d3 wind_vis trend payload`() {
    assertEquals("211 м", UnitConverter.formatElevation(211, PressureUnit.HPA))
    assertEquals("692 ft", UnitConverter.formatElevation(211, PressureUnit.INHG))

    assertEquals("900" to "м", UnitConverter.formatCeilingValueAndUnit(900, 3000, false, PressureUnit.HPA))
    assertEquals("3000" to "ft", UnitConverter.formatCeilingValueAndUnit(900, 3000, false, PressureUnit.INHG))

    assertEquals("550" to "м", UnitConverter.formatRvrValueAndUnit(550, PressureUnit.HPA))
    assertEquals("1804" to "ft", UnitConverter.formatRvrValueAndUnit(550, PressureUnit.INHG))

    assertEquals("3200" to "м", UnitConverter.formatVisibility(3200, false, PressureUnit.HPA))
    assertEquals("2.0" to "SM", UnitConverter.formatVisibility(3200, false, PressureUnit.INHG))

    val station = AirportCatalog.findOrDefault("UTAA")
    val entries = listOf(
      WeatherHistoryEntry(
        icao = "UTAA",
        observationTimeUtc = "280800Z",
        observedAtMillis = 1000L,
        windSpeedKt = 10,
        gustKt = 18,
        windDirectionDeg = 270,
        visibilityMeters = 9999,
        isCavok = true,
        temperatureC = 20,
        dewPointC = 8,
        qnhHpa = 1018,
        qnhInHg = 29.98,
        rawMetar = "METAR UTAA 280800Z 27010G18KT CAVOK 20/08 Q1018",
      ),
      WeatherHistoryEntry(
        icao = "UTAA",
        observationTimeUtc = "280900Z",
        observedAtMillis = 4600L,
        windSpeedKt = 16,
        gustKt = 25,
        windDirectionDeg = 290,
        visibilityMeters = 4500,
        isCavok = false,
        temperatureC = 21,
        dewPointC = 9,
        qnhHpa = 1015,
        qnhInHg = 29.90,
        rawMetar = "METAR UTAA 280900Z 29016G25KT 4500 21/09 Q1015",
      ),
    )
    val model = D3TrendsChartDataBuilder.build(
      station = station,
      historyEntries = entries,
      liveFallbackWeather = null,
      windUnit = WindSpeedUnit.KT,
      pressureUnit = PressureUnit.HPA,
      referenceNowMillis = 5000L,
    )
    val payloadJson = JSONObject(model.toD3JsonPayload(darkTheme = true, mode = "WIND_VIS"))
    assertEquals("WIND_VIS", payloadJson.getString("mode"))
    assertEquals(true, payloadJson.getBoolean("darkTheme"))
    assertEquals(2, payloadJson.getJSONArray("points").length())
    assertEquals(4500, payloadJson.getJSONArray("points").getJSONObject(1).getInt("visMeters"))
  }
}
