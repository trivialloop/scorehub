package com.github.trivialloop.scorehub.utils

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import androidx.core.view.doOnNextLayout
import com.github.trivialloop.scorehub.R
import com.github.trivialloop.scorehub.data.AppDatabase
import com.github.trivialloop.scorehub.data.ScoreSheet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** How many score sheets are kept per game. Disabled by default (0). */
object ScoreSheetPrefs {
    const val UNLIMITED = -1
    const val DEFAULT_LIMIT = 0
    /** 0 = feature disabled, nothing is saved. */
    val LIMITS = listOf(0, 1, 2, 3, 5, 10, 20, 30, 50, 100, UNLIMITED)

    private const val KEY = "score_sheets_limit"

    fun getLimit(context: Context): Int =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).getInt(KEY, DEFAULT_LIMIT)

    fun setLimit(context: Context, limit: Int) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putInt(KEY, limit).apply()
    }

    fun isEnabled(context: Context): Boolean = getLimit(context) != 0

    fun indexOf(limit: Int): Int = LIMITS.indexOf(limit).let { if (it < 0) LIMITS.indexOf(DEFAULT_LIMIT) else it }

    fun label(context: Context, limit: Int): String = when (limit) {
        0 -> context.getString(R.string.score_sheets_limit_none)
        UNLIMITED -> context.getString(R.string.score_sheets_limit_unlimited)
        else -> limit.toString()
    }

    /** Number of sheets to delete so that [count] fits in [limit]. */
    fun excess(count: Int, limit: Int): Int =
        if (limit == UNLIMITED) 0 else (count - limit).coerceAtLeast(0)
}

object ScoreSheetManager {

    // Size/quality trade-off: ~15-40 KB per sheet. Raise for sharper zoom, lower to save space.
    private const val MAX_WIDTH_PX = 800f
    private const val MAX_HEIGHT_PX = 8192f   // stays under the GPU texture limit
    private const val QUALITY = 80
    private const val EXTENSION = "webp"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** noBackupFilesDir: excluded from Google Auto Backup (25 MB cap would endanger the DB backup). */
    fun sheetsDir(context: Context): File =
        File(context.noBackupFilesDir, "score_sheets").apply { mkdirs() }

    fun fileOf(context: Context, sheet: ScoreSheet): File = File(sheetsDir(context), sheet.fileName)

    fun totalBytes(context: Context): Long =
        sheetsDir(context).listFiles()?.sumOf { it.length() } ?: 0L

    // ─── Capture ──────────────────────────────────────────────────────────────

    /**
     * Call from `saveResultsAndShowSummary()` with the SAME [playedAt] as the GameResults.
     *
     * [views] are stacked vertically (header + table). Games that only display a window of
     * their players pass [prepare] (show everybody + rebuild the grid) and [restore] (back to
     * the normal display). The capture runs after the layout pass triggered by [prepare].
     */
    fun captureAndSave(
        activity: Activity,
        gameType: String,
        playedAt: Long,
        views: List<View>,
        prepare: (() -> Unit)? = null,
        restore: (() -> Unit)? = null
    ) {
        val appContext = activity.applicationContext
        if (views.isEmpty() || !ScoreSheetPrefs.isEnabled(appContext)) return
        val background = resolveBackground(activity)

        prepare?.invoke()
        runAfterLayout(views) {
            val bitmap = try { capture(background, views) } catch (_: OutOfMemoryError) { null }
            // Not inside the layout pass: rebuilding the grid here would re-enter layout
            views.first().post { restore?.invoke() }
            if (bitmap != null) scope.launch { save(appContext, gameType, playedAt, bitmap) }
        }
    }

    fun captureAndSave(activity: Activity, gameType: String, playedAt: Long, vararg views: View) =
        captureAndSave(activity, gameType, playedAt, views.toList())

    /** Waits for the last view that still has a pending layout; runs now if everything is laid out. */
    private fun runAfterLayout(views: List<View>, action: () -> Unit) {
        val pending = views.lastOrNull { it.isLayoutRequested || !it.isLaidOut }
        if (pending == null) action() else pending.doOnNextLayout { action() }
    }

    private fun resolveBackground(activity: Activity): Int {
        val tv = TypedValue()
        activity.theme.resolveAttribute(android.R.attr.colorBackground, tv, true)
        return tv.data
    }

    /** Height of the real content: a fillViewport ScrollView child may be taller than its rows. */
    private fun contentHeight(v: View): Int =
        if (v is LinearLayout && v.orientation == LinearLayout.VERTICAL && v.childCount > 0)
            (v.getChildAt(v.childCount - 1).bottom + v.paddingBottom).coerceAtMost(v.height)
        else v.height

    private fun capture(background: Int, views: List<View>): Bitmap? {
        val laidOut = views.filter { it.width > 0 && it.height > 0 }
        if (laidOut.isEmpty()) return null

        val width = laidOut.maxOf { it.width }
        val height = laidOut.sumOf { contentHeight(it) }
        val scale = minOf(1f, MAX_WIDTH_PX / width, MAX_HEIGHT_PX / height)

        val bitmap = Bitmap.createBitmap(
            (width * scale).toInt().coerceAtLeast(1),
            (height * scale).toInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        canvas.drawColor(background)
        canvas.scale(scale, scale)

        var top = 0
        for (v in laidOut) {
            val h = contentHeight(v)
            canvas.save()
            canvas.translate(0f, top.toFloat())
            canvas.clipRect(0, 0, v.width, h)
            v.draw(canvas)
            canvas.restore()
            top += h
        }
        return bitmap
    }

    // ─── Persistence ──────────────────────────────────────────────────────────

    @Suppress("DEPRECATION")
    private fun webpFormat(): Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY
        else Bitmap.CompressFormat.WEBP

    private suspend fun save(context: Context, gameType: String, playedAt: Long, bitmap: Bitmap) {
        val fileName = "${gameType}_$playedAt.$EXTENSION"
        try {
            FileOutputStream(File(sheetsDir(context), fileName)).use {
                bitmap.compress(webpFormat(), QUALITY, it)
            }
        } catch (_: IOException) {
            return
        } finally {
            bitmap.recycle()
        }
        AppDatabase.getDatabase(context).scoreSheetDao()
            .insert(ScoreSheet(gameType = gameType, playedAt = playedAt, fileName = fileName))
        prune(context, gameType)
    }

    /** Deletes the oldest sheets of [gameType] beyond the configured limit. */
    suspend fun prune(context: Context, gameType: String) {
        val dao = AppDatabase.getDatabase(context).scoreSheetDao()
        val excess = ScoreSheetPrefs.excess(dao.countByGame(gameType), ScoreSheetPrefs.getLimit(context))
        if (excess > 0) dao.getOldest(gameType, excess).forEach { delete(context, it) }
    }

    suspend fun delete(context: Context, sheet: ScoreSheet) {
        fileOf(context, sheet).delete()
        AppDatabase.getDatabase(context).scoreSheetDao().deleteById(sheet.id)
    }

    suspend fun deleteAll(context: Context) {
        AppDatabase.getDatabase(context).scoreSheetDao().deleteAll()
        sheetsDir(context).listFiles()?.forEach { it.delete() }
    }
}
