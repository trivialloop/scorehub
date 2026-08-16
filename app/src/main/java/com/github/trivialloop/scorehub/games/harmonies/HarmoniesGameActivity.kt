package com.github.trivialloop.scorehub.games.harmonies

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.github.trivialloop.scorehub.R
import com.github.trivialloop.scorehub.data.AppDatabase
import com.github.trivialloop.scorehub.data.GameResult
import com.github.trivialloop.scorehub.databinding.ActivityHarmoniesGameBinding
import com.github.trivialloop.scorehub.ui.GameResultsDialog
import com.github.trivialloop.scorehub.ui.HelpDialogs
import com.github.trivialloop.scorehub.utils.LocaleHelper
import com.github.trivialloop.scorehub.utils.ScoreColorRole
import kotlinx.coroutines.launch

class HarmoniesGameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHarmoniesGameBinding
    private lateinit var database: AppDatabase
    private lateinit var playerIds: LongArray
    private lateinit var playerNames: Array<String>
    private lateinit var playerColors: IntArray
    private lateinit var playerScores: List<HarmoniesPlayerScore>
    private var gameOver = false

    companion object {
        const val GAME_TYPE = "harmonies"
        private const val LABEL_COL_DP = 65
    }

    override fun attachBaseContext(newBase: Context) {
        val language = LocaleHelper.getPersistedLocale(newBase)
        super.attachBaseContext(LocaleHelper.setLocale(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHarmoniesGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->

            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            binding.appBarLayout.setPadding(
                0,
                systemBars.top,
                0,
                0
            )

            binding.root.setPadding(
                systemBars.left,
                0,
                systemBars.right,
                systemBars.bottom
            )

            insets
        }

        database = AppDatabase.getDatabase(this)
        playerIds = intent.getLongArrayExtra("PLAYER_IDS") ?: longArrayOf()
        playerNames = intent.getStringArrayExtra("PLAYER_NAMES") ?: arrayOf()
        playerColors = intent.getIntArrayExtra("PLAYER_COLORS") ?: intArrayOf()

        playerScores = playerIds.indices.map { i ->
            HarmoniesPlayerScore(playerIds[i], playerNames[i], playerColors[i])
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.harmonies_game)

        buildScoreTable()

        onBackPressedDispatcher.addCallback(this) {
            showQuitGameDialog()
        }
    }

    // ─── Table ────────────────────────────────────────────────────────────────

    private fun buildScoreTable() {
        binding.scoreTableContainer.removeAllViews()
        binding.scoreTableContainer.addView(buildLabelColumn())
        for (ps in playerScores) binding.scoreTableContainer.addView(buildPlayerColumn(ps))
    }

    private fun buildLabelColumn(): LinearLayout {
        val col = makeColumn(weight = 0f, widthDp = LABEL_COL_DP)
        col.addView(makeLabelHeaderCell())
        for (category in HarmoniesCategory.entries) {
            col.addView(makeCategoryCell(category))
        }
        col.addView(makeLabelTotalCell())
        return col
    }

    private fun makeLabelHeaderCell(): TextView = TextView(this).apply {
        layoutParams = cellLayoutParams()
        background = borderDrawable(ContextCompat.getColor(this@HarmoniesGameActivity, R.color.header_cell_background))
    }

    private fun makeCategoryCell(category: HarmoniesCategory): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(6), dpToPx(4), dpToPx(6), dpToPx(4))
            layoutParams = cellLayoutParams()
            background = borderDrawable(ContextCompat.getColor(this@HarmoniesGameActivity, R.color.header_cell_background))
        }
        val label = TextView(this).apply {
            text = categoryLabel(category)
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@HarmoniesGameActivity, R.color.header_cell_text))
            maxLines = 2
            gravity = Gravity.CENTER_VERTICAL
        }
        container.addView(label)
        return container
    }

    private fun makeLabelTotalCell(): TextView = TextView(this).apply {
        text = getString(R.string.harmonies_total)
        gravity = Gravity.CENTER
        textSize = 13f
        setTypeface(null, Typeface.BOLD)
        layoutParams = cellLayoutParams()
        background = borderDrawable(ContextCompat.getColor(this@HarmoniesGameActivity, R.color.cell_calculated_bg))
        setTextColor(ContextCompat.getColor(this@HarmoniesGameActivity, R.color.score_calculated_cell_text))
    }

    // ─── Player column ─────────────────────────────────────────────────────────

    private fun buildPlayerColumn(ps: HarmoniesPlayerScore): LinearLayout {
        val col = makeColumn(weight = 1f, widthDp = 0)
        col.addView(makePlayerNameCell(ps))
        for (category in HarmoniesCategory.entries) col.addView(makeScoreCell(ps, category))
        col.addView(makeTotalCell(ps))
        return col
    }

    private fun makePlayerNameCell(ps: HarmoniesPlayerScore): TextView = TextView(this).apply {
        text = ps.playerName
        gravity = Gravity.CENTER
        setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
        textSize = 13f
        setTypeface(null, Typeface.BOLD)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        layoutParams = cellLayoutParams()
        background = playerCellDrawable(ps.playerColor)
        setTextColor(Color.WHITE)
    }

    private fun makeScoreCell(ps: HarmoniesPlayerScore, category: HarmoniesCategory): TextView {
        val score = ps.scores[category]
        val filled = playerScores.mapNotNull { it.scores[category] }
        val allFilled = filled.size == playerScores.size
        val role = if (allFilled) ScoreColorRole(score, playerScores.map { it.scores[category] }, higherIsBetter = true)
                   else ScoreColorRole.NEUTRAL
        val textColor = if (role != ScoreColorRole.NEUTRAL) role.toColor(this)
                        else ContextCompat.getColor(this, R.color.score_cell_text)
        val bgRes = if (!gameOver && score == null) R.color.cell_editable_bg else R.color.score_cell_background

        return TextView(this).apply {
            text = score?.toString() ?: ""
            gravity = Gravity.CENTER
            textSize = 16f
            setTextColor(textColor)
            layoutParams = cellLayoutParams()
            background = borderDrawable(ContextCompat.getColor(this@HarmoniesGameActivity, bgRes))
            if (!gameOver) setOnClickListener { showDropdownPicker(ps, category) }
        }
    }

    private fun makeTotalCell(ps: HarmoniesPlayerScore): TextView {
        val total = ps.getTotal()
        val allComplete = playerScores.all { it.isComplete() }
        val role = if (allComplete) ScoreColorRole(total, playerScores.map { it.getTotal() }, higherIsBetter = true)
                   else ScoreColorRole.NEUTRAL
        val textColor = if (role != ScoreColorRole.NEUTRAL) role.toColor(this)
                        else ContextCompat.getColor(this, R.color.score_calculated_cell_text)
        return TextView(this).apply {
            text = total.toString()
            gravity = Gravity.CENTER
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setTextColor(textColor)
            layoutParams = cellLayoutParams()
            background = borderDrawable(ContextCompat.getColor(this@HarmoniesGameActivity, R.color.cell_calculated_bg))
        }
    }

    // ─── Dropdown picker ──────────────────────────────────────────────────────

    private fun showDropdownPicker(ps: HarmoniesPlayerScore, category: HarmoniesCategory) {
        val current = ps.scores[category]
        val title = if (current != null) "✏️ ${ps.playerName} — ${categoryLabel(category)}"
                    else "${ps.playerName} — ${categoryLabel(category)}"
        val values = category.getPossibleValues()
        val items = values.map { it.toString() }.toTypedArray()

        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(items) { _, which ->
                ps.scores[category] = values[which]
                buildScoreTable()
                checkCompletion()
            }
            .create()

        dialog.show()

        val scrollTo = if (current != null) values.indexOf(current) else 0
        if (scrollTo >= 0) dialog.listView?.post { dialog.listView?.setSelection(scrollTo) }
    }

    // ─── Completion check ─────────────────────────────────────────────────────

    private fun checkCompletion() {
        if (playerScores.all { it.isComplete() } && !gameOver) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.harmonies_game_complete))
                .setMessage(getString(R.string.harmonies_game_complete_message))
                .setPositiveButton(getString(R.string.yes)) { _, _ ->
                    gameOver = true; buildScoreTable(); saveResultsAndShowSummary()
                }
                .setNegativeButton(getString(R.string.no), null).show()
        }
    }

    private fun saveResultsAndShowSummary() {
        val totals = playerScores.associate { it to it.getTotal() }
        val maxScore = totals.values.maxOrNull() ?: 0
        val winners = totals.filter { it.value == maxScore }.keys
        val isDraw = winners.size > 1
        val isSoloGame = playerScores.size == 1
        lifecycleScope.launch {
            database.gameResultDao().insertGameResults(playerScores.map { ps ->
                GameResult(
                    gameType = GAME_TYPE, playerId = ps.playerId, playerName = ps.playerName,
                    score = ps.getTotal(),
                    isWinner = if (isSoloGame) false else (!isDraw && ps in winners),
                    isDraw = if (isSoloGame) false else (isDraw && ps in winners)
                )
            })
            val sorted = totals.entries.sortedByDescending { it.value }
            var rank = 1
            val entries = sorted.mapIndexed { i, (ps, s) ->
                val r = if (i > 0 && s == sorted[i - 1].value) rank
                        else { rank = i + 1; rank }
                GameResultsDialog.PlayerResult(ps.playerName, ps.playerColor, s, r)
            }
            GameResultsDialog.show(this@HarmoniesGameActivity, entries, isDraw && !isSoloGame, " pts") { finish() }
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun categoryLabel(category: HarmoniesCategory): String = when (category) {
        HarmoniesCategory.TREES -> getString(R.string.harmonies_trees)
        HarmoniesCategory.MOUNTAINS -> getString(R.string.harmonies_mountains)
        HarmoniesCategory.FIELDS -> getString(R.string.harmonies_fields)
        HarmoniesCategory.BUILDINGS -> getString(R.string.harmonies_buildings)
        HarmoniesCategory.RIVER -> getString(R.string.harmonies_river)
        HarmoniesCategory.ANIMALS -> getString(R.string.harmonies_animals)
    }

    private fun cellLayoutParams() =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)

    private fun makeColumn(weight: Float, widthDp: Int): LinearLayout {
        val widthPx = if (widthDp == 0) 0 else dpToPx(widthDp)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(widthPx, LinearLayout.LayoutParams.MATCH_PARENT, weight)
        }
    }

    private fun borderDrawable(bgColor: Int): GradientDrawable = GradientDrawable().apply {
        setColor(bgColor); setStroke(1, ContextCompat.getColor(this@HarmoniesGameActivity, R.color.cell_border))
    }

    private fun playerCellDrawable(bgColor: Int): GradientDrawable = GradientDrawable().apply {
        setColor(bgColor); setStroke(1, ContextCompat.getColor(this@HarmoniesGameActivity, R.color.cell_border))
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private fun showQuitGameDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.harmonies_quit_game)
            .setMessage(R.string.harmonies_quit_game_message)
            .setPositiveButton(R.string.yes) { _, _ -> finish() }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_harmonies_game, menu); return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { showQuitGameDialog(); true }
            R.id.action_help -> { HelpDialogs.showAppHelp(this, GAME_TYPE); true }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
