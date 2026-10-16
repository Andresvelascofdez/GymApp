package com.gymapp.platform

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusPolicyTest {
    private val own = "com.gymapp.synthetic"
    private val essential = setOf("com.android.settings", "com.android.systemui", "test.emergency.dialer", "test.launcher")
    @Test fun spotifyAndOwnAppRemainUsable() {
        assertTrue(FocusPolicy.isAllowed(own, own, essential))
        assertTrue(FocusPolicy.isAllowed("com.spotify.music", own, essential))
    }
    @Test fun ordinaryAppsAndMiFitnessRemainRestricted() {
        listOf("test.social", "test.video", "test.browser", "com.xiaomi.wearable", "com.spotify.music.fake").forEach {
            assertFalse(it, FocusPolicy.isAllowed(it, own, essential))
        }
    }
    @Test fun essentialSystemAndEmergencySurfacesRemainAccessible() {
        essential.forEach { assertTrue(it, FocusPolicy.isAllowed(it, own, essential)) }
        assertFalse(FocusPolicy.isAllowed("test.random.system.app", own, essential))
    }
}
