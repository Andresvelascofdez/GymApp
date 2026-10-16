package com.gymapp

import android.app.Application
import com.gymapp.platform.Reminders

class GymApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Reminders.schedule(this)
    }
}
