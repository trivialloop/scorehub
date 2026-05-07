package com.github.trivialloop.scorehub.games.skyjo

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
import com.github.trivialloop.scorehub.databinding.ActivitySkyjoGameBinding
import com.github.trivialloop.scorehub.ui.GameResultsDialog
import com.github.trivialloop.scorehub.ui.HelpDialogs
import com.github.trivialloop.scorehub.utils.LocaleHelper
import com.github.trivialloop.scorehub.utils.ScoreColorRole
import kotlinx.coroutines.launch

class SkyjoGameActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySkyjoGameBinding
    private lateinit var database: AppDatabase
    private lateinit var playerIds: LongArray
    private lateinit var playerNames: Array<String>
    private lateinit var playerColors: IntArray
    private lateinit var players: List<SkyjoPlayerState>

    private val rounds = mutableListOf<SkyjoRound>()
    private var gameOver = false

    companion object {
        const val GAME_TYPE = "skyjo"
        private const val SCORE_LIMIT = 100
    }

    override fun attachBaseContext(newBase: Context) {
        val language = LocaleHelper.getPersistedLocale(newBase)
        super.attachBaseContext(LocaleHelper.setLocale(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySkyjoGameBinding.inflate(layoutInflater)
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
            SkyjoPlayerState(playerIds[i], playerNames[i], playerColors[i])
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.skyjo_game)

        rounds.add(SkyjoRound(1))
        buildTable()
    }

    // ─── Adaptive sizing ───────────────────────────────────────────────────────

    private val cellTextSize: Float get() = when {
        players.size <= 3 -> 14f; players.size <= 5 -> 13f; else -> 11f
    }
    private val cellPaddingV: Int get() = dpToPx(when {
        players.size <= 3 -> 14; players.size <= 5 -> 10; else -> 7
    })

    // ─── Table construction ────────────────────────────────────────────────────

    private fun buildTable() {
        val headerRow = buildHeaderRow()
        val roundRows = rounds.mapIndexed { index, round ->
            buildRoundRow(round, isLast = index == rounds.lastIndex, isPrev = index == rounds.lastIndex - 1)
        }
        val totalRow = buildTotalRow()

        val screenHeight       = resources.displayMetrics.heightPixels
        val appBarHeight       = binding.toolbar.layoutParams?.height?.takeIf { it > 0 } ?: dpToPx(56)
        val rowHeight          = cellPaddingV * 2 + dpToPx((cellTextSize + 4).toInt())
        val totalNaturalHeight = rowHeight * (roundRows.size + 3)

        if (totalNaturalHeight > screenHeight - appBarHeight) {
            binding.headerContainer.removeAllViews(); binding.headerContainer.addView(headerRow)
            binding.tableContainer.removeAllViews(); roundRows.forEach { binding.tableContainer.addView(it) }
            binding.totalContainer.removeAllViews(); binding.totalContainer.addView(totalRow)
            binding.scrollView.post { binding.scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
        } else {
            binding.headerContainer.removeAllViews()
            binding.tableContainer.removeAllViews(); binding.totalContainer.removeAllViews()
            binding.tableContainer.addView(headerRow)
            roundRows.forEach { binding.tableContainer.addView(it) }
            binding.tableContainer.addView(totalRow)
        }
    }

    private fun buildHeaderRow(): LinearLayout {
        val row = makeRow()
        row.addView(makeLabelCell(getString(R.string.skyjo_round_label)))
        for (player in players) {
            val cell = makePlayerCell(player.playerName, bold = true)
            cell.background = cellDrawable(player.playerColor)
            cell.setTextColor(Color.WHITE)
            cell.maxLines = 1; cell.ellipsize = TextUtils.TruncateAt.END
            row.addView(cell)
        }
        return row
    }

    private fun buildRoundRow(round: SkyjoRound, isLast: Boolean, isPrev: Boolean): LinearLayout {
        val row           = makeRow()
        val playerIdList  = players.map { it.playerId }
        val roundComplete = round.isComplete(playerIdList)

        val labelCell = makeLabelCell(round.roundNumber.toString())
        val finisher  = players.find { it.playerId == round.finisherId }
        if (finisher != null) { labelCell.background = cellDrawable(finisher.playerColor); labelCell.setTextColor(Color.WHITE) }
        if (isLast && !gameOver) {
            labelCell.setOnClickListener { showFinisherPicker(round) }
            if (finisher == null) labelCell.alpha = 0.6f
        }
        row.addView(labelCell)

        val currentRound = rounds.last()
        val colorRoles: Map<Long, SkyjoRoundColor> =
            if (roundComplete) computeColorRoles(round, playerIdList) else emptyMap()

        for (player in players) {
            val rawScore   = round.scores[player.playerId]
            val finalScore = round.finalScores[player.playerId]

            val isFinisherPenalized = roundComplete &&
                    player.playerId == round.finisherId &&
                    rawScore != null && finalScore != null && finalScore != rawScore

            val displayText = when {
                isFinisherPenalized -> "$rawScore ×2"
                roundComplete       -> finalScore?.toString() ?: ""
                rawScore != null    -> rawScore.toString()
                else                -> ""
            }

            val canEnter    = isLast && round.finisherId != null && !roundComplete && !gameOver
            val canEditPrev = isPrev && !gameOver && currentRound.finisherId == null

            val bgColor = when {
                canEnter    && rawScore == null -> ContextCompat.getColor(this, R.color.cell_editable_bg)
                canEnter    && rawScore != null -> ContextCompat.getColor(this, R.color.cell_editable_filled_bg)
                canEditPrev && rawScore == null -> ContextCompat.getColor(this, R.color.cell_editable_bg)
                canEditPrev && rawScore != null -> ContextCompat.getColor(this, R.color.cell_editable_filled_bg)
                else                            -> ContextCompat.getColor(this, R.color.score_cell_background)
            }

            val cell = makePlayerCell(displayText)
            cell.background = cellDrawable(bgColor)

            if (roundComplete) {
                when (colorRoles[player.playerId]) {
                    SkyjoRoundColor.GREEN -> { cell.setTextColor(ContextCompat.getColor(this, R.color.score_text_best));  cell.setTypeface(null, Typeface.BOLD) }
                    SkyjoRoundColor.RED   -> { cell.setTextColor(ContextCompat.getColor(this, R.color.score_text_worst)); cell.setTypeface(null, Typeface.BOLD) }
                    else -> {}
                }
            }

            when {
                canEnter    -> cell.setOnClickListener { showScoreInput(round, player, isEdit = rawScore != null) }
                canEditPrev -> cell.setOnClickListener { showScoreInput(round, player, isEdit = true) }
            }
            row.addView(cell)
        }
        return row
    }

    /**
     * Per-round colour roles (custom finisher-aware logic):
     * Case A — finisher strictly sole lowest: finisher=GREEN, highest others=RED, rest=NEUTRAL
     * Case B — finisher NOT strictly lowest:  finisher=RED,   lowest others=GREEN, rest=NEUTRAL
     */
    private fun computeColorRoles(round: SkyjoRound, playerIdList: List<Long>): Map<Long, SkyjoRoundColor> {
        val raw = playerIdList.mapNotNull { id -> round.scores[id]?.let { id to it } }.toMap()
        if (raw.size != playerIdList.size) return emptyMap()
        val finisherId  = round.finisherId ?: return emptyMap()
        val finisherRaw = raw[finisherId]  ?: return emptyMap()
        val globalMin   = raw.values.min()

        val finisherIsStrictlyLowest = finisherRaw == globalMin && raw.values.count { it == globalMin } == 1
        val result = mutableMapOf<Long, SkyjoRoundColor>()

        if (finisherIsStrictlyLowest) {
            result[finisherId] = SkyjoRoundColor.GREEN
            val globalMax = raw.values.max()
            for (id in playerIdList) {
                if (id == finisherId) continue
                result[id] = if (raw[id] == globalMax) SkyjoRoundColor.RED else SkyjoRoundColor.NEUTRAL
            }
        } else {
            result[finisherId] = SkyjoRoundColor.RED
            val minNonFinisher = playerIdList.filter { it != finisherId }.mapNotNull { raw[it] }.minOrNull()
            for (id in playerIdList) {
                if (id == finisherId) continue
                result[id] = if (raw[id] == minNonFinisher) SkyjoRoundColor.GREEN else SkyjoRoundColor.NEUTRAL
            }
        }
        return result
    }

    private fun buildTotalRow(): LinearLayout {
        val row         = makeRow()
        val totalValues = players.map { it.getTotal(rounds) }
        row.addView(makeLabelCell(getString(R.string.skyjo_total)))
        for (player in players) {
            val total = player.getTotal(rounds)
            val cell  = makePlayerCell(total.toString(), bold = true)
            if (gameOver) {
                // Skyjo: lower total = better → higherIsBetter = false
                val role = ScoreColorRole(total, totalValues, higherIsBetter = false)
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

    private fun showFinisherPicker(round: SkyjoRound) {
        val names = players.map { it.playerName }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.skyjo_who_finished))
            .setItems(names) { _, which -> round.finisherId = players[which].playerId; buildTable() }
            .show()
    }

    private fun showScoreInput(round: SkyjoRound, player: SkyjoPlayerState, isEdit: Boolean = false) {
        val playerIdList = players.map { it.playerId }
        val title = if (isEdit) "✏️ ${player.playerName} — ${getString(R.string.skyjo_enter_score)}"
        else "${player.playerName} — ${getString(R.string.skyjo_enter_score)}"

        val editText = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
            hint = getString(R.string.skyjo_score_hint); gravity = Gravity.CENTER; textSize = 20f
            filters = arrayOf(InputFilter.LengthFilter(3))
            if (isEdit) round.scores[player.playerId]?.let { setText(it.toString()) }
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(24), dpToPx(8), dpToPx(24), dpToPx(8)); addView(editText)
        }
        val dialog = AlertDialog.Builder(this).setTitle(title).setView(container)
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                val value = editText.text.toString().trim().toIntOrNull()
                if (value == null) { showScoreInput(round, player, isEdit); return@setPositiveButton }
                round.scores[player.playerId] = value
                if (round.allScoresEntered(playerIdList)) {
                    round.computeFinalScores(playerIdList)
                    buildTable()
                    if (!isEdit) updateTotalsAndCheckEnd()
                } else { buildTable() }
            }
            .setNegativeButton(getString(R.string.cancel), null).create()

        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show(); editText.requestFocus()
    }

    // ─── Game logic ────────────────────────────────────────────────────────────

    private fun updateTotalsAndCheckEnd() {
        val maxTotal = players.maxOf { it.getTotal(rounds) }
        if (maxTotal >= SCORE_LIMIT) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.skyjo_game_over_title))
                .setMessage(getString(R.string.skyjo_game_over_confirm))
                .setPositiveButton(getString(R.string.yes)) { _, _ ->
                    gameOver = true; buildTable(); saveResultsAndShowSummary()
                }
                .setNegativeButton(getString(R.string.no)) { _, _ ->
                    rounds.add(SkyjoRound(rounds.size + 1)); buildTable()
                }.show()
        } else { rounds.add(SkyjoRound(rounds.size + 1)); buildTable() }
    }

    private fun saveResultsAndShowSummary() {
        val totals   = players.associate { it to it.getTotal(rounds) }
        val minScore = totals.values.minOrNull() ?: 0
        val winners  = totals.filter { it.value == minScore }.keys
        val isDraw   = winners.size > 1
        lifecycleScope.launch {
            database.gameResultDao().insertGameResults(players.map { player ->
                GameResult(gameType = GAME_TYPE, playerId = player.playerId, playerName = player.playerName,
                    score = player.getTotal(rounds),
                    isWinner = !isDraw && player in winners, isDraw = isDraw && player in winners)
            })
            val sorted = totals.entries.sortedBy { it.value }
            var rank = 1
            val entries = sorted.mapIndexed { i, (p, s) ->
                val r = if (i > 0 && s == sorted[i - 1].value) rank else { rank = i + 1; rank }
                GameResultsDialog.PlayerResult(p.playerName, p.playerColor, s, r)
            }
            GameResultsDialog.show(this@SkyjoGameActivity, entries, isDraw, " pts") { finish() }
        }
    }

    // ─── Cell helpers ──────────────────────────────────────────────────────────

    private fun makeRow(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    }
    private fun makeLabelCell(text: String): TextView = TextView(this).apply {
        this.text = text; gravity = Gravity.CENTER
        setPadding(dpToPx(4), cellPaddingV, dpToPx(4), cellPaddingV)
        textSize = cellTextSize - 1f; setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(dpToPx(42), LinearLayout.LayoutParams.WRAP_CONTENT)
        background = cellDrawable(ContextCompat.getColor(this@SkyjoGameActivity, R.color.header_cell_background))
        setTextColor(ContextCompat.getColor(this@SkyjoGameActivity, R.color.header_cell_text))
    }
    private fun makePlayerCell(text: String, bold: Boolean = false): TextView = TextView(this).apply {
        this.text = text; gravity = Gravity.CENTER
        setPadding(dpToPx(2), cellPaddingV, dpToPx(2), cellPaddingV)
        textSize = cellTextSize; if (bold) setTypeface(null, Typeface.BOLD)
        maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        background = cellDrawable(ContextCompat.getColor(this@SkyjoGameActivity, R.color.score_cell_background))
        setTextColor(ContextCompat.getColor(this@SkyjoGameActivity, R.color.score_cell_text))
    }
    private fun cellDrawable(bgColor: Int): GradientDrawable = GradientDrawable().apply {
        setColor(bgColor); setStroke(1, ContextCompat.getColor(this@SkyjoGameActivity, R.color.cell_border))
    }
    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_skyjo_game, menu); return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                AlertDialog.Builder(this).setTitle(R.string.skyjo_quit_game).setMessage(R.string.skyjo_quit_game_message)
                    .setPositiveButton(R.string.yes) { _, _ -> finish() }.setNegativeButton(R.string.no, null).show(); true
            }
            R.id.action_help -> { HelpDialogs.showAppHelp(this, GAME_TYPE); true }
            else -> super.onOptionsItemSelected(item)
        }
    }
}

private enum class SkyjoRoundColor { GREEN, RED, NEUTRAL }