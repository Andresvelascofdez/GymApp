package com.gymapp.platform

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.gymapp.data.Store
import com.gymapp.domain.SessionStatus
import kotlinx.coroutines.CancellationException
import java.time.Instant

data class StepReading(
    val count: Long?,
    val status: String,
    val lastReadEpochMillis: Long?,
    val origins: Set<String> = emptySet(),
    val source: String = "health_connect",
)

data class PermissionStatus(
    val accessibilityEnabled: Boolean,
    val focusServiceConnected: Boolean,
    val activityRecognition: Boolean,
    val notifications: Boolean,
    val healthAvailable: Boolean,
    val phoneSensor: Boolean,
    val spotifyInstalled: Boolean,
)

internal class PlatformPrefs(private val context: Context) {
    val preferences = context.getSharedPreferences("platform", Context.MODE_PRIVATE)
    val sessionId get() = preferences.getString("session", null)
    val source get() = preferences.getString("source", "health_connect") ?: "health_connect"
    val start get() = preferences.getLong("start", 0)
    val focus get() = preferences.getBoolean("focus", false) && Store.get(context).state.value.activeSession?.let {
        it.active && it.id == sessionId && it.focusRequested
    } == true
    fun reading(): StepReading = StepReading(
        if (preferences.contains("steps")) preferences.getLong("steps", 0) else null,
        preferences.getString("status", "Pendiente de sincronizar") ?: "Pendiente de sincronizar",
        preferences.getLong("last_read", 0).takeIf { it > 0 },
        preferences.getStringSet("origins", emptySet()) ?: emptySet(), source,
    )
    fun status(value: String) {
        val id = sessionId ?: return
        val store = Store.get(context)
        val existing = store.state.value.activeSession?.steps
        val preserveGap = source == "sensor" && (preferences.getBoolean("sensor_gap", false) || existing?.status?.contains("sin verificar") == true)
        val status = if (preserveGap && !value.contains("sin verificar")) "$value · intervalo sin verificar tras reinicio" else value
        preferences.edit().putString("status", status).commit()
        store.update { state ->
            val session = state.activeSession
            if (session?.id == id && session.steps.status != status) state.copy(activeSession = session.copy(steps = session.steps.copy(status = status))) else state
        }
    }
}

class PlatformController(context: Context) {
    private val context = context.applicationContext
    private val prefs = PlatformPrefs(this.context)
    val healthPermissions = setOf(HealthPermission.getReadPermission(StepsRecord::class))
    fun healthPermissionsContract(): ActivityResultContract<Set<String>, Set<String>> =
        PermissionController.createRequestPermissionResultContract()

    fun syncSession(id: String?, startEpochMillis: Long = 0, source: String = "health_connect", focus: Boolean = false, weightsDone: Boolean = false) {
        require(source in setOf("health_connect", "sensor", "none"))
        val previous = prefs.sessionId
        val weightsChanged = prefs.preferences.getBoolean("weights_done", false) != weightsDone
        val edit = prefs.preferences.edit()
        if (id != previous) {
            stopPhoneTracking()
            edit.remove("steps").remove("baseline").remove("last_counter").remove("boot")
                .remove("last_read").remove("origins").remove("emergency")
                .putBoolean("sensor_gap", false).putString("status", "Pendiente de sincronizar")
            Store.get(context).state.value.activeSession?.takeIf { it.id == id }?.steps?.let { steps ->
                if (steps.lastReadAt != null) edit.putLong("steps", steps.count).putLong("last_read", steps.lastReadAt)
                edit.putString("status", steps.status)
            }
        } else if (id != null) {
            if (prefs.source != source) {
                stopPhoneTracking()
                prefs.status("Conflicto de fuente tras restauración: termina por fallo para iniciar una sesión con la fuente elegida")
                return
            }
        }
        edit.putString("session", id).putLong("start", startEpochMillis).putString("source", source)
            .putBoolean("focus", focus && id != null && !prefs.preferences.getBoolean("emergency", false))
            .putBoolean("weights_done", weightsDone).commit()
        if (id == null) FocusAccessibilityService.dismissBarrier()
        if (id != previous || weightsChanged) {
            Notifications.cancelAbsence(context)
            Reminders.schedule(context)
        }
    }

    fun stepReading(): StepReading {
        if (prefs.source == "none") {
            val status = "Sin fuente de pasos seleccionada; cambia para futuras sesiones o usa salida por fallo"
            prefs.status(status)
            return StepReading(null, status, null, source = "none")
        }
        if (prefs.source == "sensor" && !permissionStatus().activityRecognition) {
            stopPhoneTracking()
            prefs.status("Permiso de actividad retirado; última lectura conservada")
        }
        if (prefs.source == "sensor" && prefs.sessionId != null && !PhoneStepService.connected) {
            if (permissionStatus().activityRecognition) prefs.status("Sensor detenido: reanuda desde la app")
        }
        val reading = prefs.reading()
        val stored = Store.get(context).state.value.activeSession?.takeIf { it.id == prefs.sessionId }?.steps
        return if (prefs.source == "sensor" && stored != null) reading.copy(count = stored.count.takeIf { stored.lastReadAt != null }, lastReadEpochMillis = stored.lastReadAt) else reading
    }

    suspend fun refreshHealth(): StepReading {
        val id = prefs.sessionId ?: return StepReading(null, "Sin sesión activa", null)
        if (prefs.source != "health_connect") return stepReading()
        if (HealthConnectClient.getSdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) {
            prefs.status("Health Connect no disponible o pendiente de actualizar")
            return prefs.reading()
        }
        try {
            val client = HealthConnectClient.getOrCreate(context)
            if (!client.permissionController.getGrantedPermissions().containsAll(healthPermissions)) {
                prefs.status("Permiso de pasos de Health Connect no concedido")
                return prefs.reading()
            }
            val start = Instant.ofEpochMilli(prefs.start)
            val now = Instant.now()
            if (!start.isBefore(now)) return prefs.reading()
            val result = client.aggregate(AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(start, now),
            ))
            if (prefs.sessionId != id || prefs.source != "health_connect") return prefs.reading()
            val total = result[StepsRecord.COUNT_TOTAL]
            prefs.preferences.edit().apply {
                if (total != null) {
                    putLong("steps", total.coerceAtLeast(0))
                    putLong("last_read", now.toEpochMilli())
                } else {
                    remove("steps")
                    remove("last_read")
                }
                putLong("last_query", now.toEpochMilli())
                putStringSet("origins", result.dataOrigins.map { it.packageName }.toSet())
                putString("status", if (total == null) "Sin registros; pendiente de sincronizar" else "Agregado deduplicado de Health Connect; puede llegar con retraso")
            }.commit()
            if (total != null) Store.get(context).updateSessionSteps(id, total.coerceAtLeast(0), now.toEpochMilli(), prefs.reading().status, "health_connect")
            else Store.get(context).update { state ->
                val session = state.activeSession
                if (session?.id == id && session.steps.source == "health_connect") state.copy(activeSession = session.copy(steps = session.steps.copy(lastReadAt = null, status = prefs.reading().status))) else state
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: SecurityException) {
            prefs.status("Permiso de Health Connect retirado")
        } catch (_: Exception) {
            prefs.status("No se pudo sincronizar; se conserva la última lectura")
        }
        return prefs.reading()
    }

    fun permissionStatus(): PermissionStatus {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.split(':')?.any { ComponentName.unflattenFromString(it) == ComponentName(context, FocusAccessibilityService::class.java) } == true
        return PermissionStatus(enabled, enabled && FocusAccessibilityService.connected,
            Build.VERSION.SDK_INT < 29 || ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED,
            NotificationManagerCompat.from(context).areNotificationsEnabled(),
            HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE,
            context.getSystemService(SensorManager::class.java).getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null,
            context.packageManager.getLaunchIntentForPackage("com.spotify.music") != null)
    }

    fun startPhoneTracking(): Boolean {
        if (prefs.sessionId == null || prefs.source != "sensor") return false
        val permissions = permissionStatus()
        if (!permissions.phoneSensor || !permissions.activityRecognition) {
            prefs.status(if (!permissions.phoneSensor) "Sensor de pasos no disponible" else "Permiso de actividad no concedido")
            return false
        }
        return try {
            ContextCompat.startForegroundService(context, Intent(context, PhoneStepService::class.java))
            true
        } catch (_: RuntimeException) {
            prefs.status("Android no permite iniciar el sensor: abre la app y reintenta")
            false
        }
    }

    fun stopPhoneTracking() { context.stopService(Intent(context, PhoneStepService::class.java)) }
    fun emergencyExit() {
        prefs.preferences.edit().putBoolean("focus", false).putBoolean("emergency", true).commit()
        FocusAccessibilityService.dismissBarrier()
        stopPhoneTracking()
        Notifications.cancelAbsence(context)
        val store = Store.get(context)
        if (store.state.value.activeSession != null) store.closeSession(SessionStatus.EMERGENCY_EXIT, "Salida directa de emergencia")
    }
    fun consumeEmergency(): Boolean {
        val pending = prefs.preferences.getBoolean("emergency", false)
        if (pending) prefs.preferences.edit().putBoolean("emergency", false).commit()
        return pending
    }
    fun openAccessibilitySettings() = open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    fun openNotificationSettings() = open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
    fun openHealthSettings() = open(if (Build.VERSION.SDK_INT >= 34)
        Intent("android.health.connect.action.HEALTH_HOME_SETTINGS") else
        Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS").setPackage("com.google.android.apps.healthdata"))
    fun openBatterySettings() = open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    fun openSpotify(): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage("com.spotify.music") ?: return false
        return open(intent)
    }
    private fun open(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (_: RuntimeException) {
        if (intent.action != Settings.ACTION_SETTINGS) try {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: RuntimeException) { }
        false
    }
    fun startRest(seconds: Int) = RestTimer.start(context, seconds)
    fun cancelRest() = RestTimer.cancel(context)
}
