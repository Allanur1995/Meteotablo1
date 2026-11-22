package com.example.diagnostics

import android.content.Context
import android.os.Build
import com.example.BuildConfig
import com.example.model.AuthorBrand
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.crashlytics.crashlytics
import com.google.firebase.crashlytics.recordException
import com.google.firebase.crashlytics.setCustomKeys
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException

data class CrashDiagnosticsSnapshot(
  val isCrashlyticsActive: Boolean,
  val currentLaunchStage: String,
  val breadcrumbCount: Int,
  val lastCrashSummary: String?,
)

object CrashReporter {
  private const val PREFS_NAME = "meteotablo_crash_diag"
  private const val KEY_LAST_CRASH = "last_uncaught_crash"
  private const val MAX_BREADCRUMBS = 40

  private val initialized = AtomicBoolean(false)
  private val lock = Any()
  private val breadcrumbs = ArrayDeque<String>()

  @Volatile
  private var appContext: Context? = null

  @Volatile
  private var currentStage: String = "pre_init"

  @Volatile
  private var crashlyticsActive: Boolean = false

  fun initialize(context: Context) {
    val appCtx = context.applicationContext ?: context
    appContext = appCtx
    if (!initialized.compareAndSet(false, true)) {
      return
    }

    val firebaseReady = runCatching {
      if (FirebaseApp.getApps(appCtx).isEmpty()) {
        FirebaseApp.initializeApp(appCtx) != null
      } else {
        true
      }
    }.getOrDefault(false)

    val crashlytics = if (firebaseReady) {
      runCatching { Firebase.crashlytics }.getOrNull()
    } else {
      null
    }

    crashlyticsActive = crashlytics != null
    crashlytics?.let { instance ->
      runCatching {
        instance.setCrashlyticsCollectionEnabled(true)
        instance.setCustomKeys {
          key("app_name", AuthorBrand.APP_TITLE)
          key("app_version", BuildConfig.VERSION_NAME)
          key("version_code", BuildConfig.VERSION_CODE)
          key("build_type", BuildConfig.BUILD_TYPE)
          key("package_name", appCtx.packageName)
          key("android_sdk_int", Build.VERSION.SDK_INT)
          key("device_manufacturer", Build.MANUFACTURER)
          key("device_model", Build.MODEL)
          key("supported_abis", Build.SUPPORTED_ABIS.joinToString(","))
          key("launch_stage", "application_on_create")
        }
      }
    }

    installUncaughtExceptionHook(appCtx)
    setLaunchStage("application_on_create")
    log("Crashlytics initialized (active=$crashlyticsActive, sdk=${Build.VERSION.SDK_INT})")
  }

  fun setLaunchStage(stage: String) {
    currentStage = stage
    log("STAGE: $stage")
    crashlyticsOrNull()?.let { instance ->
      runCatching { instance.setCustomKey("launch_stage", stage) }
    }
  }

  fun setStationContext(
    icao: String,
    runwayLabel: String,
    sourceMode: String,
    favoriteCount: Int,
  ) {
    crashlyticsOrNull()?.let { instance ->
      runCatching {
        instance.setCustomKeys {
          key("active_icao", icao)
          key("active_runway", runwayLabel)
          key("source_mode", sourceMode)
          key("favorite_count", favoriteCount)
        }
      }
    }
  }

  fun log(message: String) {
    val clean = message.trim().take(240)
    if (clean.isEmpty()) return
    synchronized(lock) {
      if (breadcrumbs.size >= MAX_BREADCRUMBS) {
        breadcrumbs.removeFirst()
      }
      breadcrumbs.addLast(clean)
    }
    crashlyticsOrNull()?.let { instance ->
      runCatching { instance.log(clean) }
    }
  }

  fun recordNonFatal(
    tag: String,
    throwable: Throwable,
    extras: Map<String, String> = emptyMap(),
  ) {
    if (throwable is CancellationException) return
    val summary = "${throwable::class.java.simpleName}: ${throwable.message ?: "no message"}"
    log("NON_FATAL[$tag]: $summary")
    crashlyticsOrNull()?.let { instance ->
      runCatching {
        instance.recordException(throwable) {
          key("error_tag", tag)
          key("launch_stage", currentStage)
          extras.forEach { (k, v) -> key(k, v.take(120)) }
        }
      }
    }
  }

  fun getDiagnosticsSnapshot(context: Context? = appContext): CrashDiagnosticsSnapshot {
    val ctx = context?.applicationContext ?: appContext
    val lastCrash = ctx?.let {
      runCatching {
        it.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
          .getString(KEY_LAST_CRASH, null)
      }.getOrNull()
    }
    val count = synchronized(lock) { breadcrumbs.size }
    return CrashDiagnosticsSnapshot(
      isCrashlyticsActive = crashlyticsActive,
      currentLaunchStage = currentStage,
      breadcrumbCount = count,
      lastCrashSummary = lastCrash,
    )
  }

  fun clearLastCrashSummary(context: Context? = appContext) {
    val ctx = context?.applicationContext ?: appContext ?: return
    runCatching {
      ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .remove(KEY_LAST_CRASH)
        .apply()
    }
  }

  internal fun recentBreadcrumbs(): List<String> = synchronized(lock) { breadcrumbs.toList() }

  private fun installUncaughtExceptionHook(context: Context) {
    val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
    if (previousHandler is MeteotabloExceptionHandler) return
    Thread.setDefaultUncaughtExceptionHandler(
      MeteotabloExceptionHandler(
        appContext = context,
        delegate = previousHandler,
      ),
    )
  }

  private fun crashlyticsOrNull(): FirebaseCrashlytics? {
    if (!crashlyticsActive) return null
    return runCatching { Firebase.crashlytics }.getOrNull()
  }

  private class MeteotabloExceptionHandler(
    private val appContext: Context,
    private val delegate: Thread.UncaughtExceptionHandler?,
  ) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(thread: Thread, throwable: Throwable) {
      try {
        val summary = "stage=$currentStage | thread=${thread.name} | " +
          "${throwable::class.java.simpleName}: ${throwable.message ?: "no message"}"
        runCatching {
          appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_CRASH, summary.take(300))
            .commit()
        }
        crashlyticsOrNull()?.let { instance ->
          runCatching {
            instance.setCustomKeys {
              key("fatal_thread", thread.name)
              key("fatal_exception_class", throwable::class.java.name)
              key("last_launch_stage", currentStage)
            }
            instance.log("FATAL_UNCAUGHT: $summary")
          }
        }
      } finally {
        delegate?.uncaughtException(thread, throwable)
      }
    }
  }
}
