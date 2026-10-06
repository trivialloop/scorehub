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
import android.view.View
import android.widget.FrameLayout
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
        private const val SCORE_LIMIT          = 100
        private const val MAX_CARDS_PLAYED     = 40
        private const val MAX_STACK_LEFT       = 10
        private const val LABEL_COL_DP         = 65
        private const val ROW_HEIGHT_DP        = 48
        private const val SUB_HEADER_HEIGHT_DP = 28
        private const val PENALTY_PER_CARD     = 2
        private const val SYMBOL_PLAYED        = "🃏"
        private const val SYMBOL_LEFT          = "📚"
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
        binding.headerContainer.addView(buildProgressRow())

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
        val row = LinearLayout(this).apply {
            orientation       = LinearLayout.HORIZONTAL
            isBaselineAligned = false
            layoutParams      = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(ROW_HEIGHT_DP + SUB_HEADER_HEIGHT_DP))
        }

        // One single label cell spanning both header lines
        row.addView(makeRoundLabelCell(""))

        players.forEachIndexed { index, player ->
            if (index > 0) row.addView(makeVerticalDivider())
            row.addView(buildPlayerHeaderBlock(player))
        }
        return row
    }

    /** Name on top, then the two symbols below, in the player's color. */
    private fun buildPlayerHeaderBlock(player: LigrettoPlayerState): LinearLayout = LinearLayout(this).apply {
        orientation  = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 2f)

        addView(makePlayerNameHeaderCell(player.playerName, player.playerColor))

        addView(LinearLayout(this@LigrettoGameActivity).apply {
            orientation       = LinearLayout.HORIZONTAL
            isBaselineAligned = false
            layoutParams      = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(SUB_HEADER_HEIGHT_DP))
            addView(makeSubHeaderCell(SYMBOL_PLAYED, player.playerColor))
            addView(makeVerticalDivider())
            addView(makeSubHeaderCell(SYMBOL_LEFT, player.playerColor))
        })
    }

    private fun buildProgressRow() = ScoreProgressHelper.buildRow(
        context      = this,
        labelColDp   = LABEL_COL_DP,
        limit        = SCORE_LIMIT,
        entries      = players.map { ScoreProgressHelper.Entry(it.getTotal(rounds), it.playerColor) },
        columnWeight = 2f,
        withDividers = true
    )

    private fun buildRoundRow(round: LigrettoRound, isLast: Boolean, isPrev: Boolean): LinearLayout {
        val row = makeRow()
        val currentRound = rounds.last()
        // Previous round stays editable until the current round has any entry at all
        val prevEditable = isPrev && !gameOver && !currentRound.hasAnyEntry()
        val editable = (isLast && !gameOver) || prevEditable

        // Label cell tinted with the color of whoever called "Ligretto!"
        val finisher = players.find { it.playerId == round.finisherId }
        val labelCell = makeRoundLabelCell(round.roundNumber.toString(), tint = finisher?.playerColor)
        if (isLast && !gameOver) labelCell.setOnClickListener { showFinisherPicker(round) }
        row.addView(labelCell)

        // Colors are computed on the points each column gives, per round
        val allPlayedPts = players.map { round.cardsPlayed[it.playerId] }
        val allLeftPts   = players.map { p -> round.stackLeft[p.playerId]?.let { -PENALTY_PER_CARD * it } }

        players.forEachIndexed { index, player ->
            if (index > 0) row.addView(makeVerticalDivider())

            val played = round.cardsPlayed[player.playerId]
            val left   = round.stackLeft[player.playerId]
            val leftPts = left?.let { -PENALTY_PER_CARD * it }

            val playedRole = ScoreColorRole(played, allPlayedPts, higherIsBetter = true)
            val leftRole   = ScoreColorRole(leftPts, allLeftPts, higherIsBetter = true)

            // ── Played cards: +1 point each ──────────────────────────────────
            row.addView(makeScoreCell(
                points    = played,
                count     = played,
                showCount = false,   // same number as the points, would be redundant
                canEdit   = editable,
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

            row.addView(makeVerticalDivider())

            // ── Cards left in the Ligretto stack: −2 points each ─────────────
            row.addView(makeScoreCell(
                points    = leftPts,
                count     = left,
                showCount = true,
                canEdit   = editable,
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
        val row         = makeRow()
        val totalValues = players.map { it.getTotal(rounds) }
        row.addView(makeRoundLabelCell(getString(R.string.ligretto_total)))

        players.forEachIndexed { index, player ->
            if (index > 0) row.addView(makeVerticalDivider())

            val total = player.getTotal(rounds)
            val role  = ScoreColorRole(total, totalValues, higherIsBetter = true)
            val cell  = makeTotalCell(total.toString())
            if (gameOver && role != ScoreColorRole.NEUTRAL) cell.setTextColor(role.toColor(this))
            row.addView(cell)
        }
        return row
    }

    private fun LigrettoRound.hasAnyEntry(): Boolean =
        cardsPlayed.values.any { it != null } || stackLeft.values.any { it != null }

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
        val playedAt = System.currentTimeMillis()
        lifecycleScope.launch {
            database.gameResultDao().insertGameResults(players.map { player ->
                GameResult(
                    gameType   = GAME_TYPE, playerId = player.playerId,
                    playerName = player.playerName, score = player.getTotal(rounds),
                    isWinner   = !isDraw && player in winners,
                    isDraw     = isDraw && player in winners,
                    playedAt   = playedAt
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

    private fun makeRow(heightDp: Int = ROW_HEIGHT_DP): LinearLayout = LinearLayout(this).apply {
        orientation       = LinearLayout.HORIZONTAL
        isBaselineAligned = false
        layoutParams      = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(heightDp))
    }

    /** Label cell; [tint] (player color) marks who called "Ligretto!" first. */
    private fun makeRoundLabelCell(text: String, tint: Int? = null): TextView = TextView(this).apply {
        this.text = text; gravity = Gravity.CENTER; textSize = 12f; setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(dpToPx(LABEL_COL_DP), LinearLayout.LayoutParams.MATCH_PARENT)
        background = cellDrawable(
            tint ?: ContextCompat.getColor(this@LigrettoGameActivity, R.color.header_cell_background))
        setTextColor(
            if (tint != null) Color.WHITE
            else ContextCompat.getColor(this@LigrettoGameActivity, R.color.header_cell_text))
    }

    private fun makePlayerNameHeaderCell(name: String, color: Int): TextView = TextView(this).apply {
        text = name; gravity = Gravity.CENTER; textSize = 13f; setTypeface(null, Typeface.BOLD)
        maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(ROW_HEIGHT_DP))
        background = cellDrawable(color); setTextColor(Color.WHITE)
    }

    private fun makeSubHeaderCell(symbol: String, color: Int): TextView = TextView(this).apply {
        text = symbol; gravity = Gravity.CENTER; textSize = 14f
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        background = GradientDrawable().apply { setColor(color) }
        setTextColor(Color.WHITE)
    }

    /** 1dp vertical line, same color as the cell borders. */
    private fun makeVerticalDivider(): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(dpToPx(1), LinearLayout.LayoutParams.MATCH_PARENT)
        setBackgroundColor(ContextCompat.getColor(this@LigrettoGameActivity, R.color.cell_border))
    }

    /**
    * Score cell, same look as Cactus:
    *  - center       : points given by the entry (large, colored by [colorRole])
    *  - bottom-right : number of cards selected (tiny, light grey), if [showCount]
    */
    private fun makeScoreCell(
        points: Int?,
        count: Int?,
        showCount: Boolean,
        canEdit: Boolean,
        colorRole: ScoreColorRole,
        onClick: () -> Unit
    ): FrameLayout {
        val filled = points != null
        val bgColor = ContextCompat.getColor(this, when {
            canEdit && !filled -> R.color.cell_editable_bg
            canEdit && filled  -> R.color.cell_editable_filled_bg
            else               -> R.color.score_cell_background
        })

        return FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            background = cellDrawable(bgColor)

            // Center: points
            addView(TextView(this@LigrettoGameActivity).apply {
                text = points?.let { if (it > 0) "+$it" else it.toString() } ?: ""
                gravity = Gravity.CENTER; textSize = 16f
                setTextColor(colorRole.toColor(this@LigrettoGameActivity))
                if (colorRole != ScoreColorRole.NEUTRAL && filled) setTypeface(null, Typeface.BOLD)
                if (!canEdit && filled) alpha = 0.75f
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            })

            // Bottom-right: number of cards (tiny, light grey)
            if (showCount && count != null) {
                addView(TextView(this@LigrettoGameActivity).apply {
                    text = count.toString(); textSize = 11f; alpha = 0.45f
                    setTextColor(ContextCompat.getColor(this@LigrettoGameActivity, R.color.score_cell_text))
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.BOTTOM or Gravity.END
                    ).also { it.marginEnd = dpToPx(4); it.bottomMargin = dpToPx(2) }
                })
            }

            if (canEdit) setOnClickListener { onClick() }
        }
    }

    private fun makeTotalCell(text: String): TextView = TextView(this).apply {
        this.text = text; gravity = Gravity.CENTER; textSize = 18f; setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 2f)
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
