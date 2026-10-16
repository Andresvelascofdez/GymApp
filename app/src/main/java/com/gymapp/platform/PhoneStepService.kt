package com.gymapp.platform

import android.app.Service
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.ServiceCompat
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import com.gymapp.data.Store

class PhoneStepService : Service(), SensorEventListener {
    companion object { @Volatile var connected: Boolean = false; private set }
    private lateinit var prefs: PlatformPrefs
    private lateinit var sensors: SensorManager
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        prefs = PlatformPrefs(this)
        sensors = getSystemService(SensorManager::class.java)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (prefs.sessionId == null || prefs.source != "sensor") { stopSelf(); return START_NOT_STICKY }
        if (connected) return START_STICKY
        try {
            ServiceCompat.startForeground(this, Notifications.SENSOR_ID, Notifications.sensor(this),
                if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH else 0)
            val sensor = sensors.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
            if (sensor == null || !sensors.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)) {
                prefs.status("Sensor de pasos no disponible"); stopSelf(); return START_NOT_STICKY
            }
            connected = true
            prefs.status(if (prefs.preferences.getBoolean("sensor_gap", false)) "Intervalo sin verificar tras reinicio: conteo parcial" else "Esperando lectura inicial del sensor")
        } catch (_: SecurityException) {
            prefs.status("Permiso de actividad retirado"); stopSelf()
        } catch (_: RuntimeException) {
            prefs.status("No se pudo iniciar el servicio del sensor"); stopSelf()
        }
        return START_STICKY
    }
    override fun onSensorChanged(event: SensorEvent) {
        if (prefs.sessionId == null || prefs.source != "sensor") { stopSelf(); return }
        val measuredAt = System.currentTimeMillis() - (SystemClock.elapsedRealtimeNanos() - event.timestamp) / 1_000_000L
        if (measuredAt < prefs.start) return
        val rawValue = event.values.firstOrNull()?.takeIf { it.isFinite() } ?: return
        val raw = rawValue.toLong()
        if (raw < 0) return
        val boot = Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, -1)
        val store = Store.get(this)
        val session = store.state.value.activeSession ?: return
        if (session.id != prefs.sessionId || session.steps.source != "sensor") return
        val reset = session.steps.baseline != null && (session.steps.bootId != boot.toString() || raw < (session.steps.lastRaw ?: raw))
        val gap = prefs.preferences.getBoolean("sensor_gap", false) || reset
        store.sensorReading(session.id, raw, boot.toString(), System.currentTimeMillis())
        val updated = store.state.value.activeSession?.steps ?: return
        prefs.preferences.edit().putLong("steps", updated.count).putLong("last_read", updated.lastReadAt ?: System.currentTimeMillis())
            .putBoolean("sensor_gap", gap).putString("status", if (gap) "Conteo parcial: intervalo sin verificar tras reinicio/reset del sensor" else updated.status).commit()
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    override fun onDestroy() {
        connected = false
        sensors.unregisterListener(this)
        if (prefs.sessionId != null && prefs.source == "sensor") prefs.status("Servicio de pasos detenido; última lectura conservada")
        super.onDestroy()
    }
}
