package com.github.trivialloop.scorehub

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.github.trivialloop.scorehub.data.AppDatabase
import com.github.trivialloop.scorehub.data.ScoreSheet
import com.github.trivialloop.scorehub.databinding.ActivityScoreSheetViewerBinding
import com.github.trivialloop.scorehub.utils.LocaleHelper
import com.github.trivialloop.scorehub.utils.ScoreSheetManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

class ScoreSheetViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScoreSheetViewerBinding
    private var sheets: List<ScoreSheet> = emptyList()   // same order as the list screen (newest first)
    private var index = 0
    private var currentId = -1L
    private var loadJob: Job? = null

    companion object {
        /** Id of the sheet to open first. */
        const val EXTRA_SHEET_ID = "SHEET_ID"
        /** Ids of the sheets to browse, in display order (the filtered list of the previous screen). */
        const val EXTRA_SHEET_IDS = "SHEET_IDS"
        private const val STATE_CURRENT_ID = "current_id"
    }

    override fun attachBaseContext(newBase: Context) {
        val language = LocaleHelper.getPersistedLocale(newBase)
        super.attachBaseContext(LocaleHelper.setLocale(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScoreSheetViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.appBarLayout.setPadding(0, systemBars.top, 0, 0)
            binding.root.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            insets
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        currentId = savedInstanceState?.getLong(STATE_CURRENT_ID)
            ?: intent.getLongExtra(EXTRA_SHEET_ID, -1L)

        binding.btnPrev.setOnClickListener { go(index - 1) }
        binding.btnNext.setOnClickListener { go(index + 1) }

        val ids = intent.getLongArrayExtra(EXTRA_SHEET_IDS) ?: longArrayOf(currentId)
        lifecycleScope.launch {
            val byId = AppDatabase.getDatabase(this@ScoreSheetViewerActivity)
                .scoreSheetDao().getAll().associateBy { it.id }
            sheets = ids.mapNotNull { byId[it] }
            if (sheets.isEmpty()) { finish(); return@launch }
            index = sheets.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
            show()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(STATE_CURRENT_ID, currentId)
    }

    private fun go(newIndex: Int) {
        if (newIndex !in sheets.indices) return
        index = newIndex
        show()
    }

    private fun show() {
        val sheet = sheets[index]
        currentId = sheet.id

        val def = GameRegistry.findByType(sheet.gameType)
        supportActionBar?.title = def?.let { getString(it.nameResId) } ?: sheet.gameType
        supportActionBar?.subtitle = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(sheet.playedAt))
        binding.textPosition.text = getString(R.string.score_sheet_position, index + 1, sheets.size)
        binding.btnPrev.isEnabled = index > 0
        binding.btnPrev.alpha = if (index > 0) 1f else 0.3f
        binding.btnNext.isEnabled = index < sheets.lastIndex
        binding.btnNext.alpha = if (index < sheets.lastIndex) 1f else 0.3f

        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                BitmapFactory.decodeFile(ScoreSheetManager.fileOf(this@ScoreSheetViewerActivity, sheet).path)
            }
            if (bitmap == null) { removeCurrentFromList(); return@launch }   // file gone
            binding.imageSheet.setImageBitmap(bitmap)
        }
    }

    private fun removeCurrentFromList() {
        sheets = sheets.toMutableList().also { it.removeAt(index) }
        if (sheets.isEmpty()) { finish(); return }
        index = index.coerceAtMost(sheets.lastIndex)
        show()
    }

    private fun confirmDelete() {
        val current = sheets.getOrNull(index) ?: return
        AlertDialog.Builder(this)
            .setTitle(R.string.score_sheet_delete)
            .setMessage(R.string.score_sheet_delete_message)
            .setPositiveButton(R.string.yes) { _, _ ->
                lifecycleScope.launch {
                    ScoreSheetManager.delete(applicationContext, current)
                    Toast.makeText(this@ScoreSheetViewerActivity, R.string.score_sheet_deleted, Toast.LENGTH_SHORT).show()
                    removeCurrentFromList()   // moves on to the next sheet, or closes if it was the last
                }
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_score_sheet_viewer, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> { finish(); true }
        R.id.action_delete_sheet -> { confirmDelete(); true }
        else -> super.onOptionsItemSelected(item)
    }
}
