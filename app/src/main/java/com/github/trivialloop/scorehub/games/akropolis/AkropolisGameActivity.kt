package com.github.trivialloop.scorehub.games.akropolis

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
import com.github.trivialloop.scorehub.databinding.ActivityAkropolisGameBinding
import com.github.trivialloop.scorehub.ui.GameResultsDialog
import com.github.trivialloop.scorehub.ui.HelpDialogs
import com.github.trivialloop.scorehub.utils.LocaleHelper
import com.github.trivialloop.scorehub.utils.ScoreColorRole
import kotlinx.coroutines.launch

class AkropolisGameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAkropolisGameBinding
    private lateinit var database: AppDatabase
    private lateinit var playerIds: LongArray
    private lateinit var playerNames: Array<String>
    private lateinit var playerColors: IntArray
    private lateinit var playerScores: List<AkropolisPlayerScore>
    private var gameOver = false

    companion object {
        const val GAME_TYPE = "akropolis"
        private const val LABEL_COL_DP = 56
        private const val MAX_INPUT = 99
    }

    override fun attachBaseContext(newBase: Context) {
        val language = LocaleHelper.getPersistedLocale(newBase)
        super.attachBaseContext(LocaleHelper.setLocale(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAkropolisGameBinding.inflate(layoutInflater)
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

        playerScores = playerIds.indices.map { i ->
            AkropolisPlayerScore(playerIds[i], playerNames[i], playerColors[i])
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.akropolis_game)

        buildScoreTable()
    }

    // ─── Table ────────────────────────────────────────────────────────────────

    private fun buildScoreTable() {
        binding.scoreTableContainer.removeAllViews()
        binding.scoreTableContainer.addView(buildLabelColumn())
        for (ps in playerScores) {
            binding.scoreTableContainer.addView(buildPlayerColumn(ps))
        }
    }

    private fun buildLabelColumn(): LinearLayout {
        val col = makeColumn(weight = 0f, widthDp = LABEL_COL_DP)
        // Header spacer
        col.addView(makeHeaderSpacerCell())
        // Color rows
        for (color in AkropolisColor.entries) {
            col.addView(makeColorLabelCell(color))
        }
        // Stones row
        col.addView(makeStonesLabelCell())
        // Total row
        col.addView(makeTotalLabelCell())
        return col
    }

    private fun buildPlayerColumn(ps: AkropolisPlayerScore): LinearLayout {
        val col = makeColumn(weight = 1f, widthDp = 0)
        // Player name
        col.addView(makePlayerNameCell(ps))
        // Color rows
        for (color in AkropolisColor.entries) {
            col.addView(makeColorScoreCell(ps, color))
        }
        // Stones
        col.addView(makeStonesCell(ps))
        // Total
        col.addView(makeTotalCell(ps))
        return col
    }

    // ─── Label column cells ───────────────────────────────────────────────────

    private fun makeHeaderSpacerCell(): LinearLayout {
        // Must match the height of the player name cell (2 sub-rows)
        return LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
            background = borderDrawable(ContextCompat.getColor(this@AkropolisGameActivity, R.color.header_cell_background))
        }
    }

    private fun makeColorLabelCell(color: AkropolisColor): LinearLayout {
        // Outer container — same proportional height as a color score cell (3 sub-rows)
        val outer = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            gravity      = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 3f
            )
            background = borderDrawable(ContextCompat.getColor(this@AkropolisGameActivity, R.color.header_cell_background))
        }
        // Colored dot
        val dot = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(26), dpToPx(26)).also {
                it.gravity = Gravity.CENTER
            }
            background = dotDrawable(colorToArgb(color))
        }
        outer.addView(dot)
        return outer
    }

    private fun makeStonesLabelCell(): TextView = TextView(this).apply {
        text = getString(R.string.akropolis_stones)
        gravity = Gravity.CENTER; textSize = 10f; setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        background = borderDrawable(ContextCompat.getColor(this@AkropolisGameActivity, R.color.header_cell_background))
        setTextColor(ContextCompat.getColor(this@AkropolisGameActivity, R.color.header_cell_text))
    }

    private fun makeTotalLabelCell(): TextView = TextView(this).apply {
        text = getString(R.string.akropolis_total)
        gravity = Gravity.CENTER; textSize = 11f; setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        background = borderDrawable(ContextCompat.getColor(this@AkropolisGameActivity, R.color.cell_calculated_bg))
        setTextColor(ContextCompat.getColor(this@AkropolisGameActivity, R.color.score_calculated_cell_text))
    }

    // ─── Player column cells ──────────────────────────────────────────────────

    private fun makePlayerNameCell(ps: AkropolisPlayerScore): TextView = TextView(this).apply {
        text = ps.playerName; gravity = Gravity.CENTER; textSize = 12f; setTypeface(null, Typeface.BOLD)
        maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        background = playerCellDrawable(ps.playerColor); setTextColor(Color.WHITE)
    }

    /**
     * A color score cell is split vertically into 3 sub-rows:
     *   top (weight 1): stars
     *   middle (weight 1): districts
     *   bottom (weight 1): total = stars * districts  (calculated, grayed)
     * The whole cell has weight 3f in the parent column so it's 3× taller than a 1-weight cell.
     */
    private fun makeColorScoreCell(ps: AkropolisPlayerScore, color: AkropolisColor): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 3f)
            background   = borderDrawable(ContextCompat.getColor(this@AkropolisGameActivity, R.color.score_cell_background))
        }

        val stars     = ps.stars[color]
        val districts = ps.districts[color]
        val subtotal  = ps.getDistrictTotal(color)

        val allStars     = playerScores.map { it.stars[color] }
        val allDistricts = playerScores.map { it.districts[color] }
        val allSubtotals = playerScores.map { it.getDistrictTotal(color) }

        // Stars sub-row
        val starsRole  = ScoreColorRole(stars, allStars, higherIsBetter = true)
        val starsBg    = if (!gameOver && stars == null) R.color.cell_editable_bg else R.color.score_cell_background
        val starsCell  = makeSubCell(
            text      = stars?.toString() ?: "",
            bgColor   = ContextCompat.getColor(this, starsBg),
            textColor = if (starsRole != ScoreColorRole.NEUTRAL && stars != null) starsRole.toColor(this)
                        else ContextCompat.getColor(this, R.color.score_cell_text),
            bold      = starsRole != ScoreColorRole.NEUTRAL && stars != null,
            hint      = getString(R.string.akropolis_stars_hint)
        )
        if (!gameOver) starsCell.setOnClickListener {
            showSubScoreInput(ps, color, isStars = true)
        }
        container.addView(starsCell)

        // Divider
        container.addView(makeDivider())

        // Districts sub-row
        val distRole  = ScoreColorRole(districts, allDistricts, higherIsBetter = true)
        val distBg    = if (!gameOver && districts == null) R.color.cell_editable_bg else R.color.score_cell_background
        val distCell  = makeSubCell(
            text      = districts?.toString() ?: "",
            bgColor   = ContextCompat.getColor(this, distBg),
            textColor = if (distRole != ScoreColorRole.NEUTRAL && districts != null) distRole.toColor(this)
                        else ContextCompat.getColor(this, R.color.score_cell_text),
            bold      = distRole != ScoreColorRole.NEUTRAL && districts != null,
            hint      = getString(R.string.akropolis_districts_hint)
        )
        if (!gameOver) distCell.setOnClickListener {
            showSubScoreInput(ps, color, isStars = false)
        }
        container.addView(distCell)

        // Divider
        container.addView(makeDivider())

        // Subtotal sub-row (calculated)
        val subtotalRole  = ScoreColorRole(
            if (stars != null && districts != null) subtotal else null,
            allSubtotals.map { if (playerScores.all { p -> p.stars[color] != null && p.districts[color] != null }) it else null },
            higherIsBetter = true
        )
        val subtotalColor = if (subtotalRole != ScoreColorRole.NEUTRAL && stars != null && districts != null)
            subtotalRole.toColor(this)
        else ContextCompat.getColor(this, R.color.score_calculated_cell_text)

        val subtotalCell = makeSubCell(
            text      = if (stars != null && districts != null) subtotal.toString() else "",
            bgColor   = ContextCompat.getColor(this, R.color.cell_calculated_bg),
            textColor = subtotalColor,
            bold      = true,
            hint      = ""
        )
        container.addView(subtotalCell)

        return container
    }

    private fun makeStonesCell(ps: AkropolisPlayerScore): TextView {
        val allStones = playerScores.map { it.stones }
        val role      = ScoreColorRole(ps.stones, allStones, higherIsBetter = true)
        val bgRes     = if (!gameOver && ps.stones == null) R.color.cell_editable_bg else R.color.score_cell_background
        return TextView(this).apply {
            text = ps.stones?.toString() ?: ""
            gravity = Gravity.CENTER; textSize = 14f
            if (role != ScoreColorRole.NEUTRAL && ps.stones != null) setTypeface(null, Typeface.BOLD)
            setTextColor(
                if (role != ScoreColorRole.NEUTRAL && ps.stones != null) role.toColor(this@AkropolisGameActivity)
                else ContextCompat.getColor(this@AkropolisGameActivity, R.color.score_cell_text)
            )
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            background = borderDrawable(ContextCompat.getColor(this@AkropolisGameActivity, bgRes))
            if (!gameOver) setOnClickListener { showStonesInput(ps) }
        }
    }

    private fun makeTotalCell(ps: AkropolisPlayerScore): TextView {
        val allComplete = playerScores.all { it.isComplete() }
        val total       = ps.getTotal()
        val role        = if (allComplete) ScoreColorRole(total, playerScores.map { it.getTotal() }, higherIsBetter = true)
                          else ScoreColorRole.NEUTRAL
        val textColor   = if (role != ScoreColorRole.NEUTRAL) role.toColor(this)
                          else ContextCompat.getColor(this, R.color.score_calculated_cell_text)
        return TextView(this).apply {
            text = total.toString(); gravity = Gravity.CENTER; textSize = 16f
            setTypeface(null, Typeface.BOLD); setTextColor(textColor)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            background = borderDrawable(ContextCompat.getColor(this@AkropolisGameActivity, R.color.cell_calculated_bg))
        }
    }

    // ─── Sub-cell helpers ─────────────────────────────────────────────────────

    private fun makeSubCell(text: String, bgColor: Int, textColor: Int, bold: Boolean, hint: String): TextView =
        TextView(this).apply {
            this.text = text; gravity = Gravity.CENTER; textSize = 13f
            if (bold) setTypeface(null, Typeface.BOLD)
            setTextColor(textColor)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            background = GradientDrawable().apply { setColor(bgColor) }
            if (text.isEmpty() && hint.isNotEmpty()) {
                this.hint = hint
            }
        }

    private fun makeDivider(): android.view.View = android.view.View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
        setBackgroundColor(ContextCompat.getColor(this@AkropolisGameActivity, R.color.cell_border))
    }

    // ─── Dialogs ──────────────────────────────────────────────────────────────

    private fun showSubScoreInput(ps: AkropolisPlayerScore, color: AkropolisColor, isStars: Boolean) {
        val current = if (isStars) ps.stars[color] else ps.districts[color]
        val labelType = if (isStars) getString(R.string.akropolis_stars) else getString(R.string.akropolis_districts)
        val colorLabel = colorLabel(color)
        val title = if (current != null) "✏️ ${ps.playerName} — $colorLabel ($labelType)"
                    else "${ps.playerName} — $colorLabel ($labelType)"

        val editText = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "0–$MAX_INPUT"; gravity = Gravity.CENTER; textSize = 20f
            filters = arrayOf(InputFilter.LengthFilter(2))
            current?.let { setText(it.toString()) }
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(24), dpToPx(8), dpToPx(24), dpToPx(8))
            addView(editText)
        }
        val dialog = AlertDialog.Builder(this).setTitle(title).setView(container)
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                val value = editText.text.toString().trim().toIntOrNull()
                if (value == null || value < 0 || value > MAX_INPUT) {
                    showSubScoreInput(ps, color, isStars); return@setPositiveButton
                }
                if (isStars) ps.stars[color] = value else ps.districts[color] = value
                buildScoreTable()
                checkCompletion()
            }
            .setNegativeButton(getString(R.string.cancel), null).create()

        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show(); editText.requestFocus()
    }

    private fun showStonesInput(ps: AkropolisPlayerScore) {
        val current = ps.stones
        val title = if (current != null) "✏️ ${ps.playerName} — ${getString(R.string.akropolis_stones)}"
                    else "${ps.playerName} — ${getString(R.string.akropolis_stones)}"

        val editText = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "0–$MAX_INPUT"; gravity = Gravity.CENTER; textSize = 20f
            filters = arrayOf(InputFilter.LengthFilter(2))
            current?.let { setText(it.toString()) }
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(24), dpToPx(8), dpToPx(24), dpToPx(8))
            addView(editText)
        }
        val dialog = AlertDialog.Builder(this).setTitle(title).setView(container)
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                val value = editText.text.toString().trim().toIntOrNull()
                if (value == null || value < 0 || value > MAX_INPUT) {
                    showStonesInput(ps); return@setPositiveButton
                }
                ps.stones = value
                buildScoreTable()
                checkCompletion()
            }
            .setNegativeButton(getString(R.string.cancel), null).create()

        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show(); editText.requestFocus()
    }

    // ─── Game logic ───────────────────────────────────────────────────────────

    private fun checkCompletion() {
        if (playerScores.all { it.isComplete() } && !gameOver) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.akropolis_game_complete))
                .setMessage(getString(R.string.akropolis_game_complete_message))
                .setPositiveButton(getString(R.string.yes)) { _, _ ->
                    gameOver = true; buildScoreTable(); saveResultsAndShowSummary()
                }
                .setNegativeButton(getString(R.string.no), null).show()
        }
    }

    private fun saveResultsAndShowSummary() {
        val totals   = playerScores.associate { it to it.getTotal() }
        val maxScore = totals.values.maxOrNull() ?: 0
        val winners  = totals.filter { it.value == maxScore }.keys
        val isDraw   = winners.size > 1
        lifecycleScope.launch {
            database.gameResultDao().insertGameResults(playerScores.map { ps ->
                GameResult(
                    gameType   = GAME_TYPE, playerId = ps.playerId,
                    playerName = ps.playerName, score = ps.getTotal(),
                    isWinner   = !isDraw && ps in winners,
                    isDraw     = isDraw && ps in winners
                )
            })
            val sorted = totals.entries.sortedByDescending { it.value }
            var rank = 1
            val entries = sorted.mapIndexed { i, (ps, s) ->
                val r = if (i > 0 && s == sorted[i - 1].value) rank else { rank = i + 1; rank }
                GameResultsDialog.PlayerResult(ps.playerName, ps.playerColor, s, r)
            }
            GameResultsDialog.show(this@AkropolisGameActivity, entries, isDraw, " pts") { finish() }
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun colorLabel(color: AkropolisColor): String = when (color) {
        AkropolisColor.BLUE   -> getString(R.string.akropolis_color_blue)
        AkropolisColor.YELLOW -> getString(R.string.akropolis_color_yellow)
        AkropolisColor.RED    -> getString(R.string.akropolis_color_red)
        AkropolisColor.PURPLE -> getString(R.string.akropolis_color_purple)
        AkropolisColor.GREEN  -> getString(R.string.akropolis_color_green)
    }

    private fun colorToArgb(color: AkropolisColor): Int = when (color) {
        AkropolisColor.BLUE   -> 0xFF1565C0.toInt()
        AkropolisColor.YELLOW -> 0xFFF9A825.toInt()
        AkropolisColor.RED    -> 0xFFC62828.toInt()
        AkropolisColor.PURPLE -> 0xFF6A1B9A.toInt()
        AkropolisColor.GREEN  -> 0xFF2E7D32.toInt()
    }

    private fun makeColumn(weight: Float, widthDp: Int): LinearLayout {
        val widthPx = if (widthDp == 0) 0 else dpToPx(widthDp)
        return LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(widthPx, LinearLayout.LayoutParams.MATCH_PARENT, weight)
        }
    }

    private fun borderDrawable(bgColor: Int): GradientDrawable = GradientDrawable().apply {
        setColor(bgColor)
        setStroke(1, ContextCompat.getColor(this@AkropolisGameActivity, R.color.cell_border))
    }

    private fun dotDrawable(color: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

    private fun playerCellDrawable(bgColor: Int): GradientDrawable = GradientDrawable().apply {
        setColor(bgColor)
        setStroke(1, ContextCompat.getColor(this@AkropolisGameActivity, R.color.cell_border))
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_akropolis_game, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                AlertDialog.Builder(this)
                    .setTitle(R.string.akropolis_quit_game)
                    .setMessage(R.string.akropolis_quit_game_message)
                    .setPositiveButton(R.string.yes) { _, _ -> finish() }
                    .setNegativeButton(R.string.no, null).show()
                true
            }
            R.id.action_help -> { HelpDialogs.showAppHelp(this, GAME_TYPE); true }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
