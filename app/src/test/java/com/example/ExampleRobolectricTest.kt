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
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.example.data.AirportCatalog
import com.example.data.DataSourceMode
import com.example.data.ServerConfig
import com.example.data.SharedPreferencesFavoriteStorage
import com.example.data.SharedPreferencesRecentSearchesStorage
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
    assertEquals("http://217.174.229.206:8181/cgi-bin/wdr", initial.url)
    assertEquals("meteo", initial.username)
    assertEquals("meteo", initial.password)

    storage1.saveConfig(
      ServerConfig(
        url = "217.174.229.206:8181/cgi-bin/wdr",
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
    assertEquals("http://217.174.229.206:8181/cgi-bin/wdr", reloaded.url)
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
    composeTestRule.onNodeWithTag("toolbar_decoder_button").performScrollTo().assertIsDisplayed()
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

    composeTestRule.onNodeWithTag("toolbar_wind_unit_toggle").performScrollTo().assertTextContains("kt", substring = true)
    composeTestRule.onNodeWithTag("toolbar_pressure_unit_toggle").performScrollTo().assertTextContains("гПа", substring = true)
    composeTestRule.onNodeWithTag("toolbar_pressure_unit_toggle").performClick()
    composeTestRule.onNodeWithTag("toolbar_pressure_unit_toggle").assertTextContains("дм", substring = true)
    composeTestRule.onNodeWithTag("toolbar_pressure_unit_toggle").performClick()
    composeTestRule.onNodeWithTag("toolbar_pressure_unit_toggle").assertTextContains("гПа", substring = true)
    composeTestRule.waitForIdle()

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
    composeTestRule.onNodeWithTag("splash_author_credit").assertDoesNotExist()

    composeTestRule.mainClock.advanceTimeBy(1200L)
    composeTestRule.mainClock.autoAdvance = true
    composeTestRule.waitForIdle()

    assertFalse(showSplashState.value)
    composeTestRule.onNodeWithTag("splash_screen").assertDoesNotExist()
    composeTestRule.onNodeWithTag("toolbar_brand_title").assertTextContains("Meteotablo")
    composeTestRule.onNodeWithTag("toolbar_station_selector").assertIsDisplayed()
    composeTestRule.onNodeWithText("Время UTC").performScrollTo().assertIsDisplayed()
    assertTrue(vm.uiState.value.liveLocalClock.endsWith("Z"))
    composeTestRule.onNodeWithTag("board_owner_footer").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("board_owner_footer").assertTextContains("О программе", substring = true)
    assertTrue(composeTestRule.onAllNodesWithText("Allanur", substring = true).fetchSemanticsNodes().isEmpty())
    assertTrue(composeTestRule.onAllNodesWithText("Автор", substring = true).fetchSemanticsNodes().isEmpty())

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

    // Verify banner & top toolbar unread alert counter
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

    // Return to main weather display and confirm direct toolbar unit chips reflect Imperial units
    composeTestRule.onNodeWithTag("settings_back_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("toolbar_wind_unit_toggle").performScrollTo().assertTextContains("kt", substring = true)
    composeTestRule.onNodeWithTag("toolbar_pressure_unit_toggle").performScrollTo().assertTextContains("дм", substring = true)

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

    // Open Server dialog and verify auto-login panel and test button exist while IP/login/pass inputs are hidden
    composeTestRule.onNodeWithTag("toolbar_settings_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("settings_open_server_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("server_auto_auth_card").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("server_test_connection_button").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("server_url_input").assertDoesNotExist()
    composeTestRule.onNodeWithTag("server_user_input").assertDoesNotExist()
    composeTestRule.onNodeWithTag("server_pass_input").assertDoesNotExist()
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

  @Test
  fun `weather history analyzer builds 24h qnh and temperature trends with pruning and month rollover`() {
    val utam = AirportCatalog.findOrDefault("UTAM")
    val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
      set(2026, java.util.Calendar.MAY, 1, 2, 0, 0)
      set(java.util.Calendar.MILLISECOND, 0)
    }
    val refNowMillis = cal.timeInMillis

    // 301800Z refers to April 30 18:00Z relative to May 1 02:00Z (month boundary rollover)
    val prevMonthObs = com.example.domain.WeatherHistoryAnalyzer.resolveObservationMillis(
      rawMetar = "METAR UTAM 301800Z 09008KT 8000 SCT030 14/06 Q1016",
      referenceNowMillis = refNowMillis,
    )
    assertTrue(prevMonthObs < refNowMillis)
    assertTrue(refNowMillis - prevMonthObs in (7L * 3600_000L)..(9L * 3600_000L))

    val staleObs = refNowMillis - (25L * 3600_000L)
    val eOld = com.example.domain.WeatherHistoryAnalyzer.parseHistoricalMetar(
      rawMetar = "METAR UTAM 292200Z 09005KT 9999 FEW040 10/04 Q1010",
      rawTaf = null,
      station = utam,
      sourceLabel = "stale",
      explicitTimestampMillis = staleObs,
      referenceNowMillis = refNowMillis,
    )!!
    val e1 = com.example.domain.WeatherHistoryAnalyzer.parseHistoricalMetar(
      rawMetar = "METAR UTAM 301800Z 11012KT 9999 SCT035 16/07 Q1014",
      rawTaf = "TAF UTAM 301700Z 3018/0118 11012KT 9999 SCT035",
      station = utam,
      sourceLabel = "test",
      explicitTimestampMillis = prevMonthObs,
      referenceNowMillis = refNowMillis,
    )!!
    val e2 = com.example.domain.WeatherHistoryAnalyzer.parseHistoricalMetar(
      rawMetar = "METAR UTAM 010100Z 14018G26KT 2500 BR BKN012 11/09 Q1019",
      rawTaf = "TAF UTAM 302300Z 0100/0200 14015KT 3000 BR BKN012",
      station = utam,
      sourceLabel = "test",
      explicitTimestampMillis = refNowMillis - 3600_000L,
      referenceNowMillis = refNowMillis,
    )!!

    val merged = com.example.domain.WeatherHistoryAnalyzer.mergeHistory(
      existing = listOf(eOld, e1),
      incoming = listOf(e2),
      referenceNowMillis = refNowMillis,
    )
    assertEquals(2, merged.size) // stale 25h entry pruned
    val trend = com.example.domain.WeatherHistoryAnalyzer.buildQnhTrend(
      icao = "UTAM",
      entries = merged,
      referenceNowMillis = refNowMillis,
    )
    assertEquals(2, trend.points.size)
    assertEquals(1014, trend.minHpa)
    assertEquals(1019, trend.maxHpa)
    assertEquals(5, trend.delta24hHpa)
    assertEquals(2, trend.temperaturePoints.size)
    assertEquals(11, trend.minTempC)
    assertEquals(16, trend.maxTempC)
    assertEquals(-5, trend.delta24hTempC)
  }

  @Test
  fun `threshold config screen updates wind and visibility limits and triggers visual alerts in metar taf table`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val alertStorage = SharedPreferencesWeatherAlertStorage(context)
    alertStorage.saveConfig(com.example.model.WeatherAlertConfig())
    val vm = MeteoViewModel(alertStorage = alertStorage, autoFetch = false)
    vm.applyCustomMetar(
      rawMetar = "METAR UTAM 241000Z 28016G24KT 2200 BR BKN010 08/06 Q1012 NOSIG",
      rawTaf = "TAF UTAM 240900Z 2412/2512 27018G26KT 1800 BR BKN012",
    )

    composeTestRule.setContent {
      MeteoTabloTheme(isNightMode = false) {
        MeteoTabloScreen(viewModel = vm, onFullscreenChanged = {})
      }
    }
    composeTestRule.waitForIdle()

    // Open METAR Table -> open Thresholds configuration screen
    composeTestRule.onNodeWithTag("metar_table_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("metar_table_thresholds_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("threshold_config_screen").assertIsDisplayed()
    // Select 15 kt wind threshold chip and 2500 m visibility chip
    composeTestRule.onNodeWithTag("threshold_wind_chip_15").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("threshold_vis_chip_3000").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    val persisted = SharedPreferencesWeatherAlertStorage(context).loadConfig()
    assertEquals(15, persisted.tableWindAlertKt)
    assertEquals(3000, persisted.tableVisibilityAlertMeters)
    assertTrue(persisted.tableAlertsEnabled)

    composeTestRule.onNodeWithTag("thresholds_done_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    // Verify visual alert banner and METAR + TAF row alert badges appear
    composeTestRule.onNodeWithTag("table_active_alerts_banner").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("metar_wind_alert_badge").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("metar_vis_alert_badge").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("taf_wind_alert_badge_0").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("taf_vis_alert_badge_0").performScrollTo().assertIsDisplayed()

    // Verify Metric <-> Imperial toggle component switches table displays throughout
    composeTestRule.onNodeWithTag("aviation_table_unit_system_toggle").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("table_unit_metric").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertEquals(WindSpeedUnit.MPS, vm.uiState.value.windUnit)
    assertEquals(PressureUnit.HPA, vm.uiState.value.pressureUnit)
    composeTestRule.onNodeWithText("8 °C / 6 °C").performScrollTo().assertIsDisplayed()

    composeTestRule.onNodeWithTag("table_unit_imperial").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertEquals(WindSpeedUnit.KT, vm.uiState.value.windUnit)
    assertEquals(PressureUnit.INHG, vm.uiState.value.pressureUnit)
    composeTestRule.onNodeWithText("46 °F / 43 °F").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun `24h history screen displays qnh and temperature line chart and filters metar and taf entries`() {
    val vm = MeteoViewModel(autoFetch = false)
    val now = System.currentTimeMillis()
    val utam = AirportCatalog.findOrDefault("UTAM")
    val entries = listOf(
      com.example.domain.WeatherHistoryAnalyzer.parseHistoricalMetar(
        rawMetar = "METAR UTAM 240400Z 09008KT 9999 FEW040 09/03 Q1011",
        rawTaf = "TAF UTAM 240300Z 2406/2506 09010KT 9999 FEW040",
        station = utam,
        sourceLabel = "test-ip",
        explicitTimestampMillis = now - 6L * 3600_000L,
        referenceNowMillis = now,
      )!!,
      com.example.domain.WeatherHistoryAnalyzer.parseHistoricalMetar(
        rawMetar = "METAR UTAM 240800Z 12014KT 5000 BR SCT025 14/07 Q1015",
        rawTaf = "TAF UTAM — нет данных",
        station = utam,
        sourceLabel = "test-ip",
        explicitTimestampMillis = now - 2L * 3600_000L,
        referenceNowMillis = now,
      )!!,
      com.example.domain.WeatherHistoryAnalyzer.parseHistoricalMetar(
        rawMetar = "METAR UTAM 241000Z 24022G30KT 2000 BR BKN012 17/09 Q1018",
        rawTaf = "TAF UTAM 240900Z 2412/2512 24020KT 3000 BR BKN012",
        station = utam,
        sourceLabel = "test-ip",
        explicitTimestampMillis = now - 30L * 60_000L,
        referenceNowMillis = now,
      )!!,
    )
    vm.mergeExternal24hHistory("UTAM", entries)

    composeTestRule.setContent {
      MeteoTabloTheme(isNightMode = false) {
        MeteoTabloScreen(viewModel = vm, onFullscreenChanged = {})
      }
    }
    composeTestRule.waitForIdle()

    // Open 24h History Screen from top toolbar
    composeTestRule.onNodeWithTag("toolbar_history_24h_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("history_24h_screen").assertIsDisplayed()
    composeTestRule.onNodeWithTag("qnh_line_chart_card").assertIsDisplayed()
    composeTestRule.onNodeWithTag("qnh_line_chart_canvas").assertIsDisplayed()
    composeTestRule.onNodeWithText("1011 гПа").assertIsDisplayed()
    composeTestRule.onAllNodesWithText("1018 гПа").onFirst().assertIsDisplayed()

    // Switch chart dynamically from Pressure (QNH) to Temperature (T / DP)
    composeTestRule.onNodeWithTag("chart_metric_TEMPERATURE").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("chart_legend_row").assertIsDisplayed()
    composeTestRule.onNodeWithText("9 °C").assertIsDisplayed()
    composeTestRule.onAllNodesWithText("17 °C").onFirst().assertIsDisplayed()

    // Switch back to Pressure QNH and verify filtering ALL (3) -> TAF (2) -> METAR (3)
    composeTestRule.onNodeWithTag("chart_metric_PRESSURE").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("history_24h_list").performScrollToKey("filter_bar")
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("history_entries_count").assertTextContains("3 зап.")
    composeTestRule.onNodeWithTag("history_filter_TAF").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("history_entries_count").assertTextContains("2 зап.")
    composeTestRule.onNodeWithTag("history_filter_METAR").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("history_entries_count").assertTextContains("3 зап.")
  }

  @Test
  fun `qnh and temperature line chart handles zero-size bounds safely`() {
    val trend = com.example.model.QnhTrendSummary(
      icao = "UTAM",
      points = listOf(
        com.example.model.QnhHistoryPoint("240800Z", 1000L, 1013, 29.91),
        com.example.model.QnhHistoryPoint("240900Z", 4600L, 1016, 30.00),
      ),
      minHpa = 1013,
      maxHpa = 1016,
      latestHpa = 1016,
      delta24hHpa = 3,
      temperaturePoints = listOf(
        com.example.model.TemperatureHistoryPoint("240800Z", 1000L, 12, 6),
        com.example.model.TemperatureHistoryPoint("240900Z", 4600L, 15, 8),
      ),
      minTempC = 12,
      maxTempC = 15,
      latestTempC = 15,
      delta24hTempC = 3,
    )
    composeTestRule.setContent {
      MeteoTabloTheme(isNightMode = false) {
        Box(modifier = Modifier.size(0.dp)) {
          com.example.ui.components.QnhHistoryLineChart(
            trend = trend,
            pressureUnit = PressureUnit.HPA,
          )
        }
      }
    }
    composeTestRule.waitForIdle()
  }

  @Test
  fun `settings auto refresh toggles on off selects 5 15 30 min persists and top bar has manual refresh`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val unitStorage = SharedPreferencesUnitPreferencesStorage(context)
    unitStorage.savePreferences(
      com.example.data.UnitPreferences(
        windUnit = WindSpeedUnit.KT,
        pressureUnit = PressureUnit.HPA,
        autoRefreshEnabled = true,
        autoRefreshIntervalMinutes = 15,
      ),
    )

    val vm = MeteoViewModel(unitPreferencesStorage = unitStorage, autoFetch = false)
    composeTestRule.setContent {
      MeteoTabloTheme(isNightMode = false) {
        MeteoTabloScreen(viewModel = vm, onFullscreenChanged = {})
      }
    }
    composeTestRule.waitForIdle()

    // Manual refresh button & auto badge present in top app bar
    composeTestRule.onNodeWithTag("refresh_button").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("toolbar_auto_refresh_badge").performScrollTo().assertTextContains("АВТО 15м")

    // Open Settings and test 5 / 15 / 30 min intervals + enable/disable toggle
    composeTestRule.onNodeWithTag("toolbar_settings_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("settings_auto_refresh_card").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("settings_auto_refresh_interval_5").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertEquals(5, vm.uiState.value.autoRefreshIntervalMinutes)

    composeTestRule.onNodeWithTag("settings_auto_refresh_interval_30").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertEquals(30, vm.uiState.value.autoRefreshIntervalMinutes)

    composeTestRule.onNodeWithTag("settings_auto_refresh_switch").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertFalse(vm.uiState.value.autoRefreshEnabled)
    composeTestRule.onNodeWithTag("settings_auto_refresh_status").assertTextContains("Отключено", substring = true)

    // Return to main board and verify top bar reflects manual-only mode
    composeTestRule.onNodeWithTag("settings_back_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("toolbar_auto_refresh_badge").performScrollTo().assertTextContains("РУЧН.")

    // Re-enable in Settings at 5 min and verify persistence across ViewModel recreation
    composeTestRule.onNodeWithTag("toolbar_settings_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("settings_auto_refresh_switch").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("settings_auto_refresh_interval_5").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    val vmReloaded = MeteoViewModel(unitPreferencesStorage = SharedPreferencesUnitPreferencesStorage(context), autoFetch = false)
    assertTrue(vmReloaded.uiState.value.autoRefreshEnabled)
    assertEquals(5, vmReloaded.uiState.value.autoRefreshIntervalMinutes)
  }

  @Test
  fun `my airports bar on main screen saves switches removes and persists custom ICAO codes`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val favStorage = SharedPreferencesFavoriteStorage(context)
    favStorage.saveFavorites(listOf("UTAM", "UTAA"))

    val vm = MeteoViewModel(favoriteStorage = favStorage, autoFetch = false)
    composeTestRule.setContent {
      MeteoTabloTheme(isNightMode = false) {
        MeteoTabloScreen(viewModel = vm, onFullscreenChanged = {})
      }
    }
    composeTestRule.waitForIdle()

    // My Airports is separated from the main board into a dedicated screen
    composeTestRule.onNodeWithTag("my_airports_bar").assertDoesNotExist()
    composeTestRule.onNodeWithTag("toolbar_my_airports_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("my_airports_screen").assertIsDisplayed()

    composeTestRule.onNodeWithTag("my_airports_bar").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("my_airport_chip_UTAM").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("my_airport_chip_UTAA").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertEquals("UTAA", vm.uiState.value.station.icao)

    // Add a custom ICAO (KJFK) via "+ ICAO" button
    composeTestRule.onNodeWithTag("my_airports_add_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("my_airports_icao_input").performTextInput("kjfk")
    composeTestRule.onNodeWithTag("my_airports_confirm_add").performClick()
    composeTestRule.waitForIdle()

    assertTrue("KJFK" in vm.uiState.value.favoriteIcaos)
    assertEquals("KJFK", vm.uiState.value.station.icao)
    composeTestRule.onNodeWithTag("my_airport_chip_KJFK").performScrollTo().assertIsDisplayed()

    // Remove KJFK from My Airports and verify toggle-current adds it back
    composeTestRule.onNodeWithTag("my_airport_remove_KJFK").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertFalse("KJFK" in vm.uiState.value.favoriteIcaos)

    composeTestRule.onNodeWithTag("my_airports_toggle_current").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertTrue("KJFK" in vm.uiState.value.favoriteIcaos)

    // Verify persistence
    val vmReloaded = MeteoViewModel(favoriteStorage = SharedPreferencesFavoriteStorage(context), autoFetch = false)
    assertTrue("KJFK" in vmReloaded.uiState.value.favoriteIcaos)
  }

  @Test
  fun `my airports search autocomplete finds local ICAO database stations and adds them`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val favStorage = SharedPreferencesFavoriteStorage(context)
    favStorage.saveFavorites(listOf("UTAM"))

    val vm = MeteoViewModel(favoriteStorage = favStorage, autoFetch = false)
    composeTestRule.setContent {
      MeteoTabloTheme(isNightMode = false) {
        MeteoTabloScreen(viewModel = vm, onFullscreenChanged = {})
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("toolbar_my_airports_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("my_airports_screen").assertIsDisplayed()

    // Search local ICAO database by city/prefix ("EG") and select EGLL from autocomplete list
    composeTestRule.onNodeWithTag("my_airports_search_input").performScrollTo().performTextInput("EG")
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("my_airports_autocomplete_list").assertIsDisplayed()
    composeTestRule.onNodeWithTag("my_airports_autocomplete_item_EGLL").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    assertTrue("EGLL" in vm.uiState.value.favoriteIcaos)
    assertEquals("EGLL", vm.uiState.value.station.icao)
    composeTestRule.onNodeWithTag("my_airport_chip_EGLL").performScrollTo().assertIsDisplayed()

    // Search by Russian city name ("Ташкент") -> finds UTTT in local ICAO database
    composeTestRule.onNodeWithTag("my_airports_search_input").performScrollTo().performTextInput("Ташкент")
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("my_airports_autocomplete_item_UTTT").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertTrue("UTTT" in vm.uiState.value.favoriteIcaos)
    assertEquals("UTTT", vm.uiState.value.station.icao)
  }

  @Test
  fun `settings theme selector switches between light and dark cockpit modes and persists`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val unitStorage = SharedPreferencesUnitPreferencesStorage(context)
    unitStorage.savePreferences(com.example.data.UnitPreferences(isNightMode = false))

    val vm = MeteoViewModel(unitPreferencesStorage = unitStorage, autoFetch = false)
    composeTestRule.setContent {
      MeteoTabloTheme(isNightMode = vm.uiState.value.isNightMode) {
        MeteoTabloScreen(viewModel = vm, onFullscreenChanged = {})
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("toolbar_settings_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("settings_theme_card").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("settings_theme_dark").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertTrue(vm.uiState.value.isNightMode)
    composeTestRule.onNodeWithTag("settings_theme_status").assertTextContains("ночной кокпит", substring = true)

    // Verify persistence across ViewModel recreation
    val vmReloaded = MeteoViewModel(unitPreferencesStorage = SharedPreferencesUnitPreferencesStorage(context), autoFetch = false)
    assertTrue(vmReloaded.uiState.value.isNightMode)

    // Switch back to Light Cockpit mode via Light card
    composeTestRule.onNodeWithTag("settings_theme_light").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertFalse(vm.uiState.value.isNightMode)
    composeTestRule.onNodeWithTag("settings_theme_status").assertTextContains("дневной кокпит", substring = true)
  }

  @Test
  fun `weather report formatter and android exporter generate valid pdf and text summary with trends`() {
    val vm = MeteoViewModel(autoFetch = false)
    vm.selectStation("UTAA")
    vm.applyCustomMetar(
      rawMetar = "METAR UTAA 240900Z 12012KT 9999 FEW030 18/06 Q1018 NOSIG",
      rawTaf = "TAF UTAA 240800Z 2409/2509 12010KT CAVOK",
    )
    vm.mergeExternal24hHistory(
      icao = "UTAA",
      entries = listOf(
        com.example.model.WeatherHistoryEntry(
          icao = "UTAA",
          observationTimeUtc = "240600Z",
          observedAtMillis = System.currentTimeMillis() - 10_800_000L,
          windSpeedKt = 6,
          windDirectionDeg = 110,
          visibilityMeters = 9999,
          isCavok = true,
          temperatureC = 14,
          dewPointC = 5,
          qnhHpa = 1015,
          qnhInHg = 29.97,
          flightCategoryCode = "VFR",
          rawMetar = "METAR UTAA 240600Z 11006KT CAVOK 14/05 Q1015",
          rawTaf = "TAF UTAA 240500Z 2406/2506 11008KT CAVOK",
        ),
        com.example.model.WeatherHistoryEntry(
          icao = "UTAA",
          observationTimeUtc = "240900Z",
          observedAtMillis = System.currentTimeMillis(),
          windSpeedKt = 12,
          windDirectionDeg = 120,
          visibilityMeters = 9999,
          isCavok = false,
          temperatureC = 18,
          dewPointC = 6,
          qnhHpa = 1018,
          qnhInHg = 30.06,
          flightCategoryCode = "VFR",
          rawMetar = "METAR UTAA 240900Z 12012KT 9999 FEW030 18/06 Q1018 NOSIG",
          rawTaf = "TAF UTAA 240800Z 2409/2509 12010KT CAVOK",
        ),
      ),
    )

    val state = vm.uiState.value
    val payload = com.example.domain.WeatherExportPayload(
      station = state.station,
      weather = state.weather,
      primaryRunwayCalc = state.primaryRunwayCalc,
      reciprocalRunwayCalc = state.reciprocalRunwayCalc,
      windUnit = state.windUnit,
      pressureUnit = state.pressureUnit,
      windTrend = state.windTrend,
      qnhTrend24h = state.qnhTrend24h,
      weatherHistory24h = state.weatherHistory24h,
    )

    val fileName = com.example.domain.WeatherReportFormatter.suggestedFileBaseName(state.station, state.weather)
    assertTrue(fileName.startsWith("Meteotablo_UTAA_"))

    val txtOut = java.io.ByteArrayOutputStream()
    com.example.export.AndroidWeatherReportExporter.writeTextSummary(txtOut, payload)
    val summary = txtOut.toString("UTF-8")
    assertTrue(summary.contains("UTAA"))
    assertTrue(summary.contains("Ashgabat"))
    assertTrue(summary.contains("Q1018"))
    assertTrue(summary.contains("Тренд QNH 24ч"))
    assertTrue(summary.contains("TAF UTAA 240800Z"))

    val pdfOut = java.io.ByteArrayOutputStream()
    com.example.export.AndroidWeatherReportExporter.writePdfSummary(pdfOut, payload)
    val pdfBytes = pdfOut.toByteArray()
    assertTrue(pdfBytes.size > 500)
    assertEquals("%PDF-", String(pdfBytes, 0, 5, java.nio.charset.StandardCharsets.US_ASCII))
  }

  @Test
  fun `export dialog opens from quick controls menu and copies report summary`() {
    val vm = MeteoViewModel(autoFetch = false)
    vm.selectStation("UTAM")
    composeTestRule.setContent {
      MeteoTabloTheme(isNightMode = false) {
        MeteoTabloScreen(viewModel = vm, onFullscreenChanged = {})
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("toolbar_export_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("export_report_dialog").assertIsDisplayed()
    composeTestRule.onNodeWithTag("export_save_pdf_button").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("export_save_txt_button").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("export_summary_preview").performScrollTo().assertTextContains("UTAM", substring = true)

    composeTestRule.onNodeWithTag("export_copy_summary_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("export_status_message").performScrollTo().assertTextContains("скопирована", substring = true)

    composeTestRule.onNodeWithTag("export_close_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("export_report_dialog").assertDoesNotExist()
  }

  @Test
  fun `my airports coordinate plot displays saved airport pins and switches station on tap`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val favStorage = SharedPreferencesFavoriteStorage(context)
    favStorage.saveFavorites(listOf("UTAM", "UTAA", "EGLL"))

    val utam = AirportCatalog.findKnown("UTAM")!!
    val egll = AirportCatalog.findKnown("EGLL")!!
    val bounds = AirportMapProjection.computeCoordinatePlotBounds(listOf(utam, egll))
    assertTrue(bounds.minLat < utam.latitude && bounds.maxLat > egll.latitude)
    assertTrue(AirportMapProjection.haversineKm(utam.latitude, utam.longitude, egll.latitude, egll.longitude) > 4500)

    val vm = MeteoViewModel(favoriteStorage = favStorage, autoFetch = false)
    composeTestRule.setContent {
      MeteoTabloTheme(isNightMode = false) {
        MeteoTabloScreen(viewModel = vm, onFullscreenChanged = {})
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("toolbar_my_airports_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("my_airports_screen").assertIsDisplayed()

    composeTestRule.onNodeWithTag("my_airports_plot_toggle").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("my_airports_coordinate_plot").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("my_airports_plot_canvas").assertIsDisplayed()
    composeTestRule.onNodeWithTag("coordinate_plot_pin_UTAM").assertExists()
    composeTestRule.onNodeWithTag("coordinate_plot_pin_UTAA").assertExists()
    composeTestRule.onNodeWithTag("coordinate_plot_pin_EGLL").assertExists()

    composeTestRule.onNodeWithTag("coordinate_plot_pin_EGLL").performClick()
    composeTestRule.waitForIdle()

    assertEquals("EGLL", vm.uiState.value.station.icao)
    composeTestRule.onNodeWithTag("coordinate_plot_selected_info").assertTextContains("EGLL", substring = true)
  }

  @Test
  fun `qnh qfe pressure conversion utility toggles hPa and inHg and computes accurate aviation metrics`() {
    // Domain math verification
    val qfeAt138m = com.example.domain.UnitConverter.computeQfeFromQnhHpa(1013.25, 138)
    assertTrue(qfeAt138m in 995.5..998.0)
    val qnhRestored = com.example.domain.UnitConverter.computeQnhFromQfeHpa(qfeAt138m, 138)
    assertEquals(1013.25, qnhRestored, 0.05)
    assertEquals(1013.2, com.example.domain.UnitConverter.convertInHgToHpa(29.92), 0.3)

    val vm = MeteoViewModel(autoFetch = false)
    vm.selectStation("UTAA")
    vm.applyCustomMetar(
      rawMetar = "METAR UTAA 241000Z 12010KT CAVOK 22/08 Q1018 NOSIG",
      rawTaf = "TAF UTAA",
    )

    composeTestRule.setContent {
      MeteoTabloTheme(isNightMode = false) {
        MeteoTabloScreen(viewModel = vm, onFullscreenChanged = {})
      }
    }
    composeTestRule.waitForIdle()

    // Open QNH/QFE conversion utility directly from toolbar
    composeTestRule.onNodeWithTag("toolbar_pressure_utility_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("pressure_utility_dialog").assertIsDisplayed()
    composeTestRule.onNodeWithTag("pressure_utility_qnh_result").assertTextContains("1018 гПа", substring = true)
    composeTestRule.onNodeWithTag("pressure_utility_qfe_result").assertTextContains("гПа", substring = true)

    // Toggle converter unit hPa -> inHg
    composeTestRule.onNodeWithTag("pressure_utility_unit_INHG").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("pressure_utility_qnh_result").assertTextContains("30.06 inHg", substring = true)
    composeTestRule.onNodeWithTag("pressure_utility_qfe_result").assertTextContains("inHg", substring = true)

    // Switch direction to QFE -> QNH
    composeTestRule.onNodeWithTag("pressure_utility_mode_QFE_TO_QNH").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("pressure_utility_planning_metrics").performScrollTo().assertIsDisplayed()

    // Toggle back to hPa & close
    composeTestRule.onNodeWithTag("pressure_utility_unit_HPA").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("pressure_utility_qnh_result").performScrollTo().assertTextContains("гПа", substring = true)
    composeTestRule.onNodeWithTag("pressure_utility_close_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("pressure_utility_dialog").assertDoesNotExist()
  }

  @Test
  fun `dual runway wind rose visualization displays headwind crosswind for both ends and switches runways`() {
    val vm = MeteoViewModel(autoFetch = false)
    vm.selectStation("UTAA")
    vm.applyCustomMetar(
      rawMetar = "METAR UTAA 241000Z 15018G26KT 9999 FEW040 24/06 Q1016 NOSIG",
      rawTaf = "TAF UTAA",
    )

    composeTestRule.setContent {
      MeteoTabloTheme(isNightMode = false) {
        MeteoTabloScreen(viewModel = vm, onFullscreenChanged = {})
      }
    }
    composeTestRule.waitForIdle()

    // Toggle dual runway wind rose on main board
    composeTestRule.onNodeWithTag("toolbar_dual_wind_rose_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("dual_runway_wind_rose_panel").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("dual_rwy_compass_canvas").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("dual_rwy_recommendation").performScrollTo().assertTextContains("12L", substring = true)
    composeTestRule.onNodeWithTag("dual_rwy_card_12L").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("dual_rwy_card_30R").performScrollTo().assertIsDisplayed()

    // Switch selected runway pair 12L/30R -> 12R/30L right from dual wind rose selector
    composeTestRule.onNodeWithTag("dual_rwy_pair_chip_1").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertEquals(1, vm.uiState.value.selectedRunwayPairIndex)
    composeTestRule.onNodeWithTag("dual_rwy_card_12R").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("dual_rwy_card_30L").performScrollTo().assertIsDisplayed()

    // Toggle unit inside dual wind rose kt -> м/с
    composeTestRule.onNodeWithTag("dual_rwy_unit_toggle").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertEquals(WindSpeedUnit.MPS, vm.uiState.value.windUnit)
  }

  @Test
  fun `metar taf decoder translates all aviation codes into plain open explanations`() {
    val station = AirportCatalog.findKnown("UTAA")!!
    val metarRes = com.example.domain.MetarTafDecoder.decodeMetar(
      rawMetarInput = "METAR UTAA 241430Z 29018G28KT 240V330 1200 R12L/0800U +TSRA FEW008 BKN015CB 19/18 Q1009 NOSIG",
      stationHint = station,
    )
    assertTrue(metarRes.plainSummaryParagraph.contains("Ashgabat"))
    assertTrue(metarRes.items.any { it.rawCode == "+TSRA" && it.fullExplanation.contains("гроза", ignoreCase = true) })
    assertTrue(metarRes.items.any { it.rawCode == "Q1009" && it.fullExplanation.contains("1009 гПа") })
    assertTrue(metarRes.items.any { it.rawCode == "NOSIG" && it.fullExplanation.contains("изменений", ignoreCase = true) })

    val tafRes = com.example.domain.MetarTafDecoder.decodeTaf(
      rawTafInput = "TAF AMD UTAA 241400Z 2415/2515 28016KT CAVOK TX26/2510Z TEMPO 2416/2420 0800 +TSGRRA BKN008CB PROB30 BECMG 2504/2506 NSW",
      stationHint = station,
    )
    assertTrue(tafRes.items.any { it.rawCode == "AMD" })
    assertTrue(tafRes.items.any { it.rawCode == "TX26/2510Z" && it.fullExplanation.contains("Максимальная", ignoreCase = true) })
    assertTrue(tafRes.items.any { it.rawCode == "PROB30" && it.fullExplanation.contains("30%") })

    val vm = MeteoViewModel(autoFetch = false)
    vm.selectStation("UTAA")
    composeTestRule.setContent {
      MeteoTabloTheme(isNightMode = false) {
        MeteoTabloScreen(viewModel = vm, onFullscreenChanged = {})
      }
    }
    composeTestRule.waitForIdle()

    // Open full METAR/TAF Decoder screen from toolbar
    composeTestRule.onNodeWithTag("toolbar_decoder_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("metar_taf_decoder_screen").assertIsDisplayed()
    composeTestRule.onNodeWithTag("decoder_metar_plain_summary").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("decoder_taf_plain_summary").performScrollTo().assertIsDisplayed()

    // Load sample severe-weather METAR/TAF with all aviation code groups and verify plain decoding
    composeTestRule.onNodeWithTag("decoder_load_sample_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("decoder_metar_plain_summary").performScrollTo().assertTextContains("Ветер:", substring = true)
    composeTestRule.onNodeWithTag("decoder_metar_token_0").assertExists()
    composeTestRule.onNodeWithTag("decoder_taf_token_0").assertExists()

    // Switch tabs METAR -> TAF -> ALL
    composeTestRule.onNodeWithTag("decoder_tab_TAF").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("decoder_metar_plain_summary").assertDoesNotExist()
    composeTestRule.onNodeWithTag("decoder_taf_plain_summary").performScrollTo().assertIsDisplayed()

    composeTestRule.onNodeWithTag("decoder_back_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("metar_taf_decoder_screen").assertDoesNotExist()
  }

  @Test
  fun `d3 saved airport trends dashboard renders wind and pressure charts switches chips and updates kpis`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val favStorage = SharedPreferencesFavoriteStorage(context)
    favStorage.saveFavorites(listOf("UTAA", "UTAM"))

    val vm = MeteoViewModel(favoriteStorage = favStorage, autoFetch = false)
    val now = System.currentTimeMillis()
    val utaa = AirportCatalog.findOrDefault("UTAA")
    val utam = AirportCatalog.findOrDefault("UTAM")

    vm.mergeExternal24hHistory(
      icao = "UTAA",
      entries = listOf(
        com.example.domain.WeatherHistoryAnalyzer.parseHistoricalMetar(
          rawMetar = "METAR UTAA 250600Z 09008KT 9999 FEW040 16/05 Q1014",
          rawTaf = "TAF UTAA",
          station = utaa,
          sourceLabel = "test",
          explicitTimestampMillis = now - 4L * 3600_000L,
          referenceNowMillis = now,
        )!!,
        com.example.domain.WeatherHistoryAnalyzer.parseHistoricalMetar(
          rawMetar = "METAR UTAA 250900Z 11018G25KT 9999 SCT040 20/06 Q1020",
          rawTaf = "TAF UTAA",
          station = utaa,
          sourceLabel = "test",
          explicitTimestampMillis = now - 1L * 3600_000L,
          referenceNowMillis = now,
        )!!,
      ),
    )
    vm.mergeExternal24hHistory(
      icao = "UTAM",
      entries = listOf(
        com.example.domain.WeatherHistoryAnalyzer.parseHistoricalMetar(
          rawMetar = "METAR UTAM 250700Z 06010KT 8000 FEW035 15/04 Q1012",
          rawTaf = "TAF UTAM",
          station = utam,
          sourceLabel = "test",
          explicitTimestampMillis = now - 2L * 3600_000L,
          referenceNowMillis = now,
        )!!,
      ),
    )

    composeTestRule.setContent {
      MeteoTabloTheme(isNightMode = false) {
        MeteoTabloScreen(viewModel = vm, onFullscreenChanged = {})
      }
    }
    composeTestRule.waitForIdle()

    // D3.js trends dashboard is on its own dedicated screen, not on the main board
    composeTestRule.onNodeWithTag("d3_trends_dashboard").assertDoesNotExist()
    composeTestRule.onNodeWithTag("toolbar_d3_trends_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("d3_trends_screen").assertIsDisplayed()

    composeTestRule.onNodeWithTag("d3_trends_dashboard").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("d3_airport_chip_UTAA").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertEquals("UTAA", vm.uiState.value.selectedD3TrendIcao)
    composeTestRule.onNodeWithTag("d3_kpi_wind").performScrollTo().assertTextContains("8–18", substring = true)
    composeTestRule.onNodeWithTag("d3_kpi_pressure").performScrollTo().assertTextContains("1014–1020", substring = true)

    // Switch chip to UTAM
    composeTestRule.onNodeWithTag("d3_airport_chip_UTAM").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertEquals("UTAM", vm.uiState.value.selectedD3TrendIcao)
    composeTestRule.onNodeWithTag("d3_kpi_pressure").performScrollTo().assertTextContains("1012", substring = true)
  }

  @Test
  fun `vfr minimums alert system triggers notification when conditions drop below threshold`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    org.robolectric.Shadows.shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    val context = app
    val favStorage = SharedPreferencesFavoriteStorage(context)
    favStorage.saveFavorites(listOf("UTAA"))
    val alertStorage = SharedPreferencesWeatherAlertStorage(context)
    alertStorage.saveConfig(
      com.example.model.WeatherAlertConfig(
        enabled = true,
        vfrAlertsEnabled = true,
        vfrMinVisibilityMeters = 5000,
        vfrMinCeilingFeet = 1500,
        vfrMaxWindKt = 20,
        vfrMonitorAllFavorites = true,
      ),
    )
    alertStorage.saveAlerts(emptyList())

    val fakeNotifier = AndroidWeatherNotifier(context)
    val vm = MeteoViewModel(
      favoriteStorage = favStorage,
      alertStorage = alertStorage,
      weatherNotifier = fakeNotifier,
      autoFetch = false,
    )
    vm.selectStation("UTAA")
    // Below VFR thresholds (visibility 2200m < 5000m, ceiling BKN008=800ft < 1500ft, wind 24kt > 20kt)
    vm.applyCustomMetar(
      rawMetar = "METAR UTAA 251000Z 28024G32KT 2200 BR BKN008 11/09 Q1009",
      rawTaf = "TAF UTAA",
    )

    vm.checkVfrMinimumsNow()
    val alerts = vm.uiState.value.weatherAlerts
    assertTrue("Expected VFR breach alert", alerts.any { it.icao == "UTAA" && it.summary.contains("VFR", ignoreCase = true) })

    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val shadowNm = org.robolectric.Shadows.shadowOf(nm)
    assertTrue("Expected system notification posted", shadowNm.allNotifications.isNotEmpty())
  }

  @Test
  fun `manual lat lon coordinate entry and gps location service update station and location summary`() {
    val fakeLocationService = object : com.example.location.LocationService {
      override suspend fun detectCurrentLocation(): Result<com.example.location.DetectedLocationResult> {
        return Result.success(
          com.example.location.DetectedLocationResult(
            latitude = 37.9868,
            longitude = 58.3610,
            cityName = "Ashgabat",
            providerLabel = "FusedGPS",
          ),
        )
      }
    }

    val vm = MeteoViewModel(
      locationService = fakeLocationService,
      autoFetch = false,
    )
    // Grant location permission in Robolectric shadow so GPS tap invokes LocationService immediately
    val app = ApplicationProvider.getApplicationContext<Application>()
    org.robolectric.Shadows.shadowOf(app).grantPermissions(
      Manifest.permission.ACCESS_FINE_LOCATION,
      Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    composeTestRule.setContent {
      MeteoTabloTheme(isNightMode = false) {
        MeteoTabloScreen(viewModel = vm, onFullscreenChanged = {})
      }
    }
    composeTestRule.waitForIdle()

    // Tap GPS button -> fakeLocationService detects Ashgabat -> summary updated
    composeTestRule.onNodeWithTag("location_gps_detect_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    val summaryAfterGps = vm.uiState.value.detectedLocationSummary
    assertTrue("Expected GPS location summary, got $summaryAfterGps", summaryAfterGps?.contains("Ashgabat") == true)

    // Open manual Lat/Lon coordinate entry panel and test invalid + valid input
    composeTestRule.onNodeWithTag("coord_toggle_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("coord_entry_panel").performScrollTo().assertIsDisplayed()

    composeTestRule.onNodeWithTag("coord_lat_input").performScrollTo().performTextInput("95.0")
    composeTestRule.onNodeWithTag("coord_lon_input").performScrollTo().performTextInput("58.36")
    composeTestRule.onNodeWithTag("coord_fetch_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("coord_error_text").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun `location service finds nearest airport and board displays density altitude telemetry`() {
    val fakeLocationService = object : com.example.location.LocationService {
      override suspend fun detectCurrentLocation(): Result<com.example.location.DetectedLocationResult> {
        return Result.success(
          com.example.location.DetectedLocationResult(
            latitude = 37.62,
            longitude = 61.90,
            cityName = "Mary",
            providerLabel = "GPS",
          ),
        )
      }
    }
    val nearest = kotlinx.coroutines.runBlocking {
      fakeLocationService.detectNearestAirport().getOrThrow()
    }
    assertEquals("UTAM", nearest.airport.icao)
    assertTrue(nearest.distanceKm < 15.0)

    val (ashgabatNearest, distAsh) = com.example.location.LocationServiceHelper.findNearestAirport(37.98, 58.36)!!
    assertEquals("UTAA", ashgabatNearest.icao)
    assertTrue(distAsh < 5.0)

    val vm = MeteoViewModel(locationService = fakeLocationService, autoFetch = false)
    vm.selectStation("UTAA")
    vm.applyCustomMetar("METAR UTAA 251200Z 09010KT CAVOK 38/06 Q1008", "TAF UTAA")

    composeTestRule.setContent {
      MeteoTabloTheme(isNightMode = false) {
        MeteoTabloScreen(viewModel = vm, onFullscreenChanged = {})
      }
    }
    composeTestRule.waitForIdle()
    // PA/DA performance card is removed from main board for dispatchers
    composeTestRule.onNodeWithTag("aviation_performance_card").assertDoesNotExist()
  }

  @Test
  fun `pressure conversion utility auto updates when new metar arrives and reacts to elevation input`() {
    val conv = com.example.domain.UnitConverter.convertQnhQfe(
      pressureValue = 1020.0,
      unit = PressureUnit.HPA,
      elevationMeters = 500,
      fromQnhToQfe = true,
    )
    assertEquals(1020.0, conv.qnhHpa, 0.01)
    assertTrue(conv.qfeHpa in 958.0..963.0)
    assertTrue(conv.baricOffsetHpa > 55.0)

    val vm = MeteoViewModel(autoFetch = false)
    vm.selectStation("UTAA")
    vm.applyCustomMetar("METAR UTAA 251200Z 09010KT CAVOK 24/08 Q1018", "TAF UTAA")

    composeTestRule.setContent {
      MeteoTabloTheme(isNightMode = false) {
        MeteoTabloScreen(viewModel = vm, onFullscreenChanged = {})
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("toolbar_pressure_utility_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("pressure_utility_auto_sync_switch").assertIsDisplayed()
    composeTestRule.onNodeWithTag("pressure_utility_qnh_result").assertTextContains("1018 гПа", substring = true)

    // Adjust elevation (+50m) -> QFE changes accordingly
    composeTestRule.onNodeWithTag("pressure_utility_elev_plus").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("pressure_utility_qfe_result").assertTextContains("гПа", substring = true)

    // Simulate new METAR arriving -> QNH/QFE auto-update immediately
    vm.applyCustomMetar("METAR UTAA 251300Z 09012KT CAVOK 23/08 Q1025", "TAF UTAA")
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("pressure_utility_qnh_result").assertTextContains("1025 гПа", substring = true)
  }

  @Test
  fun `runway wind components composable calculates headwind tailwind and crosswind for given heading`() {
    val wind = WindData(
      directionDeg = 300,
      isVariable = false,
      isCalm = false,
      speedKt = 20,
      gustKt = 30,
      speedMps = 10.29,
      gustMps = 15.43,
    )

    // Runway heading 270° with wind from 300° => 30° from right: cos(30)=17.3kt headwind, sin(30)=10.0kt right crosswind
    val comp270 = com.example.ui.components.calculateRunwayWindComponents(270, wind)
    assertEquals(17.32, comp270.headwindKt, 0.2)
    assertEquals(10.0, comp270.crosswindKt, 0.2)
    assertTrue(comp270.signedCrosswindKt > 0.0)
    assertFalse(comp270.isTailwind)

    // Reciprocal runway 090° => tailwind (-17.3kt) and left crosswind
    val comp090 = com.example.ui.components.calculateRunwayWindComponents(90, wind)
    assertTrue(comp090.isTailwind)
    assertEquals(-17.32, comp090.headwindKt, 0.2)

    val headingState = androidx.compose.runtime.mutableStateOf(270)
    composeTestRule.setContent {
      MeteoTabloTheme(isNightMode = false) {
        com.example.ui.components.RunwayWindComponentsDisplay(
          runwayHeadingDeg = headingState.value,
          wind = wind,
          windUnit = WindSpeedUnit.KT,
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("runway_wind_components_display").assertIsDisplayed()
    composeTestRule.onNodeWithTag("rwy_component_headwind_box").assertTextContains("+17.3 kt", substring = true)
    composeTestRule.onNodeWithTag("rwy_component_crosswind_box").assertTextContains("10.0 kt", substring = true)

    // Switch runway heading to reciprocal 090° -> displays Tailwind component
    headingState.value = 90
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("rwy_component_headwind_box").assertTextContains("-17.3 kt", substring = true)
  }

  @Test
  fun `runway orientation wind visualizer renders relative geometry and crosswind exceedance alert`() {
    val strongCrosswind = WindData(
      directionDeg = 360,
      isVariable = false,
      isCalm = false,
      speedKt = 24,
      gustKt = 30,
      speedMps = 12.35,
      gustMps = 15.43,
    )
    val limitState = androidx.compose.runtime.mutableStateOf(15)

    composeTestRule.setContent {
      MeteoTabloTheme(isNightMode = false) {
        com.example.ui.components.RunwayOrientationWindVisualizer(
          runwayDesignator = "27",
          runwayHeadingDeg = 270,
          wind = strongCrosswind,
          windUnit = WindSpeedUnit.KT,
          maxCrosswindKt = limitState.value,
          aircraftLabel = "Cessna 172",
        )
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("runway_orientation_visualizer").assertIsDisplayed()
    composeTestRule.onNodeWithTag("runway_orientation_canvas").assertIsDisplayed()
    composeTestRule.onNodeWithTag("visualizer_crosswind_warning").assertIsDisplayed()
    composeTestRule.onNodeWithTag("visualizer_crosswind_warning").assertTextContains("Cessna 172", substring = true)

    // Raise threshold above 24 kt crosswind -> warning disappears
    limitState.value = 35
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("visualizer_crosswind_warning").assertDoesNotExist()
  }

  @Test
  fun `settings aircraft crosswind limit persists selects preset and triggers board warning banner`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val alertStorage = SharedPreferencesWeatherAlertStorage(context)
    alertStorage.saveConfig(com.example.model.WeatherAlertConfig())

    val vm = MeteoViewModel(alertStorage = alertStorage, autoFetch = false)
    vm.selectStation("UTAM") // 35L = 345 deg
    // Wind from 075° at 22 kt => pure 90° crosswind of 22 kt on 35L
    vm.applyCustomMetar(
      rawMetar = "METAR UTAM 260800Z 07522KT CAVOK 18/06 Q1020 NOSIG",
      rawTaf = "TAF UTAM",
    )

    composeTestRule.setContent {
      MeteoTabloTheme(isNightMode = false) {
        MeteoTabloScreen(viewModel = vm, onFullscreenChanged = {})
      }
    }
    composeTestRule.waitForIdle()

    // Open Settings and configure Cessna 172 (15 kt max crosswind)
    composeTestRule.onNodeWithTag("toolbar_settings_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("settings_crosswind_limit_card").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("settings_aircraft_preset_CESSNA_172").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertEquals(15, vm.uiState.value.alertConfig.maxCrosswindKt)
    assertEquals("CESSNA_172", vm.uiState.value.alertConfig.aircraftTypeId)

    // Adjust +2 kt via stepper -> becomes 17 kt (custom), still below 22 kt crosswind
    composeTestRule.onNodeWithTag("settings_crosswind_plus").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertEquals(17, vm.uiState.value.alertConfig.maxCrosswindKt)
    composeTestRule.onNodeWithTag("settings_crosswind_preview_status").performScrollTo().assertTextContains("ПРЕВЫШЕНИЕ", substring = true)

    // Verify persisted in SharedPreferencesWeatherAlertStorage
    val saved = SharedPreferencesWeatherAlertStorage(context).loadConfig()
    assertEquals(17, saved.maxCrosswindKt)
    assertTrue(saved.crosswindWarningEnabled)

    // Return to main board -> visual warning banner appears above instrument deck
    composeTestRule.onNodeWithTag("settings_back_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("crosswind_limit_warning_banner").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun `recent searches stores last 5 unique ICAO codes locally and allows reselection or clearing from UI`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val recentStorage = SharedPreferencesRecentSearchesStorage(context)
    recentStorage.saveRecentSearches(emptyList())

    val vm = MeteoViewModel(
      recentSearchesStorage = recentStorage,
      autoFetch = false,
    )
    listOf("UTAM", "UTAA", "UTAK", "UTAN", "UTAT", "UTAV").forEach { icao ->
      vm.selectStation(icao)
    }
    // Re-query UTAA: should move to front and remain capped at 5
    vm.selectStation("UTAA")

    assertEquals(listOf("UTAA", "UTAV", "UTAT", "UTAN", "UTAK"), vm.uiState.value.recentSearches)
    assertEquals(listOf("UTAA", "UTAV", "UTAT", "UTAN", "UTAK"), SharedPreferencesRecentSearchesStorage(context).loadRecentSearches())

    composeTestRule.setContent {
      MeteoTabloTheme(isNightMode = false) {
        MeteoTabloScreen(viewModel = vm, onFullscreenChanged = {})
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("recent_searches_row").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("recent_search_chip_UTAK").assertExists().performClick()
    composeTestRule.waitForIdle()

    assertEquals("UTAK", vm.uiState.value.station.icao)
    assertEquals(listOf("UTAK", "UTAA", "UTAV", "UTAT", "UTAN"), vm.uiState.value.recentSearches)

    composeTestRule.onNodeWithTag("recent_searches_clear").performClick()
    composeTestRule.waitForIdle()

    assertTrue(vm.uiState.value.recentSearches.isEmpty())
    assertTrue(SharedPreferencesRecentSearchesStorage(context).loadRecentSearches().isEmpty())
    composeTestRule.onAllNodesWithTag("recent_searches_row").fetchSemanticsNodes().let { nodes ->
      assertTrue(nodes.isEmpty())
    }
  }

  @Test
  fun `wind rose components increment metarFetchSeq and animate rotate pulse cleanly`() {
    val vm = MeteoViewModel(autoFetch = false)
    assertEquals(0L, vm.uiState.value.metarFetchSeq)

    vm.applyCustomMetar("METAR UTAM 261000Z 29012KT CAVOK 20/05 Q1018", "TAF UTAM")
    assertEquals(1L, vm.uiState.value.metarFetchSeq)

    val seqState = androidx.compose.runtime.mutableLongStateOf(0L)
    val sampleWind = WindData(
      directionDeg = 290,
      isVariable = false,
      isCalm = false,
      speedKt = 12,
      gustKt = 20,
      speedMps = 6.2,
    )
    val sampleCalc = RunwayCalculation(
      designator = "35L",
      headingDeg = 345,
      crosswindMps = 5.1,
      headwindMps = 3.5,
      crosswindKt = 9.8,
      headwindKt = 6.9,
      frictionCoeff = 0.65,
      rvrMeters = 1800,
    )

    composeTestRule.setContent {
      MeteoTabloTheme(isNightMode = false) {
        Box {
          RunwayWindRose(
            calculation = sampleCalc,
            wind = sampleWind,
            windUnit = WindSpeedUnit.KT,
            onToggleUnit = {},
            metarFetchSeq = seqState.longValue,
          )
          WindRoseCanvas(
            wind = sampleWind,
            windUnit = WindSpeedUnit.KT,
            onToggleUnit = {},
            activeRunway = sampleCalc,
            metarFetchSeq = seqState.longValue,
          )
        }
      }
    }
    composeTestRule.waitForIdle()

    seqState.longValue = 1L
    composeTestRule.waitForIdle()
    seqState.longValue = 2L
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("wind_rose_canvas").assertIsDisplayed()
    composeTestRule.onNodeWithTag("wind_center_box_35L").assertIsDisplayed()
  }

  @Test
  fun `room weather cache persists latest METAR and skips placeholder reports offline`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = androidx.room.Room.inMemoryDatabaseBuilder(
      context,
      com.example.local.MeteoDatabase::class.java,
    ).allowMainThreadQueries().build()
    try {
      val repo = com.example.local.RoomWeatherCacheRepository(db.cachedWeatherDao())
      val utam = AirportCatalog.findOrDefault("UTAM")
      val valid = kotlinx.coroutines.runBlocking {
        val placeholder = AviationWeatherParser.parse(
          rawMetarInput = "METAR UTAM --:--Z //////KT //// ////// // Q////",
          rawTafInput = "TAF UTAM — нет данных",
          station = utam,
          sourceLabel = "нет данных",
        )
        repo.saveWeather(utam, placeholder, 1000L)
        assertTrue(repo.getAllCachedWeather().isEmpty())

        val validWx = AviationWeatherParser.parse(
          rawMetarInput = "METAR UTAM 270800Z 31012G22KT 9999 SCT035 19/06 Q1021 R35L/CLRD70 NOSIG",
          rawTafInput = "TAF UTAM 270500Z 2706/2806 31012KT CAVOK",
          station = utam,
          sourceLabel = "ведомственный сервер",
        )
        repo.saveWeather(utam, validWx, 2000L)

        val loaded = repo.getCachedWeather("utam")
        assertTrue(loaded != null)
        assertEquals("UTAM", loaded!!.station.icao)
        assertEquals(validWx.rawMetar, loaded.rawMetar)
        assertEquals(validWx.rawTaf, loaded.rawTaf)
        assertEquals(2000L, loaded.cachedAtMillis)
        validWx
      }

      // Reload ViewModel backed by Room repository -> preloads offline cached METAR cleanly
      val vm = MeteoViewModel(
        weatherCacheRepository = repo,
        autoFetch = false,
      )
      val deadline = System.currentTimeMillis() + 3000L
      while (!vm.uiState.value.weather.rawMetar.contains("270800Z") && System.currentTimeMillis() < deadline) {
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        Thread.sleep(20)
      }
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      assertEquals(valid.rawMetar, vm.uiState.value.weather.rawMetar)
      assertTrue(vm.uiState.value.isOfflineCachedWeather)
    } finally {
      db.close()
    }
  }

  @Test
  fun `crosswind calculator dialog computes custom wind and resets back to live METAR`() {
    val vm = MeteoViewModel(autoFetch = false)
    vm.selectStation("UTAM") // 35L = 345 deg
    vm.applyCustomMetar(
      rawMetar = "METAR UTAM 270900Z 34510KT CAVOK 20/05 Q1018",
      rawTaf = "TAF UTAM",
    )

    composeTestRule.setContent {
      MeteoTabloTheme(isNightMode = false) {
        MeteoTabloScreen(viewModel = vm, onFullscreenChanged = {})
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("toolbar_crosswind_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("crosswind_calculator_dialog").assertIsDisplayed()

    // Enter pure 90-degree crosswind relative to 270° heading: set heading=270, windDir=360, windSpeed=20 kt
    composeTestRule.onNodeWithTag("custom_runway_heading_input").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("custom_runway_heading_input").performTextReplacement("270")
    composeTestRule.onNodeWithTag("custom_wind_dir_input").performScrollTo().performTextReplacement("360")
    composeTestRule.onNodeWithTag("custom_wind_speed_input").performScrollTo().performTextReplacement("20")
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("rwy_component_crosswind_box").assertTextContains("20.0 kt", substring = true)

    // Reset back to METAR wind (34510KT -> smaller crosswind against 270°)
    composeTestRule.onNodeWithTag("custom_wind_reset_metar_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("rwy_component_crosswind_box").assertTextContains("9.7 kt", substring = true)

    composeTestRule.onNodeWithTag("crosswind_dialog_close_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("crosswind_calculator_dialog").assertDoesNotExist()
  }

  @Test
  fun `toolbar share button opens Android native share chooser with METAR summary`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    Shadows.shadowOf(app).clearNextStartedActivities()

    val vm = MeteoViewModel(autoFetch = false)
    vm.selectStation("UTAA")
    vm.applyCustomMetar(
      rawMetar = "METAR UTAA 270900Z 12014KT CAVOK 24/08 Q1019 NOSIG",
      rawTaf = "TAF UTAA 270800Z 2709/2809 12012KT CAVOK",
    )

    composeTestRule.setContent {
      MeteoTabloTheme(isNightMode = false) {
        MeteoTabloScreen(viewModel = vm, onFullscreenChanged = {})
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("toolbar_share_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    val launched = Shadows.shadowOf(app).nextStartedActivity
    assertTrue("Expected ACTION_CHOOSER share intent", launched != null)
    assertEquals(android.content.Intent.ACTION_CHOOSER, launched!!.action)
    @Suppress("DEPRECATION")
    val innerIntent = launched.getParcelableExtra<android.content.Intent>(android.content.Intent.EXTRA_INTENT)
    assertTrue(innerIntent != null)
    assertEquals(android.content.Intent.ACTION_SEND, innerIntent!!.action)
    val sharedText = innerIntent.getStringExtra(android.content.Intent.EXTRA_TEXT).orEmpty()
    assertTrue(sharedText.contains("UTAA"))
    assertTrue(sharedText.contains("12014KT"))
  }
}

