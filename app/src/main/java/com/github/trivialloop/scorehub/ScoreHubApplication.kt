package com.github.trivialloop.scorehub

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.WindowManager
import com.github.trivialloop.scorehub.utils.ScreenHelper
import com.github.trivialloop.scorehub.utils.ThemeHelper

class ScoreHubApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Apply saved theme on app start
        val savedTheme = ThemeHelper.getPersistedTheme(this)
        ThemeHelper.applyTheme(savedTheme)

        // Apply the "keep screen on" preference to every Activity
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                if (ScreenHelper.isKeepScreenOn(activity)) {
                    activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}
