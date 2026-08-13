package com.github.trivialloop.scorehub.games.ligretto

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
import com.github.trivialloop.scorehub.databinding.ActivityLigrettoGameBinding
import com.github.trivialloop.scorehub.ui.GameResultsDialog
import com.github.trivialloop.scorehub.ui.HelpDialogs
import com.github.trivialloop.scorehub.utils.LocaleHelper
import com.github.trivialloop.scorehub.utils.ScoreColorRole
import kotlinx.coroutines.launch

class LigrettoGameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLigrettoGameBinding
    private lateinit var database: AppDatabase

    private lateinit var playerIds: LongArray
    private lateinit var playerNames: Array<String>
    private lateinit var playerColors: IntArray
    private lateinit var players: List<LigrettoPlayerState>

    private val rounds = mutableListOf<LigrettoRound>()
    private var gameOver = false

    companion object {
        const val GAME_TYPE = "ligretto"
        private const val SCORE_LIMIT = 100
        private const val MAX_CARDS_PLAYED = 40
        private const val MAX_STACK_LEFT = 10
        private const val LABEL_COL_DP = 65
        private const val ROW_HEIGHT_DP = 48
    }

    override fun attachBaseContext(newBase: Context) {
        val language = LocaleHelper.getPersistedLocale(newBase)
        super.attachBaseContext(LocaleHelper.setLocale(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLigrettoGameBinding.inflate(layoutInflater)
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

        database     = AppDatabase.getDatabase(this)
        playerIds    = intent.getLongArrayExtra("PLAYER_IDS")     ?: longArrayOf()
        playerNames  = intent.getStringArrayExtra("PLAYER_NAMES") ?: arrayOf()
        playerColors = intent.getIntArrayExtra("PLAYER_COLORS")   ?: intArrayOf()

        players = playerIds.indices.map { i ->
            LigrettoPlayerState(playerIds[i], playerNames[i], playerColors[i])
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.ligretto_game)

        rounds.add(LigrettoRound(1))
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
        rounds.forEachIndexed { index, round ->
            binding.tableContainer.addView(
                buildRoundRow(round, isLast = index == rounds.lastIndex, isPrev = index == rounds.lastIndex - 1)
            )
        }
        binding.tableContainer.addView(buildTotalRow())

        binding.scrollView.post { binding.scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun buildHeaderRow(): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val nameRow = makeFixedRow()
        nameRow.addView(makeRoundLabelCell(""))
        for (player in players) {
            nameRow.addView(makePlayerNameHeaderCell(player.playerName, player.playerColor, weight = 2f))
        }
        container.addView(nameRow)

        val subRow = makeFixedRow()
        subRow.addView(makeRoundLabelCell("#"))
        repeat(players.size) {
            subRow.addView(makeSubHeaderCell(getString(R.string.ligretto_played), weight = 1f))
            subRow.addView(makeSubHeaderCell(getString(R.string.ligretto_left), weight = 1f))
        }
        container.addView(subRow)
        return container
    }

    private fun buildRoundRow(round: LigrettoRound, isLast: Boolean, isPrev: Boolean): LinearLayout {
        val row = makeFixedRow()
        val playerIdList = players.map { it.playerId }
        val currentRound = rounds.last()
        val prevEditable = isPrev && !gameOver && !currentRound.allScoresEntered(playerIdList).let {
            // Previous round stays editable until the current round has any entry at all
            currentRound.cardsPlayed.values.any { v -> v != null } ||
                    currentRound.stackLeft.values.any { v -> v != null }
        }

        val labelCell = makeRoundLabelCell(round.roundNumber.toString())
        val finisher = players.find { it.playerId == round.finisherId }
        if (finisher != null) {
            labelCell.background = cellDrawable(finisher.playerColor)
            labelCell.setTextColor(Color.WHITE)
        }
        if (isLast && !gameOver) {
            labelCell.setOnClickListener { showFinisherPicker(round) }
        }
        row.addView(labelCell)

        // Per-column coloring: compare all players' raw values for this round,
        // independently for "Played" and "Left" (see docs/games/ligretto.md).
        val allPlayed = players.map { round.cardsPlayed[it.playerId] }
        val allLeft   = players.map { round.stackLeft[it.playerId] }

        for (player in players) {
            val played = round.cardsPlayed[player.playerId]
            val left   = round.stackLeft[player.playerId]

            val canEnter    = isLast && !gameOver
            val canEditPrev = isPrev && !gameOver && prevEditable

            val editable = canEnter || canEditPrev

            val playedRole = ScoreColorRole(played, allPlayed, higherIsBetter = true)
            val leftRole   = ScoreColorRole(left, allLeft, higherIsBetter = false)

            row.addView(makeSubScoreCell(
                text      = played?.toString() ?: "",
                canEdit   = editable,
                filled    = played != null,
                colorRole = playedRole
            ) {
                showNumberPicker(
                    title    = "${player.playerName} — ${getString(R.string.ligretto_played)}",
                    current  = played,
                    maxValue = MAX_CARDS_PLAYED
                ) { value ->
                    round.cardsPlayed[player.playerId] = value
                    onRoundValueEntered(round)
                }
            })

            row.addView(makeSubScoreCell(
                text      = left?.toString() ?: "",
                canEdit   = editable,
                filled    = left != null,
                colorRole = leftRole
            ) {
                showNumberPicker(
                    title    = "${player.playerName} — ${getString(R.string.ligretto_left)}",
                    current  = left,
                    maxValue = MAX_STACK_LEFT
                ) { value ->
                    round.stackLeft[player.playerId] = value
                    onRoundValueEntered(round)
                }
            })
        }
        return row
    }

    private fun buildTotalRow(): LinearLayout {
        val row = makeFixedRow()
        val totalValues = players.map { it.getTotal(rounds) }
        row.addView(makeRoundLabelCell(getString(R.string.ligretto_total)))

        for (player in players) {
            val total = player.getTotal(rounds)
            val role  = ScoreColorRole(total, totalValues, higherIsBetter = true)
            val group = LinearLayout(this).apply {
                orientation  = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 2f)
            }
            val cell = makeTotalCell(total.toString())
            if (gameOver && role != ScoreColorRole.NEUTRAL) {
                cell.setTextColor(role.toColor(this))
            }
            group.addView(cell)
            row.addView(group)
        }
        return row
    }

    // ─── Dialogs ───────────────────────────────────────────────────────────────

    private fun showFinisherPicker(round: LigrettoRound) {
        val names = players.map { it.playerName }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.ligretto_who_called))
            .setItems(names) { _, which ->
                round.finisherId = players[which].playerId
                buildTable()
            }
            .show()
    }

    private fun showNumberPicker(title: String, current: Int?, maxValue: Int, onPicked: (Int) -> Unit) {
        val dialogTitle = if (current != null) "✏️ $title" else title
        val values = (0..maxValue).toList()
        val items = values.map { it.toString() }.toTypedArray()

        val dialog = AlertDialog.Builder(this)
            .setTitle(dialogTitle)
            .setItems(items) { _, which -> onPicked(values[which]) }
            .create()
        dialog.show()
        if (current != null) dialog.listView?.setSelection(current)
    }

    private fun onRoundValueEntered(round: LigrettoRound) {
        buildTable()
        val playerIdList = players.map { it.playerId }
        if (round === rounds.last() && round.isComplete(playerIdList)) {
            checkEndOfRound()
        }
    }

    // ─── Game logic ────────────────────────────────────────────────────────────

    private fun checkEndOfRound() {
        val maxTotal = players.maxOf { it.getTotal(rounds) }
        if (maxTotal >= SCORE_LIMIT) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.ligretto_game_over_title))
                .setMessage(getString(R.string.ligretto_game_over_confirm))
                .setPositiveButton(getString(R.string.yes)) { _, _ ->
                    gameOver = true; buildTable(); saveResultsAndShowSummary()
                }
                .setNegativeButton(getString(R.string.no)) { _, _ ->
                    rounds.add(LigrettoRound(rounds.size + 1)); buildTable()
                }
                .show()
        } else {
            rounds.add(LigrettoRound(rounds.size + 1)); buildTable()
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
            GameResultsDialog.show(this@LigrettoGameActivity, entries, isDraw, " pts") { finish() }
        }
    }

    // ─── Cell builders ─────────────────────────────────────────────────────────

    private fun makeFixedRow(): LinearLayout = LinearLayout(this).apply {
        orientation  = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(ROW_HEIGHT_DP))
    }

    private fun makeRoundLabelCell(text: String): TextView = TextView(this).apply {
        this.text = text; gravity = Gravity.CENTER; textSize = 12f; setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(dpToPx(LABEL_COL_DP), LinearLayout.LayoutParams.MATCH_PARENT)
        background = cellDrawable(ContextCompat.getColor(this@LigrettoGameActivity, R.color.header_cell_background))
        setTextColor(ContextCompat.getColor(this@LigrettoGameActivity, R.color.header_cell_text))
    }

    private fun makePlayerNameHeaderCell(name: String, color: Int, weight: Float): TextView = TextView(this).apply {
        text = name; gravity = Gravity.CENTER; textSize = 13f; setTypeface(null, Typeface.BOLD)
        maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight)
        background = cellDrawable(color); setTextColor(Color.WHITE)
    }

    private fun makeSubHeaderCell(label: String, weight: Float): TextView = TextView(this).apply {
        text = label; gravity = Gravity.CENTER; textSize = 9f; setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight)
        background = cellDrawable(ContextCompat.getColor(this@LigrettoGameActivity, R.color.header_cell_background))
        setTextColor(ContextCompat.getColor(this@LigrettoGameActivity, R.color.header_cell_text))
    }

    private fun makeSubScoreCell(
        text: String,
        canEdit: Boolean,
        filled: Boolean,
        colorRole: ScoreColorRole = ScoreColorRole.NEUTRAL,
        onClick: () -> Unit
    ): TextView =
        TextView(this).apply {
            this.text = text; gravity = Gravity.CENTER; textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            val bgColor = when {
                canEdit && !filled -> ContextCompat.getColor(this@LigrettoGameActivity, R.color.cell_editable_bg)
                canEdit && filled  -> ContextCompat.getColor(this@LigrettoGameActivity, R.color.cell_editable_filled_bg)
                else               -> ContextCompat.getColor(this@LigrettoGameActivity, R.color.score_cell_background)
            }
            background = cellDrawable(bgColor)
            setTextColor(colorRole.toColor(this@LigrettoGameActivity))
            if (colorRole != ScoreColorRole.NEUTRAL && filled) setTypeface(null, Typeface.BOLD)
            if (canEdit) setOnClickListener { onClick() } else alpha = if (filled) 0.75f else 1f
        }

    private fun makeTotalCell(text: String): TextView = TextView(this).apply {
        this.text = text; gravity = Gravity.CENTER; textSize = 15f; setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        background = cellDrawable(ContextCompat.getColor(this@LigrettoGameActivity, R.color.cell_calculated_bg))
        setTextColor(ContextCompat.getColor(this@LigrettoGameActivity, R.color.score_calculated_cell_text))
    }

    private fun cellDrawable(bgColor: Int): GradientDrawable = GradientDrawable().apply {
        setColor(bgColor)
        setStroke(1, ContextCompat.getColor(this@LigrettoGameActivity, R.color.cell_border))
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private fun showQuitGameDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.ligretto_quit_game)
            .setMessage(R.string.ligretto_quit_game_message)
            .setPositiveButton(R.string.yes) { _, _ -> finish() }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_ligretto_game, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { showQuitGameDialog(); true }
            R.id.action_help -> { HelpDialogs.showAppHelp(this, GAME_TYPE); true }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
