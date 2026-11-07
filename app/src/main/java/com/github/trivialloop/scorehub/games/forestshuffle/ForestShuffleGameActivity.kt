package com.github.trivialloop.scorehub.games.forestshuffle

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
import com.github.trivialloop.scorehub.databinding.ActivityForestshuffleGameBinding
import com.github.trivialloop.scorehub.ui.GameResultsDialog
import com.github.trivialloop.scorehub.ui.HelpDialogs
import com.github.trivialloop.scorehub.utils.GameSymbols
import com.github.trivialloop.scorehub.utils.LocaleHelper
import com.github.trivialloop.scorehub.utils.ScoreColorRole
import kotlinx.coroutines.launch

class ForestShuffleGameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityForestshuffleGameBinding
    private lateinit var database: AppDatabase
    private lateinit var playerIds: LongArray
    private lateinit var playerNames: Array<String>
    private lateinit var playerColors: IntArray
    private lateinit var players: List<ForestShufflePlayerScore>

    private var gameOver = false

    companion object {
        const val GAME_TYPE = "forestshuffle"
        private const val LABEL_COL_DP = GameSymbols.LABEL_COL_DP_ONE_SHOT
        private const val ROW_HEIGHT_DP = 44
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
            ForestShufflePlayerScore(playerIds[i], playerNames[i], playerColors[i])
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.forestshuffle_game)

        binding.btnFinishGame.setOnClickListener { confirmFinishGame() }

        buildTable()

        onBackPressedDispatcher.addCallback(this) {
            showQuitGameDialog()
        }
    }

    private fun categoryStyle(c: ForestShuffleCategory) = when (c) {
        ForestShuffleCategory.TREES      -> GameSymbols.DECIDUOUS_TREE
        ForestShuffleCategory.TOP_BOTTOM -> GameSymbols.BLOSSOM
        ForestShuffleCategory.LEFT_RIGHT -> GameSymbols.DEER
        ForestShuffleCategory.CAVE       -> GameSymbols.ROCK
    }

    // ─── Table construction ────────────────────────────────────────────────────

    private fun buildTable() {
        binding.headerContainer.removeAllViews()
        binding.headerContainer.addView(buildHeaderRow())

        binding.tableContainer.removeAllViews()

        for (category in ForestShuffleCategory.entries) {
            binding.tableContainer.addView(buildCategoryRow(category))
        }

        binding.tableContainer.addView(buildTotalRow())

        binding.btnFinishGame.isEnabled = !gameOver
        binding.btnFinishGame.alpha = if (gameOver) 0.5f else 1f

        binding.scrollView.post { binding.scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    // ─── Rows ─────────────────────────────────────────────────────────────────

    private fun buildHeaderRow(): LinearLayout {
        val row = makeRow(ROW_HEIGHT_DP)
        row.addView(makeLabelCell("", ROW_HEIGHT_DP, ContextCompat.getColor(this, R.color.header_cell_background)))
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

    private fun buildCategoryRow(category: ForestShuffleCategory): LinearLayout {
        val style = categoryStyle(category)
        val row = makeRow(ROW_HEIGHT_DP)
        row.addView(makeLabelCell(style.emoji, ROW_HEIGHT_DP, style.color))

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

    private fun buildTotalRow(): LinearLayout {
        val row = makeRow(ROW_HEIGHT_DP)
        row.addView(makeLabelCell(GameSymbols.TOTAL, ROW_HEIGHT_DP, ContextCompat.getColor(this, R.color.cell_calculated_bg), calc = true))
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

    private fun showCategoryPicker(player: ForestShufflePlayerScore, category: ForestShuffleCategory) {
        val current = player.scores[category]
        val label = categoryStyle(category).emoji
        val title = if (current != null) "✏️ ${player.playerName} — $label" else "${player.playerName} — $label"
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

    private fun confirmFinishGame() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.forestshuffle_finish_game_title))
            .setMessage(getString(R.string.forestshuffle_finish_game_message))
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
        val playedAt = System.currentTimeMillis()
        lifecycleScope.launch {
            database.gameResultDao().insertGameResults(players.map { player ->
                GameResult(
                    gameType = GAME_TYPE, playerId = player.playerId,
                    playerName = player.playerName, score = player.getTotal(),
                    isWinner = if (isSoloGame) false else (!isDraw && player in winners),
                    isDraw = if (isSoloGame) false else (isDraw && player in winners),
                    playedAt = playedAt
                )
            })
            val sorted = totals.entries.sortedByDescending { it.value }
            var rank = 1
            val entries = sorted.mapIndexed { i, (p, s) ->
                val r = if (i > 0 && s == sorted[i - 1].value) rank else { rank = i + 1; rank }
                GameResultsDialog.PlayerResult(p.playerName, p.playerColor, s, r)
            }
            GameResultsDialog.show(this@ForestShuffleGameActivity, entries, isDraw && !isSoloGame, " pts") { finish() }
        }
    }

    // ─── Cell builders ─────────────────────────────────────────────────────────

    private fun makeRow(heightDp: Int): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(heightDp))
        // Without this, LinearLayout aligns children on the text baseline of the tallest/most
        // prominent child. Rows mixing empty cells with filled/bold ones
        // then render each cell at a slightly different vertical offset.
        isBaselineAligned = false
    }

    private fun makeLabelCell(text: String, heightDp: Int, bgColor: Int, calc: Boolean = false): TextView = TextView(this).apply {
        this.text = text
        gravity = Gravity.CENTER
        textSize = if (calc) 16f else 22f
        setTypeface(null, Typeface.BOLD)
        maxLines = 1
        layoutParams = LinearLayout.LayoutParams(dpToPx(LABEL_COL_DP), dpToPx(heightDp))
        background = cellDrawable(bgColor)
        setTextColor(ContextCompat.getColor(this@ForestShuffleGameActivity,
            if (calc) R.color.score_calculated_cell_text else R.color.header_cell_text))
    }

    private fun makeCell(text: String, heightDp: Int, bold: Boolean, textSize: Float = 14f): TextView = TextView(this).apply {
        this.text = text
        gravity = Gravity.CENTER
        this.textSize = textSize
        if (bold) setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(0, dpToPx(heightDp), 1f)
        background = cellDrawable(ContextCompat.getColor(this@ForestShuffleGameActivity, R.color.score_cell_background))
        setTextColor(ContextCompat.getColor(this@ForestShuffleGameActivity, R.color.score_cell_text))
    }

    private fun cellDrawable(bgColor: Int): GradientDrawable = GradientDrawable().apply {
        setColor(bgColor)
        setStroke(1, ContextCompat.getColor(this@ForestShuffleGameActivity, R.color.cell_border))
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private fun showQuitGameDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.forestshuffle_quit_game)
            .setMessage(R.string.forestshuffle_quit_game_message)
            .setPositiveButton(R.string.yes) { _, _ -> finish() }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_forestshuffle_game, menu); return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { showQuitGameDialog(); true }
            R.id.action_help -> { HelpDialogs.showAppHelp(this, GAME_TYPE); true }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
