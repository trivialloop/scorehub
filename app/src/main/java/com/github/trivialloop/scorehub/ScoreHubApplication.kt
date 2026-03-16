package com.github.trivialloop.scorehub

import android.app.Application
import com.github.trivialloop.scorehub.utils.ThemeHelper

class ScoreHubApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Apply saved theme on app start
        val savedTheme = ThemeHelper.getPersistedTheme(this)
        ThemeHelper.applyTheme(savedTheme)
    }
}
