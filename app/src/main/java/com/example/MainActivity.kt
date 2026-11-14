package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.SharedPreferencesFavoriteStorage
import com.example.data.SharedPreferencesServerConfigStorage
import com.example.ui.MeteoTabloScreen
import com.example.ui.theme.MeteoTabloTheme
import com.example.viewmodel.MeteoViewModel

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    setContent {
      val meteotabloViewModel: MeteoViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
          @Suppress("UNCHECKED_CAST")
          override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MeteoViewModel(
              favoriteStorage = SharedPreferencesFavoriteStorage(applicationContext),
              serverConfigStorage = SharedPreferencesServerConfigStorage(applicationContext),
            ) as T
          }
        },
      )
      val uiState by meteotabloViewModel.uiState.collectAsStateWithLifecycle()

      LaunchedEffect(uiState.isNightMode, uiState.isFullscreen) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = !uiState.isNightMode
        controller.isAppearanceLightNavigationBars = !uiState.isNightMode
        if (uiState.isFullscreen) {
          controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
          controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
          controller.show(WindowInsetsCompat.Type.systemBars())
        }
      }

      MeteoTabloTheme(isNightMode = uiState.isNightMode) {
        MeteoTabloScreen(
          viewModel = meteotabloViewModel,
          onFullscreenChanged = { },
        )
      }
    }
  }
}
