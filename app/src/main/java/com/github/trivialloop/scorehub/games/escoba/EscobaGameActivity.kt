package com.github.trivialloop.scorehub.games.escoba

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
import com.github.trivialloop.scorehub.databinding.ActivityEscobaGameBinding
import com.github.trivialloop.scorehub.ui.GameResultsDialog
import com.github.trivialloop.scorehub.ui.HelpDialogs
import com.github.trivialloop.scorehub.utils.LocaleHelper
import com.github.trivialloop.scorehub.utils.ScoreColorRole
import kotlinx.coroutines.launch

class EscobaGameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEscobaGameBinding
    private lateinit var database: AppDatabase

    private lateinit var playerIds: LongArray
    private lateinit var playerNames: Array<String>
    private lateinit var playerColors: IntArray
    private lateinit var players: List<EscobaPlayerState>

    private val rounds = mutableListOf<EscobaRound>()
    private var gameOver = false

    companion object {
        const val GAME_TYPE = "escoba"
        private const val SCORE_LIMIT = 21
        private const val ROW_HEIGHT_DP = 48
    }

    override fun attachBaseContext(newBase: Context) {
        val language = LocaleHelper.getPersistedLocale(newBase)
        super.attachBaseContext(LocaleHelper.setLocale(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEscobaGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            binding.appBarLayout.setPadding(0, statusBarInsets.top, 0, 0)
            insets
        }

        database     = AppDatabase.getDatabase(this)
        playerIds    = intent.getLongArrayExtra("PLAYER_IDS")     ?: longArrayOf()
        playerNames  = intent.getStringArrayExtra("PLAYER_NAMES") ?: arrayOf()
        playerColors = intent.getIntArrayExtra("PLAYER_COLORS")   ?: intArrayOf()

        players = playerIds.indices.map { i ->
            EscobaPlayerState(playerIds[i], playerNames[i], playerColors[i])
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.escoba_game)

        rounds.add(EscobaRound(1))
        buildTable()
    }

    // ─── Table construction ────────────────────────────────────────────────────

    private fun buildTable() {
        val headerRow = buildHeaderRow()
        val roundRows = rounds.mapIndexed { index, round ->
            buildRoundRow(round, isLast = index == rounds.lastIndex)
        }
        val totalRow = buildTotalRow()

        val screenHeight       = resources.displayMetrics.heightPixels
        val appBarHeight       = binding.toolbar.layoutParams?.height?.takeIf { it > 0 } ?: dpToPx(56)
        val rowHeight          = dpToPx(ROW_HEIGHT_DP)
        val totalNaturalHeight = rowHeight * (roundRows.size + 2)

        if (totalNaturalHeight > screenHeight - appBarHeight) {
            binding.headerContainer.removeAllViews()
            binding.headerContainer.addView(headerRow)

            binding.tableContainer.removeAllViews()
            roundRows.forEach { binding.tableContainer.addView(it) }

            binding.totalContainer.removeAllViews()
            binding.totalContainer.addView(totalRow)

            binding.scrollView.post { binding.scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
        } else {
            binding.headerContainer.removeAllViews()
            binding.totalContainer.removeAllViews()

            binding.tableContainer.removeAllViews()
            binding.tableContainer.addView(headerRow)
            roundRows.forEach { binding.tableContainer.addView(it) }
            binding.tableContainer.addView(totalRow)
        }
    }

    private fun buildHeaderRow(): LinearLayout {
        val row = makeRow()
        row.addView(makeLabelCell(getString(R.string.escoba_round_label)))
        for (player in players) {
            val cell = makeHeaderPlayerCell(player.playerName)
            cell.background = cellDrawable(player.playerColor)
            row.addView(cell)
        }
        return row
    }

    private fun buildRoundRow(round: EscobaRound, isLast: Boolean): LinearLayout {
        val row = makeRow()
        val playerIdList = players.map { it.playerId }

        row.addView(makeLabelCell(round.roundNumber.toString()))

        for (player in players) {
            val score   = round.scores[player.playerId]
            val canEdit = isLast && !gameOver

            val bgColor = when {
                canEdit && score == null -> ContextCompat.getColor(this, R.color.cell_editable_bg)
                canEdit && score != null -> ContextCompat.getColor(this, R.color.cell_editable_filled_bg)
                else                     -> ContextCompat.getColor(this, R.color.score_cell_background)
            }

            val cell = makeScoreCell(score?.toString() ?: "")
            cell.background = cellDrawable(bgColor)
            if (canEdit) cell.setOnClickListener { showScoreInput(round, player) }
            row.addView(cell)
        }
        return row
    }

    private fun buildTotalRow(): LinearLayout {
        val row = makeRow()
        row.addView(makeLabelCell(getString(R.string.escoba_total)))
        val totals = players.map { it.getTotal(rounds) }
        for ((i, player) in players.withIndex()) {
            val total = totals[i]
            val cell  = makeScoreCell(total.toString(), bold = true)
            if (gameOver) {
                val role = ScoreColorRole(total, totals, higherIsBetter = true)
                if (role != ScoreColorRole.NEUTRAL) {
                    cell.setTextColor(role.toColor(this))
                    cell.setTypeface(null, Typeface.BOLD)
                }
            }
            row.addView(cell)
        }
        return row
    }

    // ─── Dialogs ───────────────────────────────────────────────────────────────

    private fun showScoreInput(round: EscobaRound, player: EscobaPlayerState) {
        val values = (0..30).toList()
        val items  = values.map { it.toString() }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("${player.playerName} — ${getString(R.string.escoba_enter_score)}")
            .setItems(items) { _, which ->
                round.scores[player.playerId] = values[which]
                buildTable()
                val playerIdList = players.map { it.playerId }
                if (round.isComplete(playerIdList)) checkEndOfRound()
            }
            .show()
    }

    // ─── Game logic ────────────────────────────────────────────────────────────

    private fun checkEndOfRound() {
        val maxTotal = players.maxOf { it.getTotal(rounds) }
        if (maxTotal >= SCORE_LIMIT) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.escoba_game_over_title))
                .setMessage(getString(R.string.escoba_game_over_confirm))
                .setPositiveButton(getString(R.string.yes)) { _, _ ->
                    gameOver = true; buildTable(); saveResultsAndShowSummary()
                }
                .setNegativeButton(getString(R.string.no)) { _, _ ->
                    rounds.add(EscobaRound(rounds.size + 1)); buildTable()
                }
                .show()
        } else {
            rounds.add(EscobaRound(rounds.size + 1)); buildTable()
        }
    }

    private fun saveResultsAndShowSummary() {
        val totals   = players.associate { it to it.getTotal(rounds) }
        val maxScore = totals.values.maxOrNull() ?: 0
        val winners  = totals.filter { it.value == maxScore }.keys
        val isDraw   = winners.size > 1
        lifecycleScope.launch {
            database.gameResultDao().insertGameResults(players.map { player ->
                GameResult(
                    gameType   = GAME_TYPE, playerId = player.playerId,
                    playerName = player.playerName, score = player.getTotal(rounds),
                    isWinner   = !isDraw && player in winners,
                    isDraw     = isDraw && player in winners
                )
            })
            val sorted = totals.entries.sortedByDescending { it.value }
            var rank = 1
            val entries = sorted.mapIndexed { i, (p, s) ->
                val r = if (i > 0 && s == sorted[i - 1].value) rank else { rank = i + 1; rank }
                GameResultsDialog.PlayerResult(p.playerName, p.playerColor, s, r)
            }
            GameResultsDialog.show(this@EscobaGameActivity, entries, isDraw, " pts") { finish() }
        }
    }

    // ─── Cell helpers ──────────────────────────────────────────────────────────

    private fun makeRow(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    }

    private fun makeLabelCell(text: String): TextView = TextView(this).apply {
        this.text = text; gravity = Gravity.CENTER
        setPadding(dpToPx(4), dpToPx(8), dpToPx(4), dpToPx(8))
        textSize = 13f; setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(dpToPx(42), LinearLayout.LayoutParams.WRAP_CONTENT)
        background = cellDrawable(ContextCompat.getColor(this@EscobaGameActivity, R.color.header_cell_background))
        setTextColor(ContextCompat.getColor(this@EscobaGameActivity, R.color.header_cell_text))
    }

    private fun makeHeaderPlayerCell(text: String): TextView = TextView(this).apply {
        this.text = text; gravity = Gravity.CENTER
        setPadding(dpToPx(4), dpToPx(8), dpToPx(4), dpToPx(8))
        textSize = 13f; setTypeface(null, Typeface.BOLD)
        maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        setTextColor(Color.WHITE)
    }

    private fun makeScoreCell(text: String, bold: Boolean = false): TextView = TextView(this).apply {
        this.text = text; gravity = Gravity.CENTER
        setPadding(dpToPx(4), dpToPx(8), dpToPx(4), dpToPx(8))
        textSize = 14f; if (bold) setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        background = cellDrawable(ContextCompat.getColor(this@EscobaGameActivity, R.color.score_cell_background))
        setTextColor(ContextCompat.getColor(this@EscobaGameActivity, R.color.score_cell_text))
    }

    private fun cellDrawable(bgColor: Int): GradientDrawable = GradientDrawable().apply {
        setColor(bgColor)
        setStroke(1, ContextCompat.getColor(this@EscobaGameActivity, R.color.cell_border))
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_escoba_game, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                AlertDialog.Builder(this)
                    .setTitle(R.string.escoba_quit_game)
                    .setMessage(R.string.escoba_quit_game_message)
                    .setPositiveButton(R.string.yes) { _, _ -> finish() }
                    .setNegativeButton(R.string.no, null)
                    .show()
                true
            }
            R.id.action_help -> { HelpDialogs.showAppHelp(this, GAME_TYPE); true }
            else -> super.onOptionsItemSelected(item)
        }
    }
}