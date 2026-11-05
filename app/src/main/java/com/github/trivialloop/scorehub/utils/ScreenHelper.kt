package com.github.trivialloop.scorehub.utils

import android.content.Context

object ScreenHelper {
    private const val KEEP_SCREEN_ON = "keep_screen_on"

    fun isKeepScreenOn(context: Context): Boolean =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getBoolean(KEEP_SCREEN_ON, false)

    fun setKeepScreenOn(context: Context, enabled: Boolean) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit().putBoolean(KEEP_SCREEN_ON, enabled).apply()
    }
}
