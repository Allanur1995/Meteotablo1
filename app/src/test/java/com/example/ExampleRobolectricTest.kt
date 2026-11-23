package com.example

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.example.data.AirportCatalog
import com.example.data.DataSourceMode
import com.example.data.ServerConfig
import com.example.data.SharedPreferencesFavoriteStorage
import com.example.data.SharedPreferencesServerConfigStorage
import com.example.data.SharedPreferencesUnitPreferencesStorage
import com.example.data.SharedPreferencesWeatherAlertStorage
import com.example.data.SharedPreferencesWindHistoryStorage
import com.example.diagnostics.CrashReporter
import com.example.domain.AirportMapProjection
import com.example.domain.AviationWeatherParser
import com.example.domain.FlightCategory
import com.example.domain.MapRegionPreset
import com.example.domain.WindTrendAnalyzer
import com.example.model.PressureUnit
import com.example.model.RunwayCalculation
import com.example.model.UnitSystem
import com.example.model.WindData
import com.example.model.WindSpeedUnit
import com.example.model.WindTrendDirection
import com.example.notifications.AndroidWeatherNotifier
import com.example.ui.AirportMapScreen
import com.example.ui.MeteoTabloScreen
import com.example.ui.MeteotabloSplashScreen
import com.example.ui.components.RunwayWindRose
import com.example.ui.components.WindRoseCanvas
import com.example.ui.components.WindTrendIndicator
import com.example.ui.theme.MeteoTabloTheme
import com.example.viewmodel.MeteoViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Meteotablo", appName)
  }

  @Test
  fun `shared preferences server config persists default and custom settings`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val storage1 = SharedPreferencesServerConfigStorage(context)
    val initial = storage1.loadConfig()
    assertEquals("http://217.174.229.206:8181/cgi-bin/wlogin", initial.url)
    assertEquals("meteo", initial.username)
    assertEquals("meteo", initial.password)

    storage1.saveConfig(
      ServerConfig(
        url = "217.174.229.206:8181/cgi-bin/wlogin",
        username = "meteo",
        password = "secret",
        useCustomServer = true,
        fallbackToNoaa = false,
        sourceMode = DataSourceMode.IP_ONLY,
        useAviationWeatherGov = true,
        useNoaaTgftp = false,
        useVatsimMetar = true,
      ),
    )

    val storage2 = SharedPreferencesServerConfigStorage(context)
    val reloaded = storage2.loadConfig()
    assertEquals("http://217.174.229.206:8181/cgi-bin/wlogin", reloaded.url)
    assertEquals("secret", reloaded.password)
    assertFalse(reloaded.fallbackToNoaa)
    assertEquals(DataSourceMode.IP_ONLY, reloaded.resolvedMode())
    assertFalse(reloaded.useNoaaTgftp)
    assertTrue(reloaded.useVatsimMetar)
  }

  @Test
  fun `shared preferences favorite storage persists additions and removals`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val storage1 = SharedPreferencesFavoriteStorage(context)
    val initial = storage1.loadFavorites()
    assertTrue(initial.containsAll(listOf("UTAM", "UTAA", "UTAK", "UTAN", "UTAT", "UTAV", "UTAE")))

    val updated = initial - "UTAK"
    storage1.saveFavorites(updated)

    val storage2 = SharedPreferencesFavoriteStorage(context)
    val reloaded = storage2.loadFavorites()
    assertTrue(reloaded.contains("UTAE"))
    assertTrue(reloaded.contains("UTAN"))
    assertFalse(reloaded.contains("UTAK"))
  }

  @Test
  fun `all turkmenistan airports load distinct weather and switch via toolbar selector`() {
    val vm = MeteoViewModel(autoFetch = false)
    val requestedIcaos = listOf("UTAA", "UTAK", "UTAN", "UTAT", "UTAV", "UTAE")

    requestedIcaos.forEach { icao ->
      vm.selectStation(icao)
      val state = vm.uiState.value
      assertEquals(icao, state.station.icao)
      assertTrue(state.weather.rawMetar.contains(icao))
      assertTrue(state.weather.rawTaf.contains(icao))
    }

    composeTestRule.setContent {
      MeteoTabloTheme(isNightMode = false) {
        MeteoTabloScreen(viewModel = vm, onFullscreenChanged = {})
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("toolbar_station_selector").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("toolbar_station_item_UTAN").performClick()
    composeTestRule.waitForIdle()
    assertEquals("UTAN", vm.uiState.value.station.icao)
    assertTrue(vm.uiState.value.weather.rawMetar.contains("UTAN"))
    composeTestRule.onNodeWithTag("wind_center_box_16").assertExists()

    composeTestRule.onNodeWithTag("toolbar_station_selector").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("toolbar_station_item_UTAE").performClick()
    composeTestRule.waitForIdle()
    assertEquals("UTAE", vm.uiState.value.station.icao)
    assertTrue(vm.uiState.value.weather.rawMetar.contains("UTAE"))
  }

  @Test
  fun `favorite airports bar switches adds and removes icao codes in metar table`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val storage = SharedPreferencesFavoriteStorage(context)
    storage.saveFavorites(listOf("UTAM", "UTAA", "UTAK"))
    val vm = MeteoViewModel(favoriteStorage = storage, autoFetch = false)

    composeTestRule.setContent {
      MeteoTabloTheme(isNightMode = false) {
        MeteoTabloScreen(viewModel = vm, onFullscreenChanged = {})
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("metar_table_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("favorite_airports_bar").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("favorite_chip_UTAM").assertIsDisplayed()
    composeTestRule.onNodeWithTag("favorite_chip_UTAA").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    assertEquals("UTAA", vm.uiState.value.station.icao)
    composeTestRule.onNodeWithText("Таблица METAR · UTAA").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("raw_metar_full_text").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("METAR UTAA", substring = true).assertExists()

    composeTestRule.onNodeWithTag("add_favorite_chip").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("add_favorite_icao_input").performTextInput("utav")
    composeTestRule.onNodeWithTag("confirm_add_favorite_button").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("favorite_chip_UTAV").performScrollTo().assertIsDisplayed()
    assertTrue(vm.uiState.value.favoriteIcaos.contains("UTAV"))
    assertEquals("UTAV", vm.uiState.value.station.icao)

    composeTestRule.onNodeWithTag("remove_favorite_UTAV").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertFalse(vm.uiState.value.favoriteIcaos.contains("UTAV"))

    composeTestRule.onNodeWithTag("metar_table_favorite_toggle").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertTrue(vm.uiState.value.favoriteIcaos.contains("UTAV"))
  }

  @Test
  fun `metar table screen displays structured wind visibility and qnh rows`() {
    val vm = MeteoViewModel(autoFetch = false)
    vm.applyCustomMetar(
      rawMetar = "METAR UTAM 141700Z 08004KT 6000 BKN230 11/07 Q1023 R88/CLRD70 NOSIG",
      rawTaf = "TAF UTAM 141630Z 1418/1518 07006MPS 6000 SCT030",
    )
    composeTestRule.setContent {
      MeteoTabloTheme(isNightMode = false) {
        MeteoTabloScreen(viewModel = vm, onFullscreenChanged = {})
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("demo_button").assertDoesNotExist()
    composeTestRule.onNodeWithTag("toolbar_source_selector").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("toolbar_source_selector").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("source_mode_item_INTERNET_ONLY").performClick()
    composeTestRule.waitForIdle()
    assertEquals(DataSourceMode.INTERNET_ONLY, vm.uiState.value.serverConfig.resolvedMode())
    vm.applyCustomMetar(
      rawMetar = "METAR UTAM 141700Z 08004KT 6000 BKN230 11/07 Q1023 R88/CLRD70 NOSIG",
      rawTaf = "TAF UTAM 141630Z 1418/1518 07006MPS 6000 SCT030",
    )
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("wind_center_box_35L").assertExists()
    composeTestRule.onNodeWithTag("taf_footer_row").assertExists()
    composeTestRule.onNodeWithTag("metar_footer_row").assertExists()

    composeTestRule.onNodeWithTag("toolbar_wind_unit_toggle").performScrollTo().assertTextContains("kt")
    composeTestRule.onNodeWithTag("toolbar_pressure_unit_toggle").performScrollTo().assertTextContains("гПа")
    composeTestRule.onNodeWithTag("toolbar_pressure_unit_toggle").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("toolbar_pressure_unit_toggle").performScrollTo().assertTextContains("дм")
    composeTestRule.onNodeWithTag("toolbar_pressure_unit_toggle").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("toolbar_pressure_unit_toggle").performScrollTo().assertTextContains("гПа")

    composeTestRule.onNodeWithTag("metar_table_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("raw_metar_full_text").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("wind_rose_canvas").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("wind_rose_runway_strip").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("ВПП 35L · Встречный").assertExists()
    composeTestRule.onNodeWithText("ВПП 35L · Боковой").assertExists()
    composeTestRule.onNodeWithTag("metar_table_unit_toggle").performScrollTo().assertTextContains("kt")
    composeTestRule.onNodeWithTag("wind_rose_canvas").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("metar_table_unit_toggle").performScrollTo().assertTextContains("м/с")
    composeTestRule.onNodeWithTag("wind_rose_canvas").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("metar_table_unit_toggle").performScrollTo().assertTextContains("км/ч")
    composeTestRule.onNodeWithTag("metar_table_pressure_toggle").performScrollTo().assertTextContains("гПа")
    composeTestRule.onNodeWithTag("metar_table_pressure_toggle").performClick()
    composeTestRule.onNodeWithTag("metar_table_pressure_toggle").assertTextContains("дм")
    composeTestRule.onNodeWithTag("metar_structured_table").assertExists()
    composeTestRule.onNodeWithTag("metar_row_wind").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("metar_row_visibility").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("metar_row_clouds").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("ВНГО 23000 ft").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("BKN 23000 ft").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("metar_row_qnh").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("30,21 дм", substring = true).performScrollTo().assertIsDisplayed()

    composeTestRule.onNodeWithTag("taf_structured_table").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("taf_row_0").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("SCT 3000 ft").performScrollTo().assertIsDisplayed()

    composeTestRule.onNodeWithTag("table_tab_TAF").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("metar_structured_table").assertDoesNotExist()
    composeTestRule.onNodeWithTag("taf_structured_table").assertExists()

    composeTestRule.onNodeWithTag("table_tab_METAR").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("metar_structured_table").assertExists()
    composeTestRule.onNodeWithTag("taf_structured_table").assertDoesNotExist()

    composeTestRule.onNodeWithTag("table_tab_ALL").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("metar_structured_table").assertExists()
    composeTestRule.onNodeWithTag("taf_structured_table").assertExists()
  }

  @Test
  fun `branded splash screen displays Meteotablo name and logo then transitions to main board`() {
    val vm = MeteoViewModel(autoFetch = false)
    val showSplashState = androidx.compose.runtime.mutableStateOf(true)
    composeTestRule.mainClock.autoAdvance = false

    composeTestRule.setContent {
      MeteoTabloTheme(isNightMode = false) {
        MeteotabloAppHost(
          viewModel = vm,
          showSplash = showSplashState.value,
          onSplashFinished = { showSplashState.value = false },
          splashDurationMillis = 800L,
        )
      }
    }

    composeTestRule.mainClock.advanceTimeBy(400L)
    composeTestRule.onNodeWithTag("splash_screen").assertIsDisplayed()
    composeTestRule.onNodeWithTag("splash_logo").assertIsDisplayed()
    composeTestRule.onNodeWithTag("splash_app_name").assertIsDisplayed()
    composeTestRule.onNodeWithTag("splash_app_name").assertTextContains("Meteotablo")
    composeTestRule.onNodeWithTag("splash_author_credit").assertIsDisplayed()
    composeTestRule.onNodeWithTag("splash_author_credit").assertTextContains("Allanur Saparow", substring = true)

    composeTestRule.mainClock.advanceTimeBy(1200L)
    composeTestRule.mainClock.autoAdvance = true
    composeTestRule.waitForIdle()

    assertFalse(showSplashState.value)
    composeTestRule.onNodeWithTag("splash_screen").assertDoesNotExist()
    composeTestRule.onNodeWithTag("toolbar_brand_title").assertTextContains("Meteotablo")
    composeTestRule.onNodeWithTag("toolbar_station_selector").assertIsDisplayed()
    composeTestRule.onNodeWithTag("board_owner_footer").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("board_owner_footer").assertTextContains("Allanur Saparow", substring = true)

    composeTestRule.onNodeWithTag("board_owner_footer").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("about_author_card").assertIsDisplayed()
    composeTestRule.onNodeWithText("Автор: Allanur Saparow", substring = true).assertIsDisplayed()
  }

  @Test
  fun `saved weather setups persist runway configurations and recall them from toolbar`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val presetStorage = com.example.data.SharedPreferencesWeatherPresetStorage(context)
    presetStorage.savePresets(emptyList())
    val vm = MeteoViewModel(presetStorage = presetStorage, autoFetch = false)

    vm.selectStation("UTAA")
    vm.selectRunwayPair(1)
    vm.setWindUnit(com.example.model.WindSpeedUnit.MPS)
    vm.setPressureUnit(com.example.model.PressureUnit.INHG)

    composeTestRule.setContent {
      MeteoTabloTheme(isNightMode = false) {
        MeteoTabloScreen(viewModel = vm, onFullscreenChanged = {})
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("toolbar_presets_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("preset_menu_save_current").performClick()
    composeTestRule.waitForIdle()

    val savedId = com.example.model.SavedWeatherPreset.buildId(
      icao = "UTAA",
      runwayPairIndex = 1,
      windUnit = com.example.model.WindSpeedUnit.MPS,
      pressureUnit = com.example.model.PressureUnit.INHG,
    )
    assertEquals(1, presetStorage.loadPresets().size)
    assertEquals(1, presetStorage.loadPresets().first().runwayPairIndex)

    // Switch away to a different station and units
    vm.selectStation("UTAN")
    vm.setWindUnit(com.example.model.WindSpeedUnit.KT)
    vm.setPressureUnit(com.example.model.PressureUnit.HPA)
    composeTestRule.waitForIdle()
    assertEquals("UTAN", vm.uiState.value.station.icao)

    // Recall saved UTAA 12R/30L setup via toolbar presets menu
    composeTestRule.onNodeWithTag("toolbar_presets_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("preset_menu_item_$savedId").performClick()
    composeTestRule.waitForIdle()

    assertEquals("UTAA", vm.uiState.value.station.icao)
    assertEquals(1, vm.uiState.value.selectedRunwayPairIndex)
    assertEquals("12R", vm.uiState.value.activeRunwayPair.primaryDesignator)
    assertEquals(com.example.model.WindSpeedUnit.MPS, vm.uiState.value.windUnit)
    assertEquals(com.example.model.PressureUnit.INHG, vm.uiState.value.pressureUnit)
    composeTestRule.onNodeWithTag("wind_center_box_12R").assertExists()

    // Delete via dialog management
    composeTestRule.onNodeWithTag("toolbar_presets_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("preset_menu_manage").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("recall_preset_$savedId").performScrollTo().assertExists()
    composeTestRule.onNodeWithTag("delete_preset_$savedId").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertTrue(presetStorage.loadPresets().isEmpty())
  }

  @Test
  fun `favorite airport weather deterioration triggers local notification banner and dialog history`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    Shadows.shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

    val notificationManager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.cancelAll()

    val alertStorage = SharedPreferencesWeatherAlertStorage(app)
    alertStorage.saveSnapshots(emptyMap())
    alertStorage.saveAlerts(emptyList())
    alertStorage.saveConfig(com.example.model.WeatherAlertConfig(enabled = true))

    val notifier = AndroidWeatherNotifier(app)
    val vm = MeteoViewModel(
      favoriteStorage = SharedPreferencesFavoriteStorage(app),
      alertStorage = alertStorage,
      weatherNotifier = notifier,
      autoFetch = false,
    )

    composeTestRule.setContent {
      MeteoTabloTheme(isNightMode = false) {
        MeteoTabloScreen(viewModel = vm, onFullscreenChanged = {})
      }
    }

    // 1. Baseline calm weather for favorite UTAA establishes snapshot without false alarm
    vm.selectStation("UTAA")
    vm.applyCustomMetar(
      rawMetar = "METAR UTAA 151000Z 09006KT CAVOK 24/08 Q1016 NOSIG",
      rawTaf = "TAF UTAA 150900Z 1512/1612 09006MPS 9999 FEW040",
    )
    composeTestRule.waitForIdle()
    assertTrue(vm.uiState.value.weatherAlerts.isEmpty())
    assertEquals(0, Shadows.shadowOf(notificationManager).allNotifications.size)

    // 2. Sudden thunderstorm + strong gusts + low visibility at favorite UTAA triggers notification
    vm.applyCustomMetar(
      rawMetar = "METAR UTAA 151030Z 31024G35KT 1200 +TSRA BKN005CB 19/17 Q1009",
      rawTaf = "TAF UTAA 150900Z 1512/1612 31015G20MPS 2000 +TSRA BKN006CB",
    )
    composeTestRule.waitForIdle()

    assertEquals(1, vm.uiState.value.weatherAlerts.size)
    val firedAlert = vm.uiState.value.weatherAlerts.first()
    assertEquals("UTAA", firedAlert.icao)
    assertEquals(com.example.model.WeatherAlertSeverity.CRITICAL, firedAlert.severity)
    assertEquals(1, vm.uiState.value.unreadAlertCount)

    // Verify real Android NotificationManager posted local notification
    val posted = Shadows.shadowOf(notificationManager).allNotifications
    assertEquals(1, posted.size)
    val title = posted.first().extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString().orEmpty()
    assertTrue(title.contains("UTAA"))

    // Verify banner & toolbar unread alert counter
    composeTestRule.onNodeWithTag("weather_alert_banner").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("toolbar_alerts_button").performScrollTo().assertTextContains("Тревога (1)")

    // Open alerts dialog, verify alert item rendered, then mark all read
    composeTestRule.onNodeWithTag("toolbar_alerts_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("weather_alerts_dialog").assertIsDisplayed()
    composeTestRule.onNodeWithTag("alert_item_${firedAlert.id}").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("mark_alerts_read_button").performClick()
    composeTestRule.waitForIdle()

    assertEquals(0, vm.uiState.value.unreadAlertCount)
    assertEquals(1, alertStorage.loadAlerts().size)
    assertTrue(alertStorage.loadAlerts().first().isRead)
  }

  @Test
  fun `main activity launches cleanly reaches resumed state and transitions from splash`() {
    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      assertEquals(Lifecycle.State.RESUMED, scenario.state)
      composeTestRule.waitForIdle()
      val hasSplash = composeTestRule.onAllNodesWithTag("splash_screen").fetchSemanticsNodes().isNotEmpty()
      if (hasSplash) {
        composeTestRule.onNodeWithTag("splash_continue_button").performClick()
        composeTestRule.waitForIdle()
      }
      composeTestRule.onNodeWithTag("toolbar_brand_title").assertExists()
      composeTestRule.onNodeWithTag("wind_trend_indicator").assertExists()
    }
  }

  @Test
  fun `zero size canvas components render safely without IllegalArgumentException`() {
    val zeroWind = WindData(
      directionDeg = 90,
      isVariable = false,
      isCalm = false,
      speedKt = 12,
      gustKt = 20,
      speedMps = 6.2,
    )
    val zeroCalc = RunwayCalculation(
      designator = "35L",
      headingDeg = 350,
      crosswindMps = 5.0,
      headwindMps = 2.0,
      crosswindKt = 10.0,
      headwindKt = 4.0,
      frictionCoeff = 0.65,
      rvrMeters = 1500,
    )
    val sampleTrend = WindTrendAnalyzer.analyze("UTAM", emptyList())

    composeTestRule.setContent {
      MeteoTabloTheme(isNightMode = false) {
        Box(modifier = Modifier.size(0.dp)) {
          MeteotabloSplashScreen(onFinished = {}, durationMillis = 5000L)
          WindTrendIndicator(trend = sampleTrend, windUnit = WindSpeedUnit.KT)
          WindRoseCanvas(
            wind = zeroWind,
            windUnit = WindSpeedUnit.KT,
            onToggleUnit = {},
            activeRunway = zeroCalc,
          )
          RunwayWindRose(
            calculation = zeroCalc,
            wind = zeroWind,
            windUnit = WindSpeedUnit.KT,
            onToggleUnit = {},
          )
          AirportMapScreen(
            stations = AirportCatalog.presets,
            weatherByIcao = emptyMap(),
            activeStationIcao = "UTAM",
            favoriteIcaos = emptyList(),
            windUnit = WindSpeedUnit.KT,
            pressureUnit = PressureUnit.HPA,
            isFullscreen = false,
            isRefreshingMap = false,
            loadingMapIcao = null,
            isNightMode = false,
            onBack = {},
            onSelectStationOnBoard = {},
            onRefreshAll = {},
            onRefreshStation = {},
            onToggleFavorite = {},
          )
        }
      }
    }
    composeTestRule.waitForIdle()
  }

  @Test
  fun `wind speed trend indicator caches historical METARs per airport and shows direction`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val historyStorage = SharedPreferencesWindHistoryStorage(context)
    historyStorage.saveAll(emptyMap())

    val vm = MeteoViewModel(windHistoryStorage = historyStorage, autoFetch = false)
    vm.selectStation("UTAA")

    composeTestRule.setContent {
      MeteoTabloTheme(isNightMode = false) {
        MeteoTabloScreen(viewModel = vm, onFullscreenChanged = {})
      }
    }

    // 1st observation -> INSUFFICIENT
    vm.applyCustomMetar(
      rawMetar = "METAR UTAA 150900Z 09005KT CAVOK 22/08 Q1016 NOSIG",
      rawTaf = "TAF UTAA",
    )
    composeTestRule.waitForIdle()
    assertEquals(WindTrendDirection.INSUFFICIENT, vm.uiState.value.windTrend.direction)

    // 2nd observation: wind jumps 5 -> 14 kt (+9 kt) -> INCREASING
    vm.applyCustomMetar(
      rawMetar = "METAR UTAA 150930Z 09014G22KT CAVOK 23/08 Q1015 NOSIG",
      rawTaf = "TAF UTAA",
    )
    composeTestRule.waitForIdle()
    assertEquals(WindTrendDirection.INCREASING, vm.uiState.value.windTrend.direction)
    assertEquals(9, vm.uiState.value.windTrend.deltaKt)
    composeTestRule.onNodeWithTag("wind_trend_indicator").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("wind_trend_badge").assertTextContains("Усиление", substring = true)
    composeTestRule.onNodeWithTag("wind_trend_sparkline").assertExists()

    // 3rd observation: wind drops 14 -> 7 kt (-7 kt) -> DECREASING
    vm.applyCustomMetar(
      rawMetar = "METAR UTAA 151000Z 09007KT CAVOK 24/08 Q1015 NOSIG",
      rawTaf = "TAF UTAA",
    )
    composeTestRule.waitForIdle()
    assertEquals(WindTrendDirection.DECREASING, vm.uiState.value.windTrend.direction)
    assertEquals(-7, vm.uiState.value.windTrend.deltaKt)
    composeTestRule.onNodeWithTag("wind_trend_badge").assertTextContains("Ослабление", substring = true)

    // 4th & 5th observations steady (~7 kt -> 7 kt) -> STEADY
    vm.applyCustomMetar(
      rawMetar = "METAR UTAA 151030Z 09007KT CAVOK 24/08 Q1015 NOSIG",
      rawTaf = "TAF UTAA",
    )
    vm.applyCustomMetar(
      rawMetar = "METAR UTAA 151100Z 09008KT CAVOK 24/08 Q1015 NOSIG",
      rawTaf = "TAF UTAA",
    )
    composeTestRule.waitForIdle()
    assertEquals(WindTrendDirection.STEADY, vm.uiState.value.windTrend.direction)
    composeTestRule.onNodeWithTag("wind_trend_badge").assertTextContains("Стабильно", substring = true)

    // Per-airport isolation: switching to UTAM has empty history until UTAM is recorded
    vm.selectStation("UTAM")
    composeTestRule.waitForIdle()
    assertEquals("UTAM", vm.uiState.value.windTrend.icao)
    assertTrue(vm.uiState.value.windTrend.entries.isEmpty())

    // Persisted reload for UTAA restores cached entries and STEADY trend
    val vmReloaded = MeteoViewModel(
      windHistoryStorage = SharedPreferencesWindHistoryStorage(context),
      autoFetch = false,
    )
    vmReloaded.selectStation("UTAA")
    assertEquals(5, vmReloaded.uiState.value.windTrend.entries.size)
    assertEquals(WindTrendDirection.STEADY, vmReloaded.uiState.value.windTrend.direction)
  }

  @Test
  fun `crashlytics reporter initializes in application tracks launch stages and filters cancellation`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    assertTrue(app is MeteotabloApplication)

    CrashReporter.initialize(app)
    CrashReporter.setLaunchStage("test_startup_stage")
    CrashReporter.setStationContext(
      icao = "UTAA",
      runwayLabel = "12L/30R",
      sourceMode = "HYBRID",
      favoriteCount = 3,
    )
    val beforeCount = CrashReporter.getDiagnosticsSnapshot(app).breadcrumbCount

    CrashReporter.recordNonFatal(
      tag = "test_cancelled",
      throwable = kotlinx.coroutines.CancellationException("station switched"),
    )
    assertEquals(beforeCount, CrashReporter.getDiagnosticsSnapshot(app).breadcrumbCount)

    CrashReporter.recordNonFatal(
      tag = "test_parse_issue",
      throwable = IllegalStateException("Malformed METAR wind token"),
      extras = mapOf("icao" to "UTAA"),
    )
    val snapshot = CrashReporter.getDiagnosticsSnapshot(app)
    assertEquals("test_startup_stage", snapshot.currentLaunchStage)
    assertTrue(snapshot.breadcrumbCount > beforeCount)
    assertTrue(snapshot.isCrashlyticsActive)
  }

  @Test
  fun `settings screen toggles metric and imperial units updates weather display and persists preferences`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val unitStorage = SharedPreferencesUnitPreferencesStorage(context)
    unitStorage.savePreferences(com.example.data.UnitPreferences(WindSpeedUnit.KT, PressureUnit.HPA))

    val vm = MeteoViewModel(unitPreferencesStorage = unitStorage, autoFetch = false)
    vm.applyCustomMetar(
      rawMetar = "METAR UTAA 151200Z 09016KT CAVOK 25/09 Q1023 NOSIG",
      rawTaf = "TAF UTAA 151100Z 1512/1612 09008MPS 9999 FEW040",
    )

    composeTestRule.setContent {
      MeteoTabloTheme(isNightMode = false) {
        MeteoTabloScreen(viewModel = vm, onFullscreenChanged = {})
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("toolbar_settings_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    // Select Metric (hPa, m/s)
    composeTestRule.onNodeWithTag("settings_unit_system_METRIC").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertEquals(WindSpeedUnit.MPS, vm.uiState.value.windUnit)
    assertEquals(PressureUnit.HPA, vm.uiState.value.pressureUnit)
    composeTestRule.onNodeWithTag("settings_active_system_badge").assertTextContains("METRIC", substring = true)
    composeTestRule.onNodeWithTag("settings_preview_wind").performScrollTo().assertTextContains("8 м/с", substring = true)
    composeTestRule.onNodeWithTag("settings_preview_pressure").performScrollTo().assertTextContains("1023 гПа", substring = true)

    // Switch to Imperial (inHg, knots) via switch
    composeTestRule.onNodeWithTag("settings_metric_imperial_switch").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertEquals(WindSpeedUnit.KT, vm.uiState.value.windUnit)
    assertEquals(PressureUnit.INHG, vm.uiState.value.pressureUnit)
    composeTestRule.onNodeWithTag("settings_active_system_badge").assertTextContains("IMPERIAL", substring = true)
    composeTestRule.onNodeWithTag("settings_preview_wind").performScrollTo().assertTextContains("16 kt", substring = true)
    composeTestRule.onNodeWithTag("settings_preview_pressure").performScrollTo().assertTextContains("30,21 дм", substring = true)

    // Return to main weather display and confirm toolbar reflects Imperial units
    composeTestRule.onNodeWithTag("settings_back_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("toolbar_wind_unit_toggle").performScrollTo().assertTextContains("kt")
    composeTestRule.onNodeWithTag("toolbar_pressure_unit_toggle").performScrollTo().assertTextContains("дм")

    // Verify persistence across ViewModel recreation
    val vmReloaded = MeteoViewModel(unitPreferencesStorage = SharedPreferencesUnitPreferencesStorage(context), autoFetch = false)
    assertEquals(WindSpeedUnit.KT, vmReloaded.uiState.value.windUnit)
    assertEquals(PressureUnit.INHG, vmReloaded.uiState.value.pressureUnit)
  }

  @Test
  fun `airport catalog search matches ICAO prefixes city names and favorite ICAOs`() {
    val utPrefix = AirportCatalog.search("ut")
    assertTrue(utPrefix.isNotEmpty())
    assertTrue(utPrefix.all { it.icao.startsWith("UT") })

    val byCity = AirportCatalog.search("Дубай")
    assertEquals(1, byCity.size)
    assertEquals("OMDB", byCity.first().icao)

    val withFavorite = AirportCatalog.search("KJ", extraIcaos = listOf("KJFK"))
    assertTrue(withFavorite.any { it.icao == "KJFK" })
  }

  @Test
  fun `top airport search bar looks up catalog and custom ICAO codes and triggers fetch`() {
    val vm = MeteoViewModel(autoFetch = false)
    assertEquals("UTAM", vm.uiState.value.station.icao)

    composeTestRule.setContent {
      MeteoTabloTheme(isNightMode = false) {
        MeteoTabloScreen(viewModel = vm, onFullscreenChanged = {})
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("airport_icao_search_bar").assertIsDisplayed()

    // Type known preset ICAO in lowercase -> suggestion appears, click suggestion to switch
    composeTestRule.onNodeWithTag("airport_icao_search_input").performTextInput("utaa")
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("airport_icao_suggestions_row").assertExists()
    composeTestRule.onNodeWithTag("airport_search_suggestion_UTAA").performClick()
    composeTestRule.waitForIdle()

    assertEquals("UTAA", vm.uiState.value.station.icao)
    assertTrue(vm.uiState.value.weather.rawMetar.contains("UTAA"))
    composeTestRule.onNodeWithTag("toolbar_station_selector").assertTextContains("UTAA", substring = true)

    // Type arbitrary global 4-letter ICAO, verify clear button resets query, then re-enter & submit
    composeTestRule.onNodeWithTag("airport_icao_search_input").performTextInput("egll")
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("airport_icao_search_clear").performClick()
    composeTestRule.waitForIdle()
    assertEquals("UTAA", vm.uiState.value.station.icao)

    composeTestRule.onNodeWithTag("airport_icao_search_input").performTextInput("kjfk")
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("airport_search_suggestion_KJFK").assertExists()
    composeTestRule.onNodeWithTag("airport_icao_search_submit").performClick()
    composeTestRule.waitForIdle()

    assertEquals("KJFK", vm.uiState.value.station.icao)
    assertTrue(vm.uiState.value.weather.rawMetar.contains("KJFK"))
    composeTestRule.onNodeWithTag("toolbar_station_selector").assertTextContains("KJFK", substring = true)
  }

  @Test
  fun `airport map projection evaluates coordinates and flight categories accurately`() {
    val utaa = AirportCatalog.findOrDefault("UTAA")
    val uudd = AirportCatalog.findOrDefault("UUDD")
    val utak = AirportCatalog.findOrDefault("UTAK")
    val utam = AirportCatalog.findOrDefault("UTAM")

    val bounds = AirportMapProjection.computeBounds(MapRegionPreset.REGIONAL, AirportCatalog.presets)
    val pUtaa = AirportMapProjection.project(utaa.latitude, utaa.longitude, bounds, 800f, 600f)
    val pUudd = AirportMapProjection.project(uudd.latitude, uudd.longitude, bounds, 800f, 600f)
    val pUtak = AirportMapProjection.project(utak.latitude, utak.longitude, bounds, 800f, 600f)
    val pUtam = AirportMapProjection.project(utam.latitude, utam.longitude, bounds, 800f, 600f)

    // Moscow (UUDD) is north of Ashgabat (UTAA) -> smaller y coordinate
    assertTrue(pUudd.y < pUtaa.y)
    // Turkmenbashi (UTAK) is west of Mary (UTAM) -> smaller x coordinate
    assertTrue(pUtak.x < pUtam.x)

    val zeroPt = AirportMapProjection.project(utaa.latitude, utaa.longitude, bounds, 0f, 0f)
    assertEquals(0f, zeroPt.x, 0.001f)
    assertEquals(0f, zeroPt.y, 0.001f)

    val cavokWx = AviationWeatherParser.parse(
      rawMetarInput = "METAR UTAA 151200Z 09010KT CAVOK 24/08 Q1018 NOSIG",
      rawTafInput = "TAF UTAA",
      station = utaa,
      sourceLabel = "test",
    )
    assertEquals(FlightCategory.VFR, AirportMapProjection.evaluateFlightCategory(cavokWx))

    val mvfrWx = AviationWeatherParser.parse(
      rawMetarInput = "METAR UTAA 151200Z 12018G26KT 3500 BR SCT012 18/14 Q1012",
      rawTafInput = "TAF UTAA",
      station = utaa,
      sourceLabel = "test",
    )
    assertEquals(FlightCategory.MVFR, AirportMapProjection.evaluateFlightCategory(mvfrWx))

    val ifrWx = AviationWeatherParser.parse(
      rawMetarInput = "METAR UTAA 151200Z 31024G35KT 0800 +TSRA BKN004CB 19/17 Q1007",
      rawTafInput = "TAF UTAA",
      station = utaa,
      sourceLabel = "test",
    )
    assertEquals(FlightCategory.IFR, AirportMapProjection.evaluateFlightCategory(ifrWx))
    assertEquals(FlightCategory.UNKNOWN, AirportMapProjection.evaluateFlightCategory(null))
  }

  @Test
  fun `map view pins airports displays brief weather summary on pin tap and opens selected airport`() {
    val vm = MeteoViewModel(autoFetch = false)

    // Seed weather for UTAA and UTAM into mapWeatherByIcao via applyCustomMetar
    vm.selectStation("UTAA")
    vm.applyCustomMetar(
      rawMetar = "METAR UTAA 151200Z 09014KT CAVOK 26/07 Q1019 NOSIG",
      rawTaf = "TAF UTAA",
    )
    vm.selectStation("UTAM")
    vm.applyCustomMetar(
      rawMetar = "METAR UTAM 151200Z 31022G34KT 0900 +TSRA BKN004CB 17/15 Q1006",
      rawTaf = "TAF UTAM",
    )

    composeTestRule.setContent {
      MeteoTabloTheme(isNightMode = false) {
        MeteoTabloScreen(viewModel = vm, onFullscreenChanged = {})
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("toolbar_map_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("airport_map_screen").assertIsDisplayed()
    composeTestRule.onNodeWithTag("map_canvas_container").assertIsDisplayed()

    // Initially active station UTAM is selected -> shows IFR & wind summary
    composeTestRule.onNodeWithTag("map_summary_icao").performScrollTo().assertTextContains("UTAM")
    composeTestRule.onNodeWithTag("map_summary_flight_category").performScrollTo().assertTextContains("IFR")
    composeTestRule.onNodeWithTag("map_summary_pressure").performScrollTo().assertTextContains("1006 гПа")

    // Tap UTAA pin -> summary updates to UTAA weather brief
    composeTestRule.onNodeWithTag("map_pin_UTAA").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("map_summary_icao").performScrollTo().assertTextContains("UTAA")
    composeTestRule.onNodeWithTag("map_summary_flight_category").performScrollTo().assertTextContains("VFR")
    composeTestRule.onNodeWithTag("map_summary_wind").performScrollTo().assertTextContains("090° 14 kt")
    composeTestRule.onNodeWithTag("map_summary_visibility").performScrollTo().assertTextContains("CAVOK", substring = true)
    composeTestRule.onNodeWithTag("map_summary_temperature").performScrollTo().assertTextContains("26°C / 7°C")
    composeTestRule.onNodeWithTag("map_summary_pressure").performScrollTo().assertTextContains("1019 гПа")

    // Toggle favorite directly from map summary card (both remove & re-add)
    val wasFav = "UTAA" in vm.uiState.value.favoriteIcaos
    composeTestRule.onNodeWithTag("map_summary_favorite_toggle").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertEquals(!wasFav, "UTAA" in vm.uiState.value.favoriteIcaos)
    composeTestRule.onNodeWithTag("map_summary_favorite_toggle").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertEquals(wasFav, "UTAA" in vm.uiState.value.favoriteIcaos)

    // Switch region to Regional and verify regional hubs (OMDB, LTFM) are pinned
    composeTestRule.onNodeWithTag("map_region_REGIONAL").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("map_pin_OMDB").assertExists()
    composeTestRule.onNodeWithTag("map_pin_LTFM").assertExists()

    // Zoom controls respond cleanly
    composeTestRule.onNodeWithTag("map_zoom_in_button").performClick()
    composeTestRule.onNodeWithTag("map_reset_view_button").performClick()
    composeTestRule.waitForIdle()

    // Select UTAA again and tap Open on Board -> navigates back with UTAA active
    composeTestRule.onNodeWithTag("map_station_chip_UTAA").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("map_open_on_board_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    assertEquals("UTAA", vm.uiState.value.station.icao)
    composeTestRule.onNodeWithTag("toolbar_station_selector").assertTextContains("UTAA", substring = true)
  }

  @Test
  fun `slippy map projection generates mercator tiles and valid tile urls for all layers`() {
    val cam = AirportMapProjection.cameraForRegion(
      preset = MapRegionPreset.TURKMENISTAN,
      stations = AirportCatalog.presets,
      widthPx = 1080f,
      heightPx = 720f,
    )
    val tiles = AirportMapProjection.visibleTiles(cam, 1080f, 720f)
    assertTrue(tiles.isNotEmpty())
    assertTrue(tiles.all { it.z in 1..16 && it.y >= 0 && it.tileSizePx > 0f })

    val osmUrl = com.example.domain.MapLayerStyle.OSM.tileUrl(5, 20, 12)
    val cartoUrl = com.example.domain.MapLayerStyle.CARTO_VOYAGER.tileUrl(5, 20, 12)
    val satUrl = com.example.domain.MapLayerStyle.ESRI_SATELLITE.tileUrl(5, 20, 12)
    assertEquals("https://tile.openstreetmap.org/5/20/12.png", osmUrl)
    assertEquals("https://basemaps.cartocdn.com/rastertiles/voyager/5/20/12.png", cartoUrl)
    assertTrue(satUrl.endsWith("/5/12/20"))
  }

  @Test
  fun `server config preserves php endpoints and map layer switcher toggles satellite view`() {
    assertEquals(
      "http://217.174.229.206:8181/tablo.php",
      ServerConfig.normalizeServerUrl("217.174.229.206:8181/tablo.php"),
    )
    assertEquals(
      "http://217.174.229.206:8181/",
      ServerConfig.retrofitBaseUrl("217.174.229.206:8181/tablo.php"),
    )

    val vm = MeteoViewModel(autoFetch = false)
    composeTestRule.setContent {
      MeteoTabloTheme(isNightMode = false) {
        MeteoTabloScreen(viewModel = vm, onFullscreenChanged = {})
      }
    }
    composeTestRule.waitForIdle()

    // Open Server dialog and verify the diagnostic "Проверить IP-сервер" button exists
    composeTestRule.onNodeWithTag("toolbar_settings_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("settings_open_server_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("server_test_connection_button").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Закрыть").performClick()
    composeTestRule.onNodeWithTag("settings_back_button").performClick()
    composeTestRule.waitForIdle()

    // Open Map screen and switch raster tile styles (OSM -> Satellite -> Carto Dark)
    composeTestRule.onNodeWithTag("toolbar_map_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("map_layer_ESRI_SATELLITE").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("map_layer_CARTO_DARK").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("map_layer_OSM").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("map_canvas_container").assertIsDisplayed()
  }
}
