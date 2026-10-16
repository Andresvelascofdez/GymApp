package com.gymapp.platform

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.provider.Settings
import android.telecom.TelecomManager
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.lang.ref.WeakReference

object FocusPolicy {
    fun isAllowed(packageName: String, ownPackage: String, essentialPackages: Set<String>): Boolean =
        packageName == ownPackage || packageName == "com.spotify.music" || packageName in essentialPackages
}

class FocusAccessibilityService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {
    companion object {
        @Volatile var connected = false; private set
        private var service: WeakReference<FocusAccessibilityService>? = null
        fun dismissBarrier() { service?.get()?.let { it.mainExecutor.execute { it.hideBarrier() } } }
    }
    private lateinit var prefs: PlatformPrefs
    private lateinit var windows: WindowManager
    private var barrier: View? = null
    private val essentials = mutableSetOf(
        "android", "com.android.systemui", "com.android.settings", "com.android.permissioncontroller",
        "com.google.android.permissioncontroller", "com.android.packageinstaller", "com.google.android.packageinstaller",
        "com.android.phone", "com.android.server.telecom", "com.android.emergency", "com.google.android.apps.safetyhub",
        "com.android.dialer", "com.google.android.dialer", "com.miui.securitycenter", "com.miui.securityadd",
        "com.google.android.apps.healthdata", "com.android.healthconnect.controller", "com.google.android.healthconnect.controller",
    )
    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = PlatformPrefs(this)
        windows = getSystemService(WindowManager::class.java)
        service = WeakReference(this)
        connected = true
        prefs.preferences.registerOnSharedPreferenceChangeListener(this)
        packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)?.activityInfo?.packageName?.let(essentials::add)
        getSystemService(TelecomManager::class.java)?.defaultDialerPackage?.let(essentials::add)
        Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.substringBefore('/')?.let(essentials::add)
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!::prefs.isInitialized || event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (!prefs.focus || getSystemService(KeyguardManager::class.java).isKeyguardLocked) { hideBarrier(); return }
        val packageName = event.packageName?.toString() ?: return
        if (packageName == this.packageName && barrier != null) return
        if (FocusPolicy.isAllowed(packageName, this.packageName, essentials)) hideBarrier() else showBarrier()
    }
    private fun showBarrier() {
        if (barrier != null) return
        val scale = resources.displayMetrics.density
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding((28 * scale).toInt(), (32 * scale).toInt(), (28 * scale).toInt(), (32 * scale).toInt())
            setBackgroundColor(Color.rgb(15, 20, 25))
            addView(TextView(context).apply {
                text = "Vuelve a tu entrenamiento"
                textSize = 27f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            })
            addView(TextView(context).apply {
                text = "Concentración voluntaria activa. Spotify sigue disponible. Puedes revocar Accesibilidad en Ajustes."
                textSize = 17f; setTextColor(Color.LTGRAY); gravity = Gravity.CENTER
                setPadding(0, (20 * scale).toInt(), 0, (24 * scale).toInt())
            })
            addView(Button(context).apply {
                text = "Volver al entrenamiento"
                setOnClickListener { hideBarrier(); openApp() }
            })
            addView(Button(context).apply {
                text = "Spotify"
                setOnClickListener { if (PlatformController(this@FocusAccessibilityService).openSpotify()) hideBarrier() }
            })
            addView(Button(context).apply {
                text = "Emergencia: salir ahora"
                setOnClickListener {
                    PlatformController(this@FocusAccessibilityService).emergencyExit()
                    hideBarrier(); openApp()
                }
            })
            addView(Button(context).apply {
                text = "Ajustes de Accesibilidad"
                setOnClickListener { hideBarrier(); PlatformController(this@FocusAccessibilityService).openAccessibilitySettings() }
            })
        }
        try {
            windows.addView(content, WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ))
            barrier = content
        } catch (_: RuntimeException) { hideBarrier() }
    }
    private fun openApp() {
        packageManager.getLaunchIntentForPackage(packageName)?.let {
            startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        }
    }
    private fun hideBarrier() {
        barrier?.let { runCatching { windows.removeView(it) } }
        barrier = null
    }
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (!prefs.focus) mainExecutor.execute { hideBarrier() }
    }
    override fun onInterrupt() { hideBarrier() }
    override fun onDestroy() {
        connected = false
        if (::prefs.isInitialized) prefs.preferences.unregisterOnSharedPreferenceChangeListener(this)
        hideBarrier(); service = null
        super.onDestroy()
    }
}
