package com.gymapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import com.gymapp.data.Store
import com.gymapp.ui.GymApp

class MainActivity : ComponentActivity() {
    private val store by lazy { Store.get(this) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        consumeStartIntent(intent)
        setContent { GymApp(store) }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeStartIntent(intent)
    }
    private fun consumeStartIntent(intent: Intent) {
        if (intent.getBooleanExtra("gymapp.start_workout", false)) {
            intent.removeExtra("gymapp.start_workout")
            if (store.state.value.activeSession == null) store.startSession()
        }
    }
}
