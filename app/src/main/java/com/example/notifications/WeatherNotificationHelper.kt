package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.AirportCatalog
import com.example.data.AviationWeatherRepository
import com.example.data.SharedPreferencesFavoriteStorage
import com.example.data.SharedPreferencesServerConfigStorage
import com.example.data.SharedPreferencesWeatherAlertStorage
import com.example.diagnostics.CrashReporter
import com.example.domain.WeatherChangeDetector
import com.example.model.WeatherAlertSeverity
import com.example.model.WeatherChangeAlert
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

interface LocalWeatherNotifier {
  fun notify(alert: WeatherChangeAlert)
  fun syncBackgroundSchedule(enabled: Boolean, intervalMinutes: Int, hasFavorites: Boolean)
}

object NoOpWeatherNotifier : LocalWeatherNotifier {
  override fun notify(alert: WeatherChangeAlert) {}
  override fun syncBackgroundSchedule(enabled: Boolean, intervalMinutes: Int, hasFavorites: Boolean) {}
}

class AndroidWeatherNotifier(
  context: Context,
) : LocalWeatherNotifier {

  private val appContext = context.applicationContext

  init {
    ensureNotificationChannel(appContext)
  }

  override fun notify(alert: WeatherChangeAlert) {
    runCatching {
      if (!hasNotificationPermission(appContext)) return
      ensureNotificationChannel(appContext)

      val launchIntent = Intent(appContext, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(EXTRA_ALERT_ICAO, alert.icao)
      }
      val pendingIntent = PendingIntent.getActivity(
        appContext,
        abs(alert.icao.hashCode()),
        launchIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

      val priority = when (alert.severity) {
        WeatherAlertSeverity.CRITICAL -> NotificationCompat.PRIORITY_HIGH
        WeatherAlertSeverity.WARNING -> NotificationCompat.PRIORITY_DEFAULT
        WeatherAlertSeverity.INFO -> NotificationCompat.PRIORITY_LOW
      }

      val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_stat_weather_alert)
        .setContentTitle("${alert.icao} · ${alert.observationTimeUtc} (${alert.severity.label})")
        .setContentText(alert.summary)
        .setStyle(
          NotificationCompat.BigTextStyle()
            .setBigContentTitle(alert.title)
            .bigText("${alert.summary}\n${alert.rawMetar}"),
        )
        .setCategory(NotificationCompat.CATEGORY_STATUS)
        .setPriority(priority)
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)
        .build()

      val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
      manager.notify(notificationIdForIcao(alert.icao), notification)
    }.onFailure { err ->
      CrashReporter.recordNonFatal("weather_notification_post", err, mapOf("icao" to alert.icao))
    }
  }

  override fun syncBackgroundSchedule(
    enabled: Boolean,
    intervalMinutes: Int,
    hasFavorites: Boolean,
  ) {
    runCatching {
      val scheduler = appContext.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
      if (!enabled || !hasFavorites) {
        scheduler.cancel(JOB_ID)
        return
      }
      val intervalMs = (intervalMinutes.coerceAtLeast(15)) * 60_000L
      val existing = scheduler.allPendingJobs.firstOrNull { it.id == JOB_ID }
      if (existing != null && existing.intervalMillis == intervalMs) {
        return
      }
      val info = JobInfo.Builder(
        JOB_ID,
        ComponentName(appContext, FavoriteWeatherJobService::class.java),
      )
        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
        .setPeriodic(intervalMs)
        .setPersisted(false)
        .build()
      scheduler.schedule(info)
    }.onFailure { err ->
      CrashReporter.recordNonFatal("weather_job_schedule", err)
    }
  }

  companion object {
    const val CHANNEL_ID = "meteotablo_weather_alerts"
    const val EXTRA_ALERT_ICAO = "extra_alert_icao"
    private const val JOB_ID = 7104

    fun notificationIdForIcao(icao: String): Int = 5000 + abs(icao.uppercase().hashCode() % 10000)

    fun hasNotificationPermission(context: Context): Boolean {
      return runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
          ) == PackageManager.PERMISSION_GRANTED
        } else {
          true
        }
      }.getOrDefault(false)
    }

    fun ensureNotificationChannel(context: Context) {
      runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
          val existing = manager.getNotificationChannel(CHANNEL_ID)
          if (existing == null) {
            val channel = NotificationChannel(
              CHANNEL_ID,
              "Метеооповещения избранных аэродромов",
              NotificationManager.IMPORTANCE_HIGH,
            ).apply {
              description = "Локальные уведомления об опасных и значимых изменениях погоды на избранных аэродромах"
              enableVibration(true)
            }
            manager.createNotificationChannel(channel)
          }
        }
      }
    }
  }
}

class FavoriteWeatherJobService : JobService() {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private var activeJob: Job? = null

  override fun onStartJob(params: JobParameters?): Boolean {
    activeJob = scope.launch {
      var shouldReschedule = false
      try {
        val favStorage = SharedPreferencesFavoriteStorage(applicationContext)
        val serverStorage = SharedPreferencesServerConfigStorage(applicationContext)
        val alertStorage = SharedPreferencesWeatherAlertStorage(applicationContext)
        val config = alertStorage.loadConfig()
        val favorites = favStorage.loadFavorites()

        if (!config.enabled || favorites.isEmpty()) {
          runCatching { jobFinished(params, false) }
          return@launch
        }

        val repository = AviationWeatherRepository()
        val serverConfig = serverStorage.loadConfig()
        val notifier = AndroidWeatherNotifier(applicationContext)
        val snapshots = alertStorage.loadSnapshots().toMutableMap()
        val alerts = alertStorage.loadAlerts().toMutableList()

        favorites.forEach { icao ->
          val station = AirportCatalog.findOrDefault(icao)
          repository.fetchLiveWeather(station, serverConfig).onSuccess { fetched ->
            val result = WeatherChangeDetector.evaluate(
              station = fetched.resolvedStation,
              previous = snapshots[icao],
              current = fetched.weather,
              config = config,
            )
            result.snapshot?.let { snapshots[icao] = it }
            result.alert?.let { alert ->
              if (alerts.none { it.id == alert.id }) {
                alerts.add(0, alert)
                notifier.notify(alert)
              }
            }
          }
        }

        alertStorage.saveSnapshots(snapshots)
        alertStorage.saveAlerts(alerts.take(40))
      } catch (ce: CancellationException) {
        throw ce
      } catch (e: Exception) {
        shouldReschedule = true
        CrashReporter.recordNonFatal("favorite_weather_job", e)
      } finally {
        runCatching { jobFinished(params, shouldReschedule) }
          .onFailure { CrashReporter.recordNonFatal("favorite_weather_job_finish", it) }
      }
    }
    return true
  }

  override fun onStopJob(params: JobParameters?): Boolean {
    activeJob?.cancel()
    return true
  }

  override fun onDestroy() {
    scope.cancel()
    super.onDestroy()
  }
}
