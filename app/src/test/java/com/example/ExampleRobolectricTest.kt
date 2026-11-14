package com.example

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
import com.example.data.ServerConfig
import com.example.data.SharedPreferencesFavoriteStorage
import com.example.data.SharedPreferencesServerConfigStorage
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
      ),
    )

    val storage2 = SharedPreferencesServerConfigStorage(context)
    val reloaded = storage2.loadConfig()
    assertEquals("http://217.174.229.206:8181/", reloaded.url)
    assertEquals("secret", reloaded.password)
    assertFalse(reloaded.fallbackToNoaa)
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
    composeTestRule.onNodeWithTag("wind_center_box_35L").assertExists()
    composeTestRule.onNodeWithTag("taf_footer_row").assertExists()
    composeTestRule.onNodeWithTag("metar_footer_row").assertExists()

    composeTestRule.onNodeWithTag("toolbar_wind_unit_toggle").assertTextContains("kt")
    composeTestRule.onNodeWithTag("toolbar_pressure_unit_toggle").assertTextContains("гПа")
    composeTestRule.onNodeWithTag("toolbar_pressure_unit_toggle").performClick()
    composeTestRule.onNodeWithTag("toolbar_pressure_unit_toggle").assertTextContains("дм")
    composeTestRule.onNodeWithTag("toolbar_pressure_unit_toggle").performClick()
    composeTestRule.onNodeWithTag("toolbar_pressure_unit_toggle").assertTextContains("гПа")

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
}
