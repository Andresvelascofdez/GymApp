package com.gymapp.platform

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.UiAutomation
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gymapp.data.Store
import com.gymapp.domain.SessionStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

class RestrictedTestActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply { text = "Synthetic restricted app"; textSize = 24f })
    }
}

@RunWith(AndroidJUnit4::class)
class PlatformIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).bufferedReader().use { it.readText() }
    private fun waitUntil(message: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 12_000
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(100)
        }
        assertTrue(message, condition())
    }
    private fun nodeWithText(text: String): AccessibilityNodeInfo? {
        fun find(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.text?.toString() == text) return node
            for (index in 0 until node.childCount) find(node.getChild(index))?.let { return it }
            return null
        }
        automation.windows.forEach { find(it.root)?.let { result -> return result } }
        return find(automation.rootInActiveWindow)
    }

    @Test fun staleTransportCannotReactivateFocusAfterPersistedSessionCloses() {
        val store = Store.get(context)
        val original = store.state.value
        val controller = PlatformController(context)
        val transport = PlatformPrefs(context)
        try {
            store.update { it.copy(activeSession = null, settings = it.settings.copy(focusEnabled = true)) }
            transport.preferences.edit().putString("session", "synthetic_stale_session").putBoolean("focus", true).commit()
            assertFalse(transport.focus)
            store.startSession()
            assertFalse(transport.focus)
            val session = requireNotNull(store.state.value.activeSession)
            controller.syncSession(session.id, session.startedAt, session.steps.source, focus = true)
            assertTrue(transport.focus)
            store.closeSession(SessionStatus.SENSOR_UNVERIFIED, "Synthetic interrupted transport update")
            assertFalse(transport.focus)
        } finally {
            controller.syncSession(null)
            store.update { original }
        }
    }

    @Test fun voluntaryBarrierPermitsSettingsAndEmergencyWithoutReadingAppContent() {
        val store = Store.get(context)
        val original = store.state.value
        val prefs = context.getSharedPreferences("platform", 0)
        val previousEnabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        val previousAccessibility = Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
        val component = ComponentName(context, FocusAccessibilityService::class.java).flattenToString()
        val controller = PlatformController(context)
        try {
            val info = automation.serviceInfo
            info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            automation.serviceInfo = info
            val services = listOfNotNull(previousEnabled?.takeIf { it.isNotBlank() }, component).joinToString(":")
            shell("settings put secure enabled_accessibility_services $services")
            shell("settings put secure accessibility_enabled 1")
            waitUntil("Accessibility service did not connect") { controller.permissionStatus().focusServiceConnected }
            store.update { it.copy(activeSession = null, settings = it.settings.copy(stepSource = "none", focusEnabled = true)) }
            store.startSession()
            val session = requireNotNull(store.state.value.activeSession)
            controller.syncSession(session.id, session.startedAt, "none", focus = true)
            shell("am start -W -n ${instrumentation.context.packageName}/com.gymapp.platform.RestrictedTestActivity")
            waitUntil("Restricted synthetic app did not receive a barrier") { nodeWithText("Vuelve a tu entrenamiento") != null }
            shell("am start -W -a android.settings.SETTINGS")
            waitUntil("Settings must remain accessible") { nodeWithText("Vuelve a tu entrenamiento") == null }
            shell("am start -W -n ${instrumentation.context.packageName}/com.gymapp.platform.RestrictedTestActivity")
            waitUntil("Barrier did not return for restricted app") { nodeWithText("Emergencia: salir ahora") != null }
            assertTrue(requireNotNull(nodeWithText("Emergencia: salir ahora")).performAction(AccessibilityNodeInfo.ACTION_CLICK))
            waitUntil("Emergency must immediately close persisted session") { store.state.value.activeSession == null }
            assertEquals(SessionStatus.EMERGENCY_EXIT, store.state.value.workouts.last().status)
            assertFalse(prefs.getBoolean("focus", true))
            assertTrue(FocusPolicy.isAllowed("com.spotify.music", context.packageName, emptySet()))
            assertFalse(FocusPolicy.isAllowed("test.synthetic.social", context.packageName, emptySet()))
        } finally {
            controller.emergencyExit()
            controller.syncSession(null)
            store.update { original }
            val restore = previousEnabled?.takeIf { it.isNotBlank() }
            if (restore == null) shell("settings delete secure enabled_accessibility_services")
            else shell("settings put secure enabled_accessibility_services $restore")
            shell("settings put secure accessibility_enabled $previousAccessibility")
        }
    }

    @Test fun noSourceAndDeniedHealthPermissionNeverProduceVerifiedSteps() = runBlocking {
        val store = Store.get(context)
        val original = store.state.value
        val controller = PlatformController(context)
        try {
            store.update { it.copy(activeSession = null, settings = it.settings.copy(stepSource = "none", focusEnabled = false)) }
            store.startSession()
            var active = requireNotNull(store.state.value.activeSession)
            controller.syncSession(active.id, active.startedAt, "none")
            val missing = controller.stepReading()
            assertNull(missing.count)
            assertNull(missing.lastReadEpochMillis)
            assertTrue(missing.status.contains("Sin fuente"))
            assertFalse(controller.startPhoneTracking())
            store.closeSession(SessionStatus.SENSOR_UNVERIFIED, "Synthetic adapter test")
            controller.syncSession(null)
            store.update { it.copy(settings = it.settings.copy(stepSource = "health_connect")) }
            store.startSession()
            active = requireNotNull(store.state.value.activeSession)
            controller.syncSession(active.id, active.startedAt, "health_connect")
            shell("pm revoke ${context.packageName} android.permission.health.READ_STEPS")
            val denied = controller.refreshHealth()
            assertNull(denied.count)
            assertNull(denied.lastReadEpochMillis)
            assertTrue(denied.status.contains("Permiso") || denied.status.contains("no disponible"))
            assertEquals(0L, store.state.value.activeSession?.steps?.count)
        } finally {
            controller.syncSession(null)
            store.update { original }
        }
    }

    @Test fun reminderSnoozeActionPersistsThirtyMinutesOffline() {
        val store = Store.get(context)
        val original = store.state.value
        val before = System.currentTimeMillis()
        try {
            store.update { it.copy(settings = it.settings.copy(snoozeUntil = null)) }
            context.sendBroadcast(Intent(context, ReminderActionReceiver::class.java).setAction("com.gymapp.SNOOZE"))
            waitUntil("Snooze action did not persist") { store.state.value.settings.snoozeUntil != null }
            val until = requireNotNull(store.state.value.settings.snoozeUntil)
            assertTrue(until >= before + 30 * 60_000L)
            assertTrue(until <= System.currentTimeMillis() + 30 * 60_000L)
            assertTrue(store.exportJson().contains(until.toString()))
        } finally {
            store.update { original }
            Reminders.schedule(context)
        }
    }
}
