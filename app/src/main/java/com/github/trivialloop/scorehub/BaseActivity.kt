package com.github.trivialloop.scorehub

import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Base activity that handles system window insets correctly across all Android versions,
 * including Android 15+ which enforces mandatory edge-to-edge display.
 *
 * On Android 14 and below: WindowCompat.setDecorFitsSystemWindows(window, true) is enough.
 * On Android 15+: edge-to-edge is forced, so we must handle insets manually by applying
 * the status bar height as top padding to the root view.
 */
abstract class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // On Android 14 and below this restores classic toolbar behaviour.
        // On Android 15+ this call is ignored by the system, so we also apply
        // insets manually below (via ViewCompat.setOnApplyWindowInsetsListener).
        WindowCompat.setDecorFitsSystemWindows(window, true)
        super.onCreate(savedInstanceState)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        applyStatusBarInsets()
    }

    /**
     * Applies the status bar inset as top padding to the root view so the toolbar
     * is never hidden behind the system status bar on Android 15+ (edge-to-edge forced).
     *
     * The padding is only applied if the system actually delivers a non-zero top inset,
     * which means Android 15+ is overriding our setDecorFitsSystemWindows(true) call.
     */
    private fun applyStatusBarInsets() {
        val root = window.decorView.findViewById<ViewGroup>(android.R.id.content) ?: return
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            // Only apply padding if the system is pushing content behind the status bar.
            // On Android 14 and below with setDecorFitsSystemWindows(true), statusBar.top == 0.
            // On Android 15+ it will be the actual status bar height.
            if (statusBar.top > 0) {
                view.setPadding(
                    view.paddingLeft,
                    statusBar.top,
                    view.paddingRight,
                    view.paddingBottom
                )
            }
            // Return CONSUMED so child views don't double-apply the insets
            WindowInsetsCompat.CONSUMED
        }
    }
}
