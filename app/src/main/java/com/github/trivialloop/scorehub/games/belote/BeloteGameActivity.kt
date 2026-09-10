package com.github.trivialloop.scorehub.games.belote

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
import android.widget.*
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
import com.github.trivialloop.scorehub.databinding.ActivityBeloteGameBinding
import com.github.trivialloop.scorehub.ui.GameResultsDialog
import com.github.trivialloop.scorehub.ui.HelpDialogs
import com.github.trivialloop.scorehub.utils.LocaleHelper
import kotlinx.coroutines.launch

class BeloteGameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBeloteGameBinding
    private lateinit var database: AppDatabase

    private lateinit var playerIds: LongArray
    private lateinit var playerNames: Array<String>
    private lateinit var playerColors: IntArray
    private lateinit var playerTeams: IntArray   // parallel to the arrays above: 0 or 1
    private lateinit var teams: List<BeloteTeamState>

    private val rounds = mutableListOf<BeloteRound>()
    private var gameOver = false

    companion object {
        const val GAME_TYPE = "belote"
        private const val LABEL_COL_DP = 65
        private const val ROW_HEIGHT_DP = 48
    }

    override fun attachBaseContext(newBase: Context) {
        val language = LocaleHelper.getPersistedLocale(newBase)
        super.attachBaseContext(LocaleHelper.setLocale(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBeloteGameBinding.inflate(layoutInflater)
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
        playerTeams  = intent.getIntArrayExtra("PLAYER_TEAMS")    ?: intArrayOf()

        teams = buildTeams()

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.belote_game)

        buildTable()

        onBackPressedDispatcher.addCallback(this) { showQuitGameDialog() }
    }

    private fun buildTeams(): List<BeloteTeamState> {
        fun indicesOf(team: Int) = playerTeams.indices.filter { playerTeams[it] == team }
        val team0Idx = indicesOf(0)
        val team1Idx = indicesOf(1)
        return listOf(
            BeloteTeamState(0, playerNames[team0Idx[0]], playerNames[team0Idx[1]], playerColors[team0Idx[0]]),
            BeloteTeamState(1, playerNames[team1Idx[0]], playerNames[team1Idx[1]], playerColors[team1Idx[0]])
        )
    }

    // ─── Table construction ────────────────────────────────────────────────────

    private fun buildTable() {
        binding.headerContainer.removeAllViews()
        binding.headerContainer.addView(buildHeaderRow())

        binding.tableContainer.removeAllViews()
        val roundScores = BeloteScoring.computeRoundScores(rounds)
        rounds.forEachIndexed { index, round ->
            binding.tableContainer.addView(
                buildRoundRow(round, roundScores[index], isLast = index == rounds.lastIndex)
            )
        }
        binding.tableContainer.addView(buildAddRoundRow())
        binding.tableContainer.addView(buildTotalRow())

        binding.scrollView.post { binding.scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun buildHeaderRow(): LinearLayout {
        val row = makeRow()
        row.addView(makeLabelCell("#"))
        for (team in teams) {
            val cell = makeTeamCell(team.displayName, bold = true)
            cell.background = cellDrawable(team.teamColor)
            cell.setTextColor(Color.WHITE)
            row.addView(cell)
        }
        return row
    }

    private fun buildRoundRow(round: BeloteRound, scores: Map<Int, Int>, isLast: Boolean): LinearLayout {
        val row = makeRow()
        val attackingColor = teams[round.attackingTeam].teamColor

        val labelCell = makeLabelCell(round.roundNumber.toString())
        labelCell.background = cellDrawable(attackingColor)
        labelCell.setTextColor(Color.WHITE)
        if (!gameOver) labelCell.setOnClickListener { showRoundDialog(existingRound = round) }
        row.addView(labelCell)

        for (team in teams) {
            val score = scores[team.teamIndex] ?: 0
            val cell = makeTeamCell(scoreLabel(round, team.teamIndex, score))
            if (!gameOver && isLast) cell.setOnClickListener { showRoundDialog(existingRound = round) }
            row.addView(cell)
        }
        return row
    }

    /** Small annotation next to the raw score: 🃏 for capot, 🤝 for belote. */
    private fun scoreLabel(round: BeloteRound, teamIndex: Int, score: Int): String {
        val tags = mutableListOf<String>()
        if (round.isCapot && round.capotTeam == teamIndex) tags.add("🃏")
        if (round.beloteTeam == teamIndex) tags.add("🤝")
        return if (tags.isEmpty()) "$score" else "$score ${tags.joinToString(" ")}"
    }

    private fun buildAddRoundRow(): LinearLayout {
        if (gameOver) return makeRow()
        val nextNum = rounds.size + 1
        val row = makeRow()
        val labelCell = makeLabelCell(nextNum.toString())
        labelCell.alpha = 0.4f
        labelCell.setOnClickListener { showRoundDialog(existingRound = null) }
        row.addView(labelCell)
        for (team in teams) {
            val cell = makeTeamCell("")
            cell.setOnClickListener { showRoundDialog(existingRound = null) }
            row.addView(cell)
        }
        return row
    }

    private fun buildTotalRow(): LinearLayout {
        val row = makeRow()
        row.addView(makeLabelCell(getString(R.string.belote_total)))
        val totals = teams.associate { it.teamIndex to it.getTotal(rounds) }
        for (team in teams) {
            val total = totals[team.teamIndex] ?: 0
            val other = totals[1 - team.teamIndex] ?: 0
            val cell = makeTeamCell(total.toString(), bold = true)
            cell.background = cellDrawable(ContextCompat.getColor(this, R.color.cell_calculated_bg))
            var textColor = ContextCompat.getColor(this, R.color.score_calculated_cell_text)
            if (gameOver && total != other) {
                textColor = ContextCompat.getColor(
                    this, if (total > other) R.color.score_text_best else R.color.score_text_worst
                )
            }
            cell.setTextColor(textColor)
            row.addView(cell)
        }
        return row
    }

    // ─── Round entry dialog ─────────────────────────────────────────────────────

    private fun showRoundDialog(existingRound: BeloteRound?) {
        val view = layoutInflater.inflate(R.layout.dialog_belote_round, null)

        val rgAttacker = view.findViewById<RadioGroup>(R.id.rgAttackingTeam)
        val rbTeam0 = view.findViewById<RadioButton>(R.id.rbAttackerTeam0)
        val rbTeam1 = view.findViewById<RadioButton>(R.id.rbAttackerTeam1)
        rbTeam0.text = teams[0].displayName
        rbTeam1.text = teams[1].displayName

        val cbCapot = view.findViewById<CheckBox>(R.id.cbCapot)
        val rowCapotTeam = view.findViewById<LinearLayout>(R.id.rowCapotTeam)
        val rgCapotTeam = view.findViewById<RadioGroup>(R.id.rgCapotTeam)
        val rbCapotTeam0 = view.findViewById<RadioButton>(R.id.rbCapotTeam0)
        val rbCapotTeam1 = view.findViewById<RadioButton>(R.id.rbCapotTeam1)
        rbCapotTeam0.text = teams[0].displayName
        rbCapotTeam1.text = teams[1].displayName

        val rowPoints = view.findViewById<LinearLayout>(R.id.rowPoints)
        val editPoints = view.findViewById<EditText>(R.id.editPointsMade)
        editPoints.filters = arrayOf(InputFilter.LengthFilter(3))

        val rgBelote = view.findViewById<RadioGroup>(R.id.rgBeloteTeam)
        val rbBeloteNone = view.findViewById<RadioButton>(R.id.rbBeloteNone)
        val rbBeloteTeam0 = view.findViewById<RadioButton>(R.id.rbBeloteTeam0)
        val rbBeloteTeam1 = view.findViewById<RadioButton>(R.id.rbBeloteTeam1)
        rbBeloteTeam0.text = teams[0].displayName
        rbBeloteTeam1.text = teams[1].displayName

        // Pre-fill from an existing round (edit mode)
        (if (existingRound?.attackingTeam == 1) rbTeam1 else rbTeam0).isChecked = true
        cbCapot.isChecked = existingRound?.isCapot ?: false
        (if (existingRound?.capotTeam == 1) rbCapotTeam1 else rbCapotTeam0).isChecked = true
        existingRound?.pointsMade?.let { editPoints.setText(it.toString()) }
        when (existingRound?.beloteTeam) {
            0 -> rbBeloteTeam0.isChecked = true
            1 -> rbBeloteTeam1.isChecked = true
            else -> rbBeloteNone.isChecked = true
        }

        fun refreshVisibility() {
            val isCapot = cbCapot.isChecked
            rowCapotTeam.visibility = if (isCapot) android.view.View.VISIBLE else android.view.View.GONE
            rowPoints.visibility = if (isCapot) android.view.View.GONE else android.view.View.VISIBLE
        }
        refreshVisibility()
        cbCapot.setOnCheckedChangeListener { _, _ -> refreshVisibility() }

        val title = if (existingRound != null)
            "✏️ ${getString(R.string.belote_round_title, existingRound.roundNumber)}"
        else
            getString(R.string.belote_round_title, rounds.size + 1)

        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(view)
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                val attackingTeam = if (rgAttacker.checkedRadioButtonId == R.id.rbAttackerTeam1) 1 else 0
                val isCapot = cbCapot.isChecked
                val capotTeam = if (rgCapotTeam.checkedRadioButtonId == R.id.rbCapotTeam1) 1 else 0
                val beloteTeam = when (rgBelote.checkedRadioButtonId) {
                    R.id.rbBeloteTeam0 -> 0
                    R.id.rbBeloteTeam1 -> 1
                    else -> null
                }

                if (!isCapot) {
                    val pts = editPoints.text.toString().trim().toIntOrNull()
                    if (pts == null || pts < 0 || pts > BELOTE_MAX_POINTS) {
                        Toast.makeText(this, getString(R.string.belote_points_error), Toast.LENGTH_SHORT).show()
                        showRoundDialog(existingRound)
                        return@setPositiveButton
                    }
                    commitRound(existingRound, attackingTeam, pts, isCapot = false, capotTeam = null, beloteTeam)
                } else {
                    commitRound(existingRound, attackingTeam, pointsMade = null, isCapot = true, capotTeam, beloteTeam)
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)

        if (existingRound != null) {
            dialog.setNeutralButton(getString(R.string.tarot_delete_round)) { _, _ -> deleteRound(existingRound) }
        }

        val alert = dialog.create()
        alert.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        alert.show()
    }

    private fun commitRound(
        existingRound: BeloteRound?,
        attackingTeam: Int,
        pointsMade: Int?,
        isCapot: Boolean,
        capotTeam: Int?,
        beloteTeam: Int?
    ) {
        val newRound = BeloteRound(
            roundNumber = existingRound?.roundNumber ?: rounds.size + 1,
            attackingTeam = attackingTeam,
            pointsMade = pointsMade,
            isCapot = isCapot,
            capotTeam = capotTeam,
            beloteTeam = beloteTeam
        )
        if (existingRound != null) {
            val idx = rounds.indexOf(existingRound)
            if (idx >= 0) rounds[idx] = newRound else rounds.add(newRound)
        } else {
            rounds.add(newRound)
        }
        buildTable()
        checkEndOfGame()
    }

    private fun deleteRound(round: BeloteRound) {
        rounds.remove(round)
        rounds.forEachIndexed { idx, r -> rounds[idx] = r.copy(roundNumber = idx + 1) }
        buildTable()
    }

    // ─── Game logic ────────────────────────────────────────────────────────────

    private fun checkEndOfGame() {
        if (gameOver) return
        val maxTotal = teams.maxOf { it.getTotal(rounds) }
        if (maxTotal >= BELOTE_SCORE_LIMIT) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.belote_game_over_title))
                .setMessage(getString(R.string.belote_game_over_confirm))
                .setPositiveButton(getString(R.string.yes)) { _, _ ->
                    gameOver = true; buildTable(); saveResultsAndShowSummary()
                }
                .setNegativeButton(getString(R.string.no), null)
                .show()
        }
    }

    private fun saveResultsAndShowSummary() {
        val totals = teams.associate { it.teamIndex to it.getTotal(rounds) }
        val maxScore = totals.values.maxOrNull() ?: 0
        val winningTeams = totals.filter { it.value == maxScore }.keys
        val isDraw = winningTeams.size > 1

        lifecycleScope.launch {
            val results = playerIds.indices.map { i ->
                val team = playerTeams[i]
                GameResult(
                    gameType = GAME_TYPE,
                    playerId = playerIds[i],
                    playerName = playerNames[i],
                    score = totals[team] ?: 0,
                    isWinner = !isDraw && team in winningTeams,
                    isDraw = isDraw && team in winningTeams
                )
            }
            database.gameResultDao().insertGameResults(results)

            val sortedTeams = teams.sortedByDescending { totals[it.teamIndex] ?: 0 }
            var rank = 1
            val entries = sortedTeams.mapIndexed { i, team ->
                val score = totals[team.teamIndex] ?: 0
                val r = if (i > 0 && score == (totals[sortedTeams[i - 1].teamIndex] ?: 0)) rank
                        else { rank = i + 1; rank }
                GameResultsDialog.PlayerResult(team.displayName, team.teamColor, score, r)
            }
            GameResultsDialog.show(this@BeloteGameActivity, entries, isDraw, " pts") { finish() }
        }
    }

    // ─── Cell builders ─────────────────────────────────────────────────────────

    private fun makeRow(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(ROW_HEIGHT_DP))
        isBaselineAligned = false
    }

    private fun makeLabelCell(text: String): TextView = TextView(this).apply {
        this.text = text; gravity = Gravity.CENTER; textSize = 13f; setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(dpToPx(LABEL_COL_DP), LinearLayout.LayoutParams.MATCH_PARENT)
        background = cellDrawable(ContextCompat.getColor(this@BeloteGameActivity, R.color.header_cell_background))
        setTextColor(ContextCompat.getColor(this@BeloteGameActivity, R.color.header_cell_text))
    }

    private fun makeTeamCell(text: String, bold: Boolean = false): TextView = TextView(this).apply {
        this.text = text; gravity = Gravity.CENTER; textSize = 15f
        if (bold) setTypeface(null, Typeface.BOLD)
        maxLines = 2; ellipsize = TextUtils.TruncateAt.END
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        background = cellDrawable(ContextCompat.getColor(this@BeloteGameActivity, R.color.score_cell_background))
        setTextColor(ContextCompat.getColor(this@BeloteGameActivity, R.color.score_cell_text))
    }

    private fun cellDrawable(bgColor: Int): GradientDrawable = GradientDrawable().apply {
        setColor(bgColor)
        setStroke(1, ContextCompat.getColor(this@BeloteGameActivity, R.color.cell_border))
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private fun showQuitGameDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.belote_quit_game)
            .setMessage(R.string.belote_quit_game_message)
            .setPositiveButton(R.string.yes) { _, _ -> finish() }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_belote_game, menu)
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
