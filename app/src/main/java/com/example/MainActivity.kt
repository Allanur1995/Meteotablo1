package com.example

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.SharedPreferencesFavoriteStorage
import com.example.data.SharedPreferencesServerConfigStorage
import com.example.data.SharedPreferencesUnitPreferencesStorage
import com.example.data.SharedPreferencesWeatherAlertStorage
import com.example.data.SharedPreferencesWeatherHistoryStorage
import com.example.data.SharedPreferencesWeatherPresetStorage
import com.example.data.SharedPreferencesWindHistoryStorage
import com.example.diagnostics.CrashReporter
import com.example.notifications.AndroidWeatherNotifier
import com.example.ui.MeteoTabloScreen
import com.example.ui.MeteotabloSplashScreen
import com.example.ui.theme.MeteoTabloTheme
import com.example.viewmodel.MeteoViewModel

class MainActivity : ComponentActivity() {
  private val pendingAlertIcao = mutableStateOf<String?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    CrashReporter.initialize(applicationContext)
    CrashReporter.setLaunchStage("main_activity_on_create")
    runCatching { enableEdgeToEdge() }
      .onFailure { CrashReporter.recordNonFatal("edge_to_edge", it) }
    pendingAlertIcao.value = intent?.getStringExtra(AndroidWeatherNotifier.EXTRA_ALERT_ICAO)

    setContent {
      CrashReporter.setLaunchStage("compose_set_content")
      val meteotabloViewModel: MeteoViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
          @Suppress("UNCHECKED_CAST")
          override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MeteoViewModel(
              favoriteStorage = SharedPreferencesFavoriteStorage(applicationContext),
              serverConfigStorage = SharedPreferencesServerConfigStorage(applicationContext),
              presetStorage = SharedPreferencesWeatherPresetStorage(applicationContext),
              alertStorage = SharedPreferencesWeatherAlertStorage(applicationContext),
              windHistoryStorage = SharedPreferencesWindHistoryStorage(applicationContext),
              unitPreferencesStorage = SharedPreferencesUnitPreferencesStorage(applicationContext),
              weatherNotifier = AndroidWeatherNotifier(applicationContext),
              weatherHistoryStorage = SharedPreferencesWeatherHistoryStorage(applicationContext),
            ) as T
          }
        },
      )
      val uiState by meteotabloViewModel.uiState.collectAsStateWithLifecycle()
      var showSplash by rememberSaveable { mutableStateOf(true) }
      var hasPromptedNotification by rememberSaveable { mutableStateOf(false) }
      val requestedIcao by pendingAlertIcao

      val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
      ) { _ -> }

      LaunchedEffect(showSplash, uiState.alertConfig.enabled) {
        if (!showSplash &&
          !hasPromptedNotification &&
          Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
          uiState.alertConfig.enabled &&
          !AndroidWeatherNotifier.hasNotificationPermission(applicationContext)
        ) {
          hasPromptedNotification = true
          runCatching {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
          }.onFailure {
            CrashReporter.recordNonFatal("notification_permission_prompt", it)
          }
        }
      }

      LaunchedEffect(requestedIcao) {
        val target = requestedIcao
        if (!target.isNullOrBlank()) {
          meteotabloViewModel.openAlertStation(target)
          pendingAlertIcao.value = null
        }
      }

      LaunchedEffect(uiState.isNightMode, uiState.isFullscreen, showSplash) {
        runCatching {
          val controller = WindowCompat.getInsetsController(window, window.decorView)
          val useDarkBarContent = !uiState.isNightMode && !showSplash
          controller.isAppearanceLightStatusBars = useDarkBarContent
          controller.isAppearanceLightNavigationBars = useDarkBarContent
          if (uiState.isFullscreen && !showSplash) {
            controller.systemBarsBehavior =
              WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
          } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
          }
        }.onFailure {
          CrashReporter.recordNonFatal("window_insets_controller", it)
        }
      }

      MeteoTabloTheme(isNightMode = uiState.isNightMode) {
        MeteotabloAppHost(
          viewModel = meteotabloViewModel,
          showSplash = showSplash,
          onSplashFinished = {
            showSplash = false
            CrashReporter.setLaunchStage("dashboard_active")
          },
        )
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    intent.getStringExtra(AndroidWeatherNotifier.EXTRA_ALERT_ICAO)?.let { icao ->
      CrashReporter.log("MainActivity.onNewIntent alertIcao=$icao")
      pendingAlertIcao.value = icao
    }
  }
}

@Composable
fun MeteotabloAppHost(
  viewModel: MeteoViewModel,
  showSplash: Boolean,
  onSplashFinished: () -> Unit,
  splashDurationMillis: Long = 1450L,
) {
  Box(modifier = Modifier.fillMaxSize()) {
    MeteoTabloScreen(
      viewModel = viewModel,
      onFullscreenChanged = { },
    )

    AnimatedVisibility(
      visible = showSplash,
      exit = fadeOut(animationSpec = tween(durationMillis = 320)) +
        scaleOut(targetScale = 1.04f, animationSpec = tween(durationMillis = 320)),
    ) {
      MeteotabloSplashScreen(
        durationMillis = splashDurationMillis,
        onFinished = onSplashFinished,
      )
    }
  }
}
