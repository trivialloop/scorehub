package com.github.trivialloop.scorehub.games.cactus

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.MenuItem
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.github.trivialloop.scorehub.R
import com.github.trivialloop.scorehub.data.AppDatabase
import com.github.trivialloop.scorehub.data.GameResult
import com.github.trivialloop.scorehub.databinding.ActivityCactusGameBinding
import com.github.trivialloop.scorehub.ui.GameResultsDialog
import com.github.trivialloop.scorehub.utils.LocaleHelper
import com.github.trivialloop.scorehub.utils.scoreColorRole
import com.github.trivialloop.scorehub.utils.ScoreColorRole
import kotlinx.coroutines.launch

class CactusGameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCactusGameBinding
    private lateinit var database: AppDatabase

    private lateinit var playerIds: LongArray
    private lateinit var playerNames: Array<String>
    private lateinit var playerColors: IntArray
    private lateinit var players: List<CactusPlayerState>

    private val rounds = mutableListOf<CactusRound>()
    private var gameOver = false

    companion object {
        const val GAME_TYPE = "cactus"
        private const val SCORE_LIMIT = 10
        private const val RAW_SCORE_MAX = 40
    }

    override fun attachBaseContext(newBase: Context) {
        val language = LocaleHelper.getPersistedLocale(newBase)
        super.attachBaseContext(LocaleHelper.setLocale(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCactusGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = AppDatabase.getDatabase(this)
        playerIds = intent.getLongArrayExtra("PLAYER_IDS") ?: longArrayOf()
        playerNames = intent.getStringArrayExtra("PLAYER_NAMES") ?: arrayOf()
        playerColors = intent.getIntArrayExtra("PLAYER_COLORS") ?: intArrayOf()

        players = playerIds.indices.map { i ->
            CactusPlayerState(playerIds[i], playerNames[i], playerColors[i])
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.cactus_game)

        rounds.add(CactusRound(1))
        buildTable()
    }

    private val cellTextSize: Float get() = when {
        players.size <= 3 -> 14f; players.size <= 5 -> 13f; else -> 11f
    }
    private val cellPaddingV: Int get() = dpToPx(when {
        players.size <= 3 -> 14; players.size <= 5 -> 10; else -> 7
    })

    private fun buildTable() {
        val headerRow = buildHeaderRow()
        val roundRows = rounds.mapIndexed { index, round ->
            buildRoundRow(round, isLast = index == rounds.lastIndex, isPrev = index == rounds.lastIndex - 1)
        }
        val totalRow = buildTotalRow()

        val screenHeight = resources.displayMetrics.heightPixels
        val appBarHeight = binding.toolbar.layoutParams?.height?.takeIf { it > 0 } ?: dpToPx(56)
        val rowHeight = cellPaddingV * 2 + dpToPx((cellTextSize + 4).toInt())
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
        row.addView(makeLabelCell(getString(R.string.cactus_round_label)))
        for (player in players) {
            val cell = makePlayerCell(player.playerName, bold = true)
            cell.background = cellDrawable(player.playerColor)
            cell.setTextColor(Color.WHITE)
            cell.maxLines = 1; cell.ellipsize = TextUtils.TruncateAt.END
            row.addView(cell)
        }
        return row
    }

    private fun buildRoundRow(round: CactusRound, isLast: Boolean, isPrev: Boolean): LinearLayout {
        val row = makeRow()
        val playerIdList = players.map { it.playerId }
        val allEntered = round.allScoresEntered(playerIdList)

        // Label cell — tinted with the finisher's color
        val labelCell = makeLabelCell(round.roundNumber.toString())
        val finisher = players.find { it.playerId == round.finisherId }
        if (finisher != null) {
            labelCell.background = cellDrawable(finisher.playerColor)
            labelCell.setTextColor(Color.WHITE)
        }
        if (isLast && !gameOver) {
            labelCell.setOnClickListener { showFinisherPicker(round) }
            if (finisher == null) labelCell.alpha = 0.6f
        }
        row.addView(labelCell)

        val currentRound = rounds.last()

        for (player in players) {
            val rawScore = round.rawScores[player.playerId]
            val point = round.points[player.playerId]

            val displayBase = when {
                point != null && rawScore != null -> "$point ($rawScore)"
                rawScore != null -> "($rawScore)"
                else -> ""
            }

            val canEnter = isLast && round.finisherId != null && !allEntered && !gameOver
            val canEditPrev = isPrev && !gameOver && currentRound.finisherId == null
            val isReEditing = (canEnter || canEditPrev) && rawScore != null

            val displayText = if (isReEditing) "✏ $displayBase" else displayBase

            val bgColor = when {
                isReEditing -> ContextCompat.getColor(this, R.color.cell_editable_filled_bg)
                canEnter || canEditPrev -> ContextCompat.getColor(this, R.color.cell_editable_bg)
                else -> ContextCompat.getColor(this, R.color.score_cell_background)
            }

            val cell = makePlayerCell(displayText)
            cell.background = cellDrawable(bgColor)

            // Text color: lowest raw = green, highest raw = red (finisher uses same rule)
            if (allEntered) {
                val allRaw = playerIdList.mapNotNull { round.rawScores[it] }
                val role = scoreColorRole(rawScore, playerIdList.map { round.rawScores[it] })
                when (role) {
                    ScoreColorRole.BEST -> {
                        cell.setTextColor(ContextCompat.getColor(this, R.color.score_text_best))
                        cell.setTypeface(null, Typeface.BOLD)
                    }
                    ScoreColorRole.WORST -> {
                        cell.setTextColor(ContextCompat.getColor(this, R.color.score_text_worst))
                        cell.setTypeface(null, Typeface.BOLD)
                    }
                    else -> {}
                }
            }

            when {
                canEnter -> cell.setOnClickListener { showScoreInput(round, player, isEdit = rawScore != null) }
                canEditPrev -> cell.setOnClickListener { showScoreInput(round, player, isEdit = true) }
            }
            row.addView(cell)
        }
        return row
    }

    private fun buildTotalRow(): LinearLayout {
        val row = makeRow()
        row.addView(makeLabelCell(getString(R.string.cactus_total)))
        val totalValues = players.map { it.getTotal(rounds) }
        for (player in players) {
            val total = player.getTotal(rounds)
            val cell = makePlayerCell(total.toString(), bold = true)
            if (gameOver) {
                val role = scoreColorRole(total, totalValues)
                when (role) {
                    ScoreColorRole.BEST -> cell.setTextColor(ContextCompat.getColor(this, R.color.score_text_best))
                    ScoreColorRole.WORST -> cell.setTextColor(ContextCompat.getColor(this, R.color.score_text_worst))
                    else -> {}
                }
            }
            row.addView(cell)
        }
        return row
    }

    private fun showFinisherPicker(round: CactusRound) {
        val names = players.map { it.playerName }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.cactus_who_finished))
            .setItems(names) { _, which ->
                round.finisherId = players[which].playerId
                buildTable()
            }
            .show()
    }

    private fun showScoreInput(round: CactusRound, player: CactusPlayerState, isEdit: Boolean = false) {
        val playerIdList = players.map { it.playerId }
        val title = if (isEdit) "✏️ ${player.playerName} — ${getString(R.string.cactus_enter_score)}"
                    else "${player.playerName} — ${getString(R.string.cactus_enter_score)}"

        val editText = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.cactus_score_hint)
            gravity = Gravity.CENTER
            textSize = 20f
            filters = arrayOf(InputFilter.LengthFilter(2))
            if (isEdit) round.rawScores[player.playerId]?.let { setText(it.toString()) }
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(24), dpToPx(8), dpToPx(24), dpToPx(8))
            addView(editText)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(container)
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                val value = editText.text.toString().trim().toIntOrNull()
                if (value == null || value < 0 || value > RAW_SCORE_MAX) {
                    showScoreInput(round, player, isEdit); return@setPositiveButton
                }
                round.rawScores[player.playerId] = value
                round.computePoints(playerIdList)
                buildTable()
                if (!isEdit && round.isComplete(playerIdList)) checkEndOfRound()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()

        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
        editText.requestFocus()
    }

    private fun checkEndOfRound() {
        val maxTotal = players.maxOf { it.getTotal(rounds) }
        if (maxTotal >= SCORE_LIMIT) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.cactus_game_over_title))
                .setMessage(getString(R.string.cactus_game_over_confirm))
                .setPositiveButton(getString(R.string.yes)) { _, _ ->
                    gameOver = true; buildTable(); saveResultsAndShowSummary()
                }
                .setNegativeButton(getString(R.string.no)) { _, _ ->
                    rounds.add(CactusRound(rounds.size + 1)); buildTable()
                }
                .show()
        } else {
            rounds.add(CactusRound(rounds.size + 1)); buildTable()
        }
    }

    private fun saveResultsAndShowSummary() {
        val totals = players.associate { it to it.getTotal(rounds) }
        val maxScore = totals.values.maxOrNull() ?: 0
        val winners = totals.filter { it.value == maxScore }.keys
        val isDraw = winners.size > 1
        lifecycleScope.launch {
            database.gameResultDao().insertGameResults(players.map { player ->
                GameResult(
                    gameType = GAME_TYPE, playerId = player.playerId,
                    playerName = player.playerName, score = player.getTotal(rounds),
                    isWinner = !isDraw && player in winners,
                    isDraw = isDraw && player in winners
                )
            })
            val sorted = totals.entries.sortedByDescending { it.value }
            var rank = 1
            val entries = sorted.mapIndexed { i, (p, s) ->
                val r = if (i > 0 && s == sorted[i - 1].value) rank else { rank = i + 1; rank }
                GameResultsDialog.PlayerResult(p.playerName, p.playerColor, s, r)
            }
            GameResultsDialog.show(this@CactusGameActivity, entries, isDraw, " pts") { finish() }
        }
    }

    private fun makeRow(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    }

    private fun makeLabelCell(text: String): TextView = TextView(this).apply {
        this.text = text; gravity = Gravity.CENTER
        setPadding(dpToPx(4), cellPaddingV, dpToPx(4), cellPaddingV)
        textSize = cellTextSize - 1f; setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(dpToPx(42), LinearLayout.LayoutParams.WRAP_CONTENT)
        background = cellDrawable(ContextCompat.getColor(this@CactusGameActivity, R.color.header_cell_background))
        setTextColor(ContextCompat.getColor(this@CactusGameActivity, R.color.header_cell_text))
    }

    private fun makePlayerCell(text: String, bold: Boolean = false): TextView = TextView(this).apply {
        this.text = text; gravity = Gravity.CENTER
        setPadding(dpToPx(2), cellPaddingV, dpToPx(2), cellPaddingV)
        textSize = cellTextSize; if (bold) setTypeface(null, Typeface.BOLD)
        maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        background = cellDrawable(ContextCompat.getColor(this@CactusGameActivity, R.color.score_cell_background))
        setTextColor(ContextCompat.getColor(this@CactusGameActivity, R.color.score_cell_text))
    }

    private fun cellDrawable(bgColor: Int): GradientDrawable = GradientDrawable().apply {
        setColor(bgColor)
        setStroke(1, ContextCompat.getColor(this@CactusGameActivity, R.color.cell_border))
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                AlertDialog.Builder(this)
                    .setTitle(R.string.cactus_quit_game)
                    .setMessage(R.string.cactus_quit_game_message)
                    .setPositiveButton(R.string.yes) { _, _ -> finish() }
                    .setNegativeButton(R.string.no, null)
                    .show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
