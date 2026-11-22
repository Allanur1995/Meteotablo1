package com.example

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.DataSourceMode
import com.example.data.ServerConfig
import com.example.data.SharedPreferencesFavoriteStorage
import com.example.data.SharedPreferencesServerConfigStorage
import com.example.data.SharedPreferencesWeatherAlertStorage
import com.example.notifications.AndroidWeatherNotifier
import com.example.ui.MeteoTabloScreen
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
    assertEquals("http://217.174.229.206:8181/", initial.url)
    assertEquals("meteo", initial.username)
    assertEquals("meteo", initial.password)

    storage1.saveConfig(
      ServerConfig(
        url = "217.174.229.206:8181",
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
    assertEquals("http://217.174.229.206:8181/", reloaded.url)
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
    composeTestRule.onNodeWithTag("metar_row_qnh").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("30,21 дм", substring = true).assertIsDisplayed()
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
}
