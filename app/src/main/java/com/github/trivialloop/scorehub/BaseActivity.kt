package com.github.trivialloop.scorehub

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat

/**
 * Base activity that ensures the decor fits system windows correctly.
 *
 * This fixes a display issue introduced in newer versions of androidx.core / appcompat
 * where the toolbar was rendered behind the status bar (system bars overlay).
 *
 * All activities in the app must extend this class instead of AppCompatActivity directly.
 */
abstract class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be called BEFORE super.onCreate() and setContentView()
        WindowCompat.setDecorFitsSystemWindows(window, true)
        super.onCreate(savedInstanceState)
    }
}
