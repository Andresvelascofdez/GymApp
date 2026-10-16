package com.gymapp.platform

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.gymapp.data.Store
import com.gymapp.domain.Rules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.TimeUnit

internal object Notifications {
    const val SENSOR_ID = 40
    const val REMINDER_ID = 41
    const val REST_ID = 42
    fun channels(context: Context) {
        context.getSystemService(NotificationManager::class.java).apply {
            createNotificationChannel(NotificationChannel("steps_service", "Sensor durante entrenamiento", NotificationManager.IMPORTANCE_LOW))
            createNotificationChannel(NotificationChannel("reminders", "Entrenamiento y pasos pendientes", NotificationManager.IMPORTANCE_DEFAULT))
            createNotificationChannel(NotificationChannel("rest", "Fin del descanso", NotificationManager.IMPORTANCE_DEFAULT).apply {
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250)
            })
        }
    }
    fun appIntent(context: Context, start: Boolean = false): PendingIntent {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent()
        intent.putExtra("gymapp.start_workout", start).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, if (start) 21 else 20, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    fun sensor(context: Context): Notification {
        channels(context)
        return NotificationCompat.Builder(context, "steps_service")
            .setSmallIcon(android.R.drawable.ic_menu_compass).setContentTitle("Pasos del entrenamiento")
            .setContentText("Sensor del teléfono activo. Abre GymApp para ver el estado.")
            .setContentIntent(appIntent(context)).setOngoing(true).setCategory(NotificationCompat.CATEGORY_SERVICE).build()
    }
    fun allowed(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()
    @SuppressLint("MissingPermission")
    fun reminder(context: Context, kind: String): Boolean {
        channels(context)
        if (!allowed(context)) return false
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel("reminders")?.importance == NotificationManager.IMPORTANCE_NONE) return false
        val snooze = PendingIntent.getBroadcast(context, 31, Intent(context, ReminderActionReceiver::class.java).setAction("com.gymapp.SNOOZE"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val messages = if (kind == "steps") listOf("Pesas guardadas. Revisa los pasos pendientes cuando te venga bien.", "Tu sesión sigue con pasos pendientes; comprueba la sincronización.")
            else listOf("Retoma la siguiente sesión pendiente. Empieza con lo que puedas hacer hoy.", "Llevas tres días completos sin pesas. Tu siguiente sesión está preparada.", "Hoy puedes volver a tu rutina. También tienes el modo «Hoy me cuesta».")
        val message = messages[((System.currentTimeMillis() / 3_600_000L) % messages.size).toInt()]
        try { manager.notify(REMINDER_ID, NotificationCompat.Builder(context, "reminders")
            .setSmallIcon(android.R.drawable.ic_menu_today).setContentTitle(if (kind == "steps") "Pasos pendientes" else "Tu entrenamiento te espera")
            .setContentText(message).setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(appIntent(context)).setAutoCancel(true)
            .addAction(0, "Empezar", appIntent(context, true)).addAction(0, "Posponer 30 min", snooze).build())
        } catch (_: SecurityException) { return false }
        return true
    }
    fun cancelAbsence(context: Context) { context.getSystemService(NotificationManager::class.java).cancel(REMINDER_ID) }
    @SuppressLint("MissingPermission")
    fun rest(context: Context) {
        channels(context)
        if (!allowed(context)) return
        try { context.getSystemService(NotificationManager::class.java).notify(REST_ID,
            NotificationCompat.Builder(context, "rest").setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Descanso terminado").setContentText("Continúa cuando estés preparado.")
                .setContentIntent(appIntent(context)).setAutoCancel(true).setCategory(NotificationCompat.CATEGORY_ALARM)
                .setDefaults(NotificationCompat.DEFAULT_VIBRATE).build())
        } catch (_: SecurityException) { }
    }
}

object Reminders {
    private const val NAME = "training-reminder"
    fun schedule(context: Context, after: Instant = Instant.now(), append: Boolean = false) {
        val state = Store.get(context).state.value
        val zone = ZoneId.systemDefault()
        if (!state.settings.reminderEnabled) { WorkManager.getInstance(context).cancelUniqueWork(NAME); return }
        var candidate = after
        for (minute in 0..11520) {
            if (Rules.reminderKind(state, candidate, zone) != null) break
            candidate = candidate.plusSeconds(60)
        }
        val delay = Duration.between(Instant.now(), candidate).toMillis().coerceAtLeast(1000)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork(NAME,
            if (append) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE, request)
    }
}

class ReminderWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val store = Store.get(applicationContext)
        val now = Instant.now()
        val kind = Rules.reminderKind(store.state.value, now, ZoneId.systemDefault())
        if (kind != null && Notifications.reminder(applicationContext, kind)) store.update {
            it.copy(settings = it.settings.copy(lastReminderAt = now.toEpochMilli()))
        }
        Reminders.schedule(applicationContext, now.plusSeconds(900), append = true)
        return Result.success()
    }
}

class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != "com.gymapp.SNOOZE") return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Store.get(context).update { it.copy(settings = it.settings.copy(snoozeUntil = System.currentTimeMillis() + 30 * 60_000L)) }
                Notifications.cancelAbsence(context)
                Reminders.schedule(context)
            } finally { pending.finish() }
        }
    }
}

class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)) return
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            PlatformPrefs(context).preferences.edit().putBoolean("sensor_gap", true)
                .putString("status", "Reinicio: abre GymApp para reanudar el sensor; existe un intervalo sin verificar").commit()
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch { try { Reminders.schedule(context) } finally { pending.finish() } }
    }
}

object RestTimer {
    private val handler = Handler(Looper.getMainLooper())
    fun start(context: Context, seconds: Int) {
        require(seconds in 1..7200)
        val token = UUID.randomUUID().toString()
        PlatformPrefs(context).preferences.edit().putString("rest_token", token).putLong("rest_end", System.currentTimeMillis() + seconds * 1000L).commit()
        val intent = Intent(context, RestReceiver::class.java).putExtra("token", token)
        val pending = PendingIntent.getBroadcast(context, 32, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + seconds * 1000L, pending)
        handler.postDelayed({ complete(context.applicationContext, token) }, seconds * 1000L)
    }
    fun cancel(context: Context) {
        PlatformPrefs(context).preferences.edit().remove("rest_token").remove("rest_end").commit()
        val pending = PendingIntent.getBroadcast(context, 32, Intent(context, RestReceiver::class.java), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        if (pending != null) context.getSystemService(AlarmManager::class.java).cancel(pending)
        context.getSystemService(NotificationManager::class.java).cancel(Notifications.REST_ID)
    }
    internal fun complete(context: Context, token: String) {
        val prefs = PlatformPrefs(context).preferences
        if (prefs.getString("rest_token", null) != token) return
        prefs.edit().remove("rest_token").remove("rest_end").commit()
        Notifications.rest(context)
    }
}

class RestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        intent?.getStringExtra("token")?.let { RestTimer.complete(context, it) }
    }
}
