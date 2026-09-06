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
import android.widget.ScrollView
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
    private lateinit var players: List<HarmoniesPlayerScore>

    private var gameOver = false

    companion object {
        const val GAME_TYPE = "harmonies"
        private const val LABEL_COL_DP = 80
        private const val ROW_HEIGHT_DP = 44
        private const val ANIMAL_ROW_HEIGHT_DP = 38
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

        players = playerIds.indices.map { i ->
            HarmoniesPlayerScore(playerIds[i], playerNames[i], playerColors[i])
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.harmonies_game)

        binding.btnFinishGame.setOnClickListener { confirmFinishGame() }

        buildTable()

        onBackPressedDispatcher.addCallback(this) {
            showQuitGameDialog()
        }
    }

    // ─── Table construction ────────────────────────────────────────────────────

    private fun buildTable() {
        binding.headerContainer.removeAllViews()
        binding.headerContainer.addView(buildHeaderRow())

        binding.tableContainer.removeAllViews()

        for (category in HarmoniesCategory.entries) {
            binding.tableContainer.addView(buildCategoryRow(category))
        }

        binding.tableContainer.addView(buildAnimalsSectionHeaderRow())
        val maxAnimals = players.maxOf { it.animalEntries.size }
        for (slot in 0 until maxAnimals) {
            binding.tableContainer.addView(buildAnimalSlotRow(slot))
        }
        binding.tableContainer.addView(buildSubtotalRow(getString(R.string.harmonies_total_animals)) { it.getAnimalsTotal() })

        binding.tableContainer.addView(buildTotalRow())

        binding.btnFinishGame.isEnabled = !gameOver
        binding.btnFinishGame.alpha = if (gameOver) 0.5f else 1f

        binding.scrollView.post { binding.scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    // ─── Rows ─────────────────────────────────────────────────────────────────

    private fun buildHeaderRow(): LinearLayout {
        val row = makeRow(ROW_HEIGHT_DP)
        row.addView(makeLabelCell("", ROW_HEIGHT_DP, isCalc = false))
        for (player in players) {
            val cell = makeCell(player.playerName, ROW_HEIGHT_DP, bold = true)
            cell.background = cellDrawable(player.playerColor)
            cell.setTextColor(Color.WHITE)
            cell.maxLines = 1
            cell.ellipsize = TextUtils.TruncateAt.END
            row.addView(cell)
        }
        return row
    }

    private fun buildCategoryRow(category: HarmoniesCategory): LinearLayout {
        val row = makeRow(ROW_HEIGHT_DP)
        row.addView(makeLabelCell(categoryLabel(category), ROW_HEIGHT_DP, isCalc = false))

        val allValues = players.map { it.scores[category] }
        for (player in players) {
            val score = player.scores[category]
            val role = ScoreColorRole(score, allValues, higherIsBetter = true)
            val bgColor = if (!gameOver) R.color.cell_editable_bg else R.color.score_cell_background
            val cell = makeCell(score?.toString() ?: "", ROW_HEIGHT_DP, bold = role != ScoreColorRole.NEUTRAL && score != null)
            cell.background = cellDrawable(ContextCompat.getColor(this, bgColor))
            if (role != ScoreColorRole.NEUTRAL && score != null) cell.setTextColor(role.toColor(this))
            if (!gameOver) cell.setOnClickListener { showCategoryPicker(player, category) }
            row.addView(cell)
        }
        return row
    }

    private fun buildAnimalsSectionHeaderRow(): LinearLayout {
        val row = makeRow(ROW_HEIGHT_DP)
        row.addView(makeLabelCell(getString(R.string.harmonies_animals), ROW_HEIGHT_DP, isCalc = false))
        for (player in players) {
            val cell = makeCell(if (gameOver) "" else "+", ROW_HEIGHT_DP, bold = true)
            cell.background = cellDrawable(
                ContextCompat.getColor(this, if (!gameOver) R.color.cell_editable_bg else R.color.header_cell_background)
            )
            if (!gameOver) cell.setOnClickListener { showAddAnimalDialog(player) }
            row.addView(cell)
        }
        return row
    }

    private fun buildAnimalSlotRow(slot: Int): LinearLayout {
        val row = makeRow(ANIMAL_ROW_HEIGHT_DP)
        row.addView(makeLabelCell("", ANIMAL_ROW_HEIGHT_DP, isCalc = false))
        for (player in players) {
            val value = player.animalEntries.getOrNull(slot)
            val text = value?.let { "+$it" } ?: ""
            val cell = makeCell(text, ANIMAL_ROW_HEIGHT_DP, bold = value != null, textSize = 13f)
            if (value != null) cell.setTextColor(ContextCompat.getColor(this, R.color.score_text_best))
            cell.background = cellDrawable(ContextCompat.getColor(this, R.color.score_cell_background))
            if (!gameOver && value != null) {
                cell.setOnClickListener { showEditAnimalDialog(player, slot) }
            }
            row.addView(cell)
        }
        return row
    }

    private fun buildSubtotalRow(label: String, valueOf: (HarmoniesPlayerScore) -> Int): LinearLayout {
        val row = makeRow(ROW_HEIGHT_DP)
        row.addView(makeLabelCell(label, ROW_HEIGHT_DP, isCalc = true))
        for (player in players) {
            val value = valueOf(player)
            val cell = makeCell(value.toString(), ROW_HEIGHT_DP, bold = true)
            cell.background = cellDrawable(ContextCompat.getColor(this, R.color.cell_calculated_bg))
            cell.setTextColor(ContextCompat.getColor(this, R.color.score_calculated_cell_text))
            row.addView(cell)
        }
        return row
    }

    private fun buildTotalRow(): LinearLayout {
        val row = makeRow(ROW_HEIGHT_DP)
        row.addView(makeLabelCell(getString(R.string.harmonies_total), ROW_HEIGHT_DP, isCalc = true))
        val allTotals = players.map { it.getTotal() }
        for (player in players) {
            val total = player.getTotal()
            val cell = makeCell(total.toString(), ROW_HEIGHT_DP, bold = true, textSize = 16f)
            cell.background = cellDrawable(ContextCompat.getColor(this, R.color.cell_calculated_bg))
            var textColor = ContextCompat.getColor(this, R.color.score_calculated_cell_text)
            if (gameOver) {
                val role = ScoreColorRole(total, allTotals, higherIsBetter = true)
                if (role != ScoreColorRole.NEUTRAL) textColor = role.toColor(this)
            }
            cell.setTextColor(textColor)
            row.addView(cell)
        }
        return row
    }

    // ─── Dialogs ───────────────────────────────────────────────────────────────

    private fun showCategoryPicker(player: HarmoniesPlayerScore, category: HarmoniesCategory) {
        val current = player.scores[category]
        val title = if (current != null) "✏️ ${player.playerName} — ${categoryLabel(category)}"
                    else "${player.playerName} — ${categoryLabel(category)}"
        val values = category.getPossibleValues()
        val items = values.map { it.toString() }.toTypedArray()

        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(items) { _, which ->
                player.scores[category] = values[which]
                buildTable()
            }
            .create()
        dialog.show()
        val scrollTo = if (current != null) values.indexOf(current) else 0
        if (scrollTo >= 0) dialog.listView?.post { dialog.listView?.setSelection(scrollTo) }
    }

    private fun showAddAnimalDialog(player: HarmoniesPlayerScore) {
        val values = HARMONIES_ANIMAL_CARD_VALUES
        val items = values.map { it.toString() }.toTypedArray()
        val title = "${player.playerName} — ${getString(R.string.harmonies_add_animal_title)}"

        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(items) { _, which ->
                player.animalEntries.add(values[which])
                buildTable()
            }
            .show()
    }

    private fun showEditAnimalDialog(player: HarmoniesPlayerScore, slot: Int) {
        val current = player.animalEntries.getOrNull(slot) ?: return

        AlertDialog.Builder(this)
            .setTitle("✏️ ${player.playerName}")
            .setItems(arrayOf(
                getString(R.string.harmonies_edit_entry, current),
                getString(R.string.harmonies_delete_entry)
            )) { _, which ->
                when (which) {
                    0 -> showAnimalValuePicker(player, slot, current)
                    1 -> { player.animalEntries.removeAt(slot); buildTable() }
                }
            }
            .show()
    }

    private fun showAnimalValuePicker(player: HarmoniesPlayerScore, slot: Int, current: Int) {
        val values = HARMONIES_ANIMAL_CARD_VALUES
        val items = values.map { it.toString() }.toTypedArray()

        val dialog = AlertDialog.Builder(this)
            .setTitle("✏️ ${player.playerName} — ${getString(R.string.harmonies_add_animal_title)}")
            .setItems(items) { _, which ->
                player.animalEntries[slot] = values[which]
                buildTable()
            }
            .create()
        dialog.show()
        val idx = values.indexOf(current)
        if (idx >= 0) dialog.listView?.post { dialog.listView?.setSelection(idx) }
    }

    private fun confirmFinishGame() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.harmonies_finish_game_title))
            .setMessage(getString(R.string.harmonies_finish_game_message))
            .setPositiveButton(getString(R.string.yes)) { _, _ ->
                gameOver = true
                buildTable()
                saveResultsAndShowSummary()
            }
            .setNegativeButton(getString(R.string.no), null)
            .show()
    }

    // ─── Save results ──────────────────────────────────────────────────────────

    private fun saveResultsAndShowSummary() {
        val isSoloGame = players.size == 1
        val totals = players.associate { it to it.getTotal() }
        val maxScore = totals.values.maxOrNull() ?: 0
        val winners = totals.filter { it.value == maxScore }.keys
        val isDraw = winners.size > 1
        lifecycleScope.launch {
            database.gameResultDao().insertGameResults(players.map { player ->
                GameResult(
                    gameType = GAME_TYPE, playerId = player.playerId,
                    playerName = player.playerName, score = player.getTotal(),
                    isWinner = if (isSoloGame) false else (!isDraw && player in winners),
                    isDraw = if (isSoloGame) false else (isDraw && player in winners)
                )
            })
            val sorted = totals.entries.sortedByDescending { it.value }
            var rank = 1
            val entries = sorted.mapIndexed { i, (p, s) ->
                val r = if (i > 0 && s == sorted[i - 1].value) rank else { rank = i + 1; rank }
                GameResultsDialog.PlayerResult(p.playerName, p.playerColor, s, r)
            }
            GameResultsDialog.show(this@HarmoniesGameActivity, entries, isDraw && !isSoloGame, " pts") { finish() }
        }
    }

    // ─── Cell builders ─────────────────────────────────────────────────────────

    private fun makeRow(heightDp: Int): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(heightDp))
        // Without this, LinearLayout aligns children on the text baseline of the tallest/most
        // prominent child. Rows mixing empty cells (no animal card yet) with filled/bold ones
        // then render each cell at a slightly different vertical offset.
        isBaselineAligned = false
    }

    private fun makeLabelCell(text: String, heightDp: Int, isCalc: Boolean): TextView = TextView(this).apply {
        this.text = text
        gravity = Gravity.CENTER
        textSize = 11f
        setTypeface(null, Typeface.BOLD)
        maxLines = 3
        setPadding(dpToPx(4), 0, dpToPx(4), 0)
        layoutParams = LinearLayout.LayoutParams(dpToPx(LABEL_COL_DP), dpToPx(heightDp))
        val bg = if (isCalc) R.color.cell_calculated_bg else R.color.header_cell_background
        val fg = if (isCalc) R.color.score_calculated_cell_text else R.color.header_cell_text
        background = cellDrawable(ContextCompat.getColor(this@HarmoniesGameActivity, bg))
        setTextColor(ContextCompat.getColor(this@HarmoniesGameActivity, fg))
    }

    private fun makeCell(text: String, heightDp: Int, bold: Boolean, textSize: Float = 14f): TextView =
        TextView(this).apply {
            this.text = text
            gravity = Gravity.CENTER
            this.textSize = textSize
            if (bold) setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(heightDp), 1f)
            background = cellDrawable(ContextCompat.getColor(this@HarmoniesGameActivity, R.color.score_cell_background))
            setTextColor(ContextCompat.getColor(this@HarmoniesGameActivity, R.color.score_cell_text))
        }

    private fun categoryLabel(category: HarmoniesCategory): String = when (category) {
        HarmoniesCategory.TREES -> getString(R.string.harmonies_trees)
        HarmoniesCategory.MOUNTAINS -> getString(R.string.harmonies_mountains)
        HarmoniesCategory.FIELDS -> getString(R.string.harmonies_fields)
        HarmoniesCategory.BUILDINGS -> getString(R.string.harmonies_buildings)
        HarmoniesCategory.RIVER -> getString(R.string.harmonies_river)
    }

    private fun cellDrawable(bgColor: Int): GradientDrawable = GradientDrawable().apply {
        setColor(bgColor)
        setStroke(1, ContextCompat.getColor(this@HarmoniesGameActivity, R.color.cell_border))
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
