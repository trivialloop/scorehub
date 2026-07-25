package com.github.trivialloop.scorehub.games.forestshuffle

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
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
import com.github.trivialloop.scorehub.databinding.ActivityForestshuffleGameBinding
import com.github.trivialloop.scorehub.ui.GameResultsDialog
import com.github.trivialloop.scorehub.ui.HelpDialogs
import com.github.trivialloop.scorehub.utils.LocaleHelper
import com.github.trivialloop.scorehub.utils.ScoreColorRole
import kotlinx.coroutines.launch

class ForestShuffleGameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityForestshuffleGameBinding
    private lateinit var database: AppDatabase
    private lateinit var playerIds: LongArray
    private lateinit var playerNames: Array<String>
    private lateinit var playerColors: IntArray
    private lateinit var playerScores: List<ForestShufflePlayerScore>
    private var gameOver = false

    companion object {
        const val GAME_TYPE = "forest_shuffle"
        private const val LABEL_COL_DP = 150

        // Tree rows shown in this exact order
        private val TREE_ROW_ORDER = listOf(
            TreeSpecies.BIRCH, TreeSpecies.DOUGLAS_FIR, TreeSpecies.BEECH, TreeSpecies.HORSE_CHESTNUT,
            TreeSpecies.LINDEN, TreeSpecies.OAK, TreeSpecies.SILVER_FIR, TreeSpecies.SYCAMORE
        )

        private const val SAPLINGS_MAX = 20
        private const val SILVER_FIR_ATTACHED_MAX = 24
        private const val CAVE_MAX = 15
        private const val TOP_BOTTOM_MAX = 200
        private const val LEFT_RIGHT_MAX = 200
    }

    override fun attachBaseContext(newBase: Context) {
        val language = LocaleHelper.getPersistedLocale(newBase)
        super.attachBaseContext(LocaleHelper.setLocale(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityForestshuffleGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.appBarLayout.setPadding(0, systemBars.top, 0, 0)
            binding.root.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            insets
        }

        database     = AppDatabase.getDatabase(this)
        playerIds    = intent.getLongArrayExtra("PLAYER_IDS")     ?: longArrayOf()
        playerNames  = intent.getStringArrayExtra("PLAYER_NAMES") ?: arrayOf()
        playerColors = intent.getIntArrayExtra("PLAYER_COLORS")   ?: intArrayOf()

        playerScores = playerIds.indices.map { i ->
            ForestShufflePlayerScore(playerIds[i], playerNames[i], playerColors[i])
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.forestshuffle_game)

        buildScoreTable()
    }

    // ─── Table ────────────────────────────────────────────────────────────────

    private fun buildScoreTable() {
        binding.scoreTableContainer.removeAllViews()
        binding.scoreTableContainer.addView(buildLabelColumn())
        for (ps in playerScores) binding.scoreTableContainer.addView(buildPlayerColumn(ps))
    }

    private fun buildLabelColumn(): LinearLayout {
        val col = makeColumn(weight = 0f, widthDp = LABEL_COL_DP)
        col.addView(makeLabelCell("", isTotal = false)) // header spacer
        for (species in TREE_ROW_ORDER) col.addView(makeLabelCell(treeLabel(species), isTotal = false))
        col.addView(makeLabelCell(getString(R.string.forestshuffle_saplings), isTotal = false))
        col.addView(makeLabelCell(getString(R.string.forestshuffle_silver_fir_attached), isTotal = false))
        col.addView(makeLabelCell(getString(R.string.forestshuffle_top_bottom), isTotal = false))
        col.addView(makeLabelCell(getString(R.string.forestshuffle_left_right), isTotal = false))
        col.addView(makeLabelCell(getString(R.string.forestshuffle_cave), isTotal = false))
        col.addView(makeLabelCell(getString(R.string.forestshuffle_total), isTotal = true))
        return col
    }

    private fun buildPlayerColumn(ps: ForestShufflePlayerScore): LinearLayout {
        val col = makeColumn(weight = 1f, widthDp = 0)
        col.addView(makePlayerNameCell(ps))

        for (species in TREE_ROW_ORDER) {
            col.addView(makeTreeCell(ps, species))
        }
        col.addView(makeSaplingsCell(ps))
        col.addView(makeSilverFirAttachedCell(ps))
        col.addView(makeFreeTextCell(
            value = ps.topBottomPoints,
            max = TOP_BOTTOM_MAX,
            title = "${ps.playerName} — ${getString(R.string.forestshuffle_top_bottom)}"
        ) { v -> ps.topBottomPoints = v; buildScoreTable(); checkCompletion() })
        col.addView(makeFreeTextCell(
            value = ps.leftRightPoints,
            max = LEFT_RIGHT_MAX,
            title = "${ps.playerName} — ${getString(R.string.forestshuffle_left_right)}"
        ) { v -> ps.leftRightPoints = v; buildScoreTable(); checkCompletion() })
        col.addView(makeCaveCell(ps))
        col.addView(makeTotalCell(ps))
        return col
    }

    // ─── Tree cells ───────────────────────────────────────────────────────────

    private fun makeTreeCell(ps: ForestShufflePlayerScore, species: TreeSpecies): TextView {
        val count = ps.treeCounts[species]
        // Silver Fir's own count is informational (score comes from the attached-cards row)
        val isInformational = species == TreeSpecies.SILVER_FIR
        val displayScores = playerScores.map { p ->
            if (p.treeCounts[species] == null) null
            else if (isInformational) p.treeCounts[species] else ForestShuffleScoring.speciesScore(species, p, playerScores)
        }
        val myDisplay = if (count == null) null
        else if (isInformational) count
        else ForestShuffleScoring.speciesScore(species, ps, playerScores)

        val allEntered = displayScores.all { it != null }
        val role = if (allEntered && !isInformational)
            ScoreColorRole(myDisplay, displayScores, higherIsBetter = true) else ScoreColorRole.NEUTRAL

        val bgRes = if (!gameOver && count == null) R.color.cell_editable_bg
        else if (!gameOver) R.color.cell_editable_filled_bg
        else R.color.score_cell_background

        return TextView(this).apply {
            text = myDisplay?.toString() ?: ""
            gravity = Gravity.CENTER; textSize = 14f
            if (isInformational) alpha = 0.65f
            if (role != ScoreColorRole.NEUTRAL) { setTextColor(role.toColor(this@ForestShuffleGameActivity)); setTypeface(null, Typeface.BOLD) }
            else setTextColor(ContextCompat.getColor(this@ForestShuffleGameActivity, R.color.score_cell_text))
            layoutParams = cellLayoutParams()
            background = borderDrawable(ContextCompat.getColor(this@ForestShuffleGameActivity, bgRes))
            if (!gameOver) setOnClickListener {
                showDropdownPicker(
                    title = "${ps.playerName} — ${treeLabel(species)}",
                    max = species.maxCount, current = count
                ) { v -> ps.treeCounts[species] = v; buildScoreTable(); checkCompletion() }
            }
        }
    }

    private fun makeSaplingsCell(ps: ForestShufflePlayerScore): TextView {
        val value = ps.saplings
        val bgRes = if (!gameOver && value == null) R.color.cell_editable_bg
        else if (!gameOver) R.color.cell_editable_filled_bg
        else R.color.score_cell_background
        return TextView(this).apply {
            text = value?.toString() ?: ""
            gravity = Gravity.CENTER; textSize = 14f; alpha = 0.65f
            setTextColor(ContextCompat.getColor(this@ForestShuffleGameActivity, R.color.score_cell_text))
            layoutParams = cellLayoutParams()
            background = borderDrawable(ContextCompat.getColor(this@ForestShuffleGameActivity, bgRes))
            if (!gameOver) setOnClickListener {
                showDropdownPicker(
                    title = "${ps.playerName} — ${getString(R.string.forestshuffle_saplings)}",
                    max = SAPLINGS_MAX, current = value
                ) { v -> ps.saplings = v; buildScoreTable(); checkCompletion() }
            }
        }
    }

    private fun makeSilverFirAttachedCell(ps: ForestShufflePlayerScore): TextView {
        val count = ps.silverFirAttachedCards
        val myScore = count?.let { it * 2 }
        val allValues = playerScores.map { it.silverFirAttachedCards?.let { c -> c * 2 } }
        val allEntered = allValues.all { it != null }
        val role = if (allEntered) ScoreColorRole(myScore, allValues, higherIsBetter = true) else ScoreColorRole.NEUTRAL
        val bgRes = if (!gameOver && count == null) R.color.cell_editable_bg
        else if (!gameOver) R.color.cell_editable_filled_bg
        else R.color.score_cell_background
        return TextView(this).apply {
            text = myScore?.toString() ?: ""
            gravity = Gravity.CENTER; textSize = 14f
            if (role != ScoreColorRole.NEUTRAL) { setTextColor(role.toColor(this@ForestShuffleGameActivity)); setTypeface(null, Typeface.BOLD) }
            else setTextColor(ContextCompat.getColor(this@ForestShuffleGameActivity, R.color.score_cell_text))
            layoutParams = cellLayoutParams()
            background = borderDrawable(ContextCompat.getColor(this@ForestShuffleGameActivity, bgRes))
            if (!gameOver) setOnClickListener {
                showDropdownPicker(
                    title = "${ps.playerName} — ${getString(R.string.forestshuffle_silver_fir_attached)}",
                    max = SILVER_FIR_ATTACHED_MAX, current = count
                ) { v -> ps.silverFirAttachedCards = v; buildScoreTable(); checkCompletion() }
            }
        }
    }

    private fun makeCaveCell(ps: ForestShufflePlayerScore): TextView {
        val value = ps.caveCards
        val allValues = playerScores.map { it.caveCards }
        val allEntered = allValues.all { it != null }
        val role = if (allEntered) ScoreColorRole(value, allValues, higherIsBetter = true) else ScoreColorRole.NEUTRAL
        val bgRes = if (!gameOver && value == null) R.color.cell_editable_bg
        else if (!gameOver) R.color.cell_editable_filled_bg
        else R.color.score_cell_background
        return TextView(this).apply {
            text = value?.toString() ?: ""
            gravity = Gravity.CENTER; textSize = 14f
            if (role != ScoreColorRole.NEUTRAL) { setTextColor(role.toColor(this@ForestShuffleGameActivity)); setTypeface(null, Typeface.BOLD) }
            else setTextColor(ContextCompat.getColor(this@ForestShuffleGameActivity, R.color.score_cell_text))
            layoutParams = cellLayoutParams()
            background = borderDrawable(ContextCompat.getColor(this@ForestShuffleGameActivity, bgRes))
            if (!gameOver) setOnClickListener {
                showDropdownPicker(
                    title = "${ps.playerName} — ${getString(R.string.forestshuffle_cave)}",
                    max = CAVE_MAX, current = value
                ) { v -> ps.caveCards = v; buildScoreTable(); checkCompletion() }
            }
        }
    }

    private fun makeFreeTextCell(value: Int?, max: Int, title: String, onSaved: (Int) -> Unit): TextView {
        val allSameFieldValues = playerScores.map {
            if (title.endsWith(getString(R.string.forestshuffle_top_bottom))) it.topBottomPoints else it.leftRightPoints
        }
        val allEntered = allSameFieldValues.all { it != null }
        val role = if (allEntered) ScoreColorRole(value, allSameFieldValues, higherIsBetter = true) else ScoreColorRole.NEUTRAL
        val bgRes = if (!gameOver && value == null) R.color.cell_editable_bg
        else if (!gameOver) R.color.cell_editable_filled_bg
        else R.color.score_cell_background
        return TextView(this).apply {
            text = value?.toString() ?: ""
            gravity = Gravity.CENTER; textSize = 14f
            if (role != ScoreColorRole.NEUTRAL) { setTextColor(role.toColor(this@ForestShuffleGameActivity)); setTypeface(null, Typeface.BOLD) }
            else setTextColor(ContextCompat.getColor(this@ForestShuffleGameActivity, R.color.score_cell_text))
            layoutParams = cellLayoutParams()
            background = borderDrawable(ContextCompat.getColor(this@ForestShuffleGameActivity, bgRes))
            if (!gameOver) setOnClickListener {
                showFreeTextInput(title = title, current = value, max = max, onSaved = onSaved)
            }
        }
    }

    private fun makePlayerNameCell(ps: ForestShufflePlayerScore): TextView = TextView(this).apply {
        text = ps.playerName; gravity = Gravity.CENTER
        setPadding(dpToPx(4), dpToPx(6), dpToPx(4), dpToPx(6))
        textSize = 13f; setTypeface(null, Typeface.BOLD); maxLines = 1
        ellipsize = TextUtils.TruncateAt.END; layoutParams = cellLayoutParams()
        background = borderDrawable(ps.playerColor); setTextColor(Color.WHITE)
    }

    private fun makeTotalCell(ps: ForestShufflePlayerScore): TextView {
        val allComplete = playerScores.all { it.isComplete() }
        val total = ForestShuffleScoring.grandTotal(ps, playerScores)
        val allTotals = playerScores.map { ForestShuffleScoring.grandTotal(it, playerScores) }
        val role = if (allComplete) ScoreColorRole(total, allTotals, higherIsBetter = true) else ScoreColorRole.NEUTRAL
        return TextView(this).apply {
            text = total.toString(); gravity = Gravity.CENTER; textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(if (role != ScoreColorRole.NEUTRAL) role.toColor(this@ForestShuffleGameActivity)
            else ContextCompat.getColor(this@ForestShuffleGameActivity, R.color.score_calculated_cell_text))
            layoutParams = cellLayoutParams()
            background = borderDrawable(ContextCompat.getColor(this@ForestShuffleGameActivity, R.color.cell_calculated_bg))
        }
    }

    private fun makeLabelCell(text: String, isTotal: Boolean): TextView = TextView(this).apply {
        this.text = text; gravity = Gravity.CENTER_VERTICAL; textSize = 12f
        setTypeface(null, Typeface.BOLD)
        setPadding(dpToPx(6), dpToPx(4), dpToPx(6), dpToPx(4))
        maxLines = 2
        layoutParams = cellLayoutParams()
        val bgRes = if (isTotal) R.color.cell_calculated_bg else R.color.header_cell_background
        val fgRes = if (isTotal) R.color.score_calculated_cell_text else R.color.header_cell_text
        background = borderDrawable(ContextCompat.getColor(this@ForestShuffleGameActivity, bgRes))
        setTextColor(ContextCompat.getColor(this@ForestShuffleGameActivity, fgRes))
    }

    // ─── Dialogs ──────────────────────────────────────────────────────────────

    private fun showDropdownPicker(title: String, max: Int, current: Int?, onPicked: (Int) -> Unit) {
        val values = (0..max).toList()
        val items = values.map { it.toString() }.toTypedArray()
        val dialogTitle = if (current != null) "✏️ $title" else title
        val dialog = AlertDialog.Builder(this)
            .setTitle(dialogTitle)
            .setItems(items) { _, which -> onPicked(values[which]) }
            .create()
        dialog.show()
        val scrollTo = current?.let { values.indexOf(it) } ?: 0
        if (scrollTo >= 0) dialog.listView?.post { dialog.listView?.setSelection(scrollTo) }
    }

    private fun showFreeTextInput(title: String, current: Int?, max: Int, onSaved: (Int) -> Unit) {
        val dialogTitle = if (current != null) "✏️ $title" else title
        val editText = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "0–$max"; gravity = Gravity.CENTER; textSize = 20f
            filters = arrayOf(InputFilter.LengthFilter(3))
            current?.let { setText(it.toString()) }
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(24), dpToPx(8), dpToPx(24), dpToPx(8))
            addView(editText)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(dialogTitle)
            .setView(container)
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                val value = editText.text.toString().trim().toIntOrNull()
                if (value == null || value < 0 || value > max) {
                    showFreeTextInput(title, current, max, onSaved); return@setPositiveButton
                }
                onSaved(value)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
        editText.requestFocus()
    }

    // ─── Completion / results ──────────────────────────────────────────────────

    private fun checkCompletion() {
        if (playerScores.all { it.isComplete() } && !gameOver) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.forestshuffle_game_complete))
                .setMessage(getString(R.string.forestshuffle_game_complete_message))
                .setPositiveButton(getString(R.string.yes)) { _, _ ->
                    gameOver = true; buildScoreTable(); saveResultsAndShowSummary()
                }
                .setNegativeButton(getString(R.string.no), null).show()
        }
    }

    private fun saveResultsAndShowSummary() {
        val totals = playerScores.associateWith { ForestShuffleScoring.grandTotal(it, playerScores) }
        val maxScore = totals.values.maxOrNull() ?: 0
        val winners = totals.filter { it.value == maxScore }.keys
        val isDraw = winners.size > 1
        lifecycleScope.launch {
            database.gameResultDao().insertGameResults(playerScores.map { ps ->
                GameResult(gameType = GAME_TYPE, playerId = ps.playerId, playerName = ps.playerName,
                    score = totals[ps] ?: 0, isWinner = !isDraw && ps in winners, isDraw = isDraw && ps in winners)
            })
            val sorted = totals.entries.sortedByDescending { it.value }
            var rank = 1
            val entries = sorted.mapIndexed { i, (ps, s) ->
                val r = if (i > 0 && s == sorted[i - 1].value) rank else { rank = i + 1; rank }
                GameResultsDialog.PlayerResult(ps.playerName, ps.playerColor, s, r)
            }
            GameResultsDialog.show(this@ForestShuffleGameActivity, entries, isDraw, " pts") { finish() }
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun treeLabel(species: TreeSpecies): String = when (species) {
        TreeSpecies.BEECH -> getString(R.string.forestshuffle_tree_beech)
        TreeSpecies.BIRCH -> getString(R.string.forestshuffle_tree_birch)
        TreeSpecies.DOUGLAS_FIR -> getString(R.string.forestshuffle_tree_douglas_fir)
        TreeSpecies.HORSE_CHESTNUT -> getString(R.string.forestshuffle_tree_horse_chestnut)
        TreeSpecies.LINDEN -> getString(R.string.forestshuffle_tree_linden)
        TreeSpecies.OAK -> getString(R.string.forestshuffle_tree_oak)
        TreeSpecies.SILVER_FIR -> getString(R.string.forestshuffle_tree_silver_fir)
        TreeSpecies.SYCAMORE -> getString(R.string.forestshuffle_tree_sycamore)
    }

    private fun cellLayoutParams() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)

    private fun makeColumn(weight: Float, widthDp: Int): LinearLayout {
        val widthPx = if (widthDp == 0) 0 else dpToPx(widthDp)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(widthPx, LinearLayout.LayoutParams.MATCH_PARENT, weight)
        }
    }

    private fun borderDrawable(bgColor: Int): GradientDrawable = GradientDrawable().apply {
        setColor(bgColor); setStroke(1, ContextCompat.getColor(this@ForestShuffleGameActivity, R.color.cell_border))
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_forestshuffle_game, menu); return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                AlertDialog.Builder(this).setTitle(R.string.forestshuffle_quit_game).setMessage(R.string.forestshuffle_quit_game_message)
                    .setPositiveButton(R.string.yes) { _, _ -> finish() }.setNegativeButton(R.string.no, null).show(); true
            }
            R.id.action_help -> { HelpDialogs.showAppHelp(this, GAME_TYPE); true }
            else -> super.onOptionsItemSelected(item)
        }
    }
}