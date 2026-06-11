package com.github.trivialloop.scorehub.games.akropolis

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageView
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
        const val GAME_TYPE    = "akropolis"
        private const val ICON_COL_DP = 65   // row-type icon column
    }

    // ─── Possible picker values ───────────────────────────────────────────────

    /** Star (multiplier) values vary by color */
    private fun starsValues(color: AkropolisColor): List<Int> = when (color) {
        AkropolisColor.BLUE     -> (0..8).toList()
        AkropolisColor.YELLOW   -> (0..6).map { it * 2 }
        AkropolisColor.RED      -> (0..6).map { it * 2 }
        AkropolisColor.PURPLE   -> (0..6).map { it * 2 }
        AkropolisColor.GREEN    -> (0..5).map { it * 3 }
    }

    /** Districts values vary by color */
    private fun districtsValues(color: AkropolisColor): List<Int> = when (color) {
        AkropolisColor.BLUE     -> (0..50).toList()
        AkropolisColor.YELLOW   -> (0..35).toList()
        AkropolisColor.RED      -> (0..30).toList()
        AkropolisColor.PURPLE   -> (0..25).toList()
        AkropolisColor.GREEN    -> (0..20).toList()
    }

    private val stonesValues: List<Int>    = (0..30).toList()

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun attachBaseContext(newBase: Context) {
        val language = LocaleHelper.getPersistedLocale(newBase)
        super.attachBaseContext(LocaleHelper.setLocale(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAkropolisGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        val controller = WindowCompat.getInsetsController(window, window.decorView)

        val darkMode =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES

        controller.isAppearanceLightStatusBars = !darkMode

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->

            val systemBars =
                insets.getInsets(WindowInsetsCompat.Type.systemBars())

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
        binding.scoreTableContainer.addView(buildIconColumn())
        for (ps in playerScores) binding.scoreTableContainer.addView(buildPlayerColumn(ps))
    }

    // ─── Category Column ────────────────────────────────────────────────

    private fun buildIconColumn(): LinearLayout {
        val col = makeFixedColumn(ICON_COL_DP)
        col.addView(makeBlankCell(rows = 1f, isCalc = false)) // header spacer

        for (color in AkropolisColor.entries) {
            // Group of 3 icon sub-rows, total weight 3f
            val bgColor = colorToArgb(color)

            val group = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 3f)
                background = borderDrawable(bgColor)
            }

            group.addView(TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
                gravity = Gravity.CENTER
                text = "⭐"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                setTextColor(ContextCompat.getColor(this@AkropolisGameActivity, android.R.color.black))
                setBackgroundColor(Color.TRANSPARENT)
            })
            group.addView(makeThinDivider())

            group.addView(TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
                gravity = Gravity.CENTER
                text = "🏘️"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                setTextColor(ContextCompat.getColor(this@AkropolisGameActivity, android.R.color.black))
                setBackgroundColor(Color.TRANSPARENT)
            })
            group.addView(makeThinDivider())

            group.addView(TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
                gravity = Gravity.CENTER
                text = "🟰"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                setTextColor(ContextCompat.getColor(this@AkropolisGameActivity, android.R.color.black))
                setBackgroundColor(Color.TRANSPARENT)
            })

            col.addView(group)
        }

        // Stones icon (single row)
        col.addView(TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            gravity = Gravity.CENTER
            text = "🪨"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            setTextColor(ContextCompat.getColor(this@AkropolisGameActivity, android.R.color.black))
            background = borderDrawable(
                ContextCompat.getColor(this@AkropolisGameActivity, R.color.header_cell_background))
        })

        // Total
        col.addView(TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            gravity = Gravity.CENTER
            text = getString(R.string.akropolis_total)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(ContextCompat.getColor(this@AkropolisGameActivity, android.R.color.black))
            background = borderDrawable(
                ContextCompat.getColor(this@AkropolisGameActivity, R.color.header_cell_background))
        })

        return col
    }


    // ─── Player column ────────────────────────────────────────────────────────

    private fun buildPlayerColumn(ps: AkropolisPlayerScore): LinearLayout {
        val col = makeWeightColumn()
        col.addView(makePlayerNameCell(ps))
        for (color in AkropolisColor.entries) col.addView(makeColorScoreCell(ps, color))
        col.addView(makeStonesCell(ps))
        col.addView(makeTotalCell(ps))
        return col
    }

    // ─── Dot-column cell helpers ──────────────────────────────────────────────

    private fun makeColorDotCell(color: AkropolisColor): LinearLayout {
        return LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            gravity      = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 3f)
            background = borderDrawable(
                ContextCompat.getColor(this@AkropolisGameActivity, R.color.header_cell_background))
            val dot = android.view.View(this@AkropolisGameActivity).apply {
                val s = dpToPx(20)
                layoutParams = LinearLayout.LayoutParams(s, s).apply { gravity = Gravity.CENTER }
                background = dotDrawable(colorToArgb(color))
            }
            addView(dot)
        }
    }

    private fun makeBlankCell(rows: Float, isCalc: Boolean): LinearLayout = LinearLayout(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, rows)
        val bgRes = if (isCalc) R.color.cell_calculated_bg else R.color.header_cell_background
        background = borderDrawable(ContextCompat.getColor(this@AkropolisGameActivity, bgRes))
    }

    // ─── Icon-column cell helpers ─────────────────────────────────────────────

    private fun makeIconSubCell(iconRes: Int): LinearLayout = LinearLayout(this).apply {
        orientation  = LinearLayout.VERTICAL
        gravity      = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        addView(iconView(iconRes))
    }

    private fun makeIconSubCellStandalone(iconRes: Int): LinearLayout = LinearLayout(this).apply {
        orientation  = LinearLayout.VERTICAL
        gravity      = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        background = borderDrawable(
            ContextCompat.getColor(this@AkropolisGameActivity, R.color.header_cell_background))
        addView(iconView(iconRes))
    }

    private fun iconView(iconRes: Int): ImageView = ImageView(this).apply {
        val size = dpToPx(15)
        layoutParams = LinearLayout.LayoutParams(size, size)
        setImageResource(iconRes)
        imageTintList = android.content.res.ColorStateList.valueOf(
            ContextCompat.getColor(this@AkropolisGameActivity, R.color.header_cell_text))
        scaleType = ImageView.ScaleType.FIT_CENTER
    }

    // ─── Player-column cell helpers ───────────────────────────────────────────

    private fun makePlayerNameCell(ps: AkropolisPlayerScore): TextView = TextView(this).apply {
        text = ps.playerName; gravity = Gravity.CENTER; textSize = 12f
        setTypeface(null, Typeface.BOLD)
        maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        background = playerCellDrawable(ps.playerColor)
        setTextColor(Color.WHITE)
    }

    /**
     * Color score cell — 3 sub-rows (weight 3f):
     *   [0] stars picker   — no color role
     *   [1] districts picker — no color role
     *   [2] subtotal (calculated) — color role applied here only
     */
    private fun makeColorScoreCell(ps: AkropolisPlayerScore, color: AkropolisColor): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 3f)
            background = borderDrawable(
                ContextCompat.getColor(this@AkropolisGameActivity, R.color.score_cell_background))
        }

        val stars     = ps.stars[color]
        val districts = ps.districts[color]
        val subtotal  = ps.getDistrictTotal(color)

        // Subtotal role — compared across all players for this color
        val allSubtotals = playerScores.map {
            if (it.stars[color] != null && it.districts[color] != null)
                it.getDistrictTotal(color) else null
        }
        val subtotalRole = ScoreColorRole(
            if (stars != null && districts != null) subtotal else null,
            allSubtotals,
            higherIsBetter = true
        )

        // Stars
        val starsBg = if (!gameOver && stars == null) R.color.cell_editable_bg
                      else R.color.score_cell_background
        val starsCell = makeSubCell(
            text      = stars?.toString() ?: "",
            bgColor   = ContextCompat.getColor(this, starsBg),
            textColor = ContextCompat.getColor(this, R.color.score_cell_text),
            bold      = false
        )
        if (!gameOver) starsCell.setOnClickListener {
            showPicker(
                title   = "${ps.playerName} — ${colorLabel(color)} (${getString(R.string.akropolis_stars)})",
                values  = starsValues(color),
                current = stars,
                isEdit  = stars != null
            ) { v -> ps.stars[color] = v; buildScoreTable(); checkCompletion() }
        }
        container.addView(starsCell)
        container.addView(makeThinDivider())

        // Districts
        val distBg = if (!gameOver && districts == null) R.color.cell_editable_bg
                     else R.color.score_cell_background
        val distCell = makeSubCell(
            text      = districts?.toString() ?: "",
            bgColor   = ContextCompat.getColor(this, distBg),
            textColor = ContextCompat.getColor(this, R.color.score_cell_text),
            bold      = false
        )
        if (!gameOver) distCell.setOnClickListener {
            showPicker(
                title   = "${ps.playerName} — ${colorLabel(color)} (${getString(R.string.akropolis_districts)})",
                values  = districtsValues(color),
                current = districts,
                isEdit  = districts != null
            ) { v -> ps.districts[color] = v; buildScoreTable(); checkCompletion() }
        }
        container.addView(distCell)
        container.addView(makeThinDivider())

        // Subtotal (color-coded)
        val subtotalText  = if (stars != null && districts != null) subtotal.toString() else ""
        val subtotalColor = when {
            subtotalRole == ScoreColorRole.BEST  && subtotalText.isNotEmpty() ->
                ContextCompat.getColor(this, R.color.score_text_best)
            subtotalRole == ScoreColorRole.WORST && subtotalText.isNotEmpty() ->
                ContextCompat.getColor(this, R.color.score_text_worst)
            else -> ContextCompat.getColor(this, R.color.score_calculated_cell_text)
        }
        container.addView(makeSubCell(
            text      = subtotalText,
            bgColor   = ContextCompat.getColor(this, R.color.cell_calculated_bg),
            textColor = subtotalColor,
            bold      = subtotalRole != ScoreColorRole.NEUTRAL && subtotalText.isNotEmpty()
        ))

        return container
    }

    private fun makeStonesCell(ps: AkropolisPlayerScore): TextView {
        val allStones = playerScores.map { it.stones }
        val role      = ScoreColorRole(ps.stones, allStones, higherIsBetter = true)
        val bgRes     = if (!gameOver && ps.stones == null) R.color.cell_editable_bg
                        else R.color.score_cell_background
        val textColor = when {
            role == ScoreColorRole.BEST  && ps.stones != null ->
                ContextCompat.getColor(this, R.color.score_text_best)
            role == ScoreColorRole.WORST && ps.stones != null ->
                ContextCompat.getColor(this, R.color.score_text_worst)
            else -> ContextCompat.getColor(this, R.color.score_cell_text)
        }
        return TextView(this).apply {
            text = ps.stones?.toString() ?: ""
            gravity = Gravity.CENTER; textSize = 13f
            if (role != ScoreColorRole.NEUTRAL && ps.stones != null) setTypeface(null, Typeface.BOLD)
            setTextColor(textColor)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            background = borderDrawable(ContextCompat.getColor(this@AkropolisGameActivity, bgRes))
            if (!gameOver) setOnClickListener {
                showPicker(
                    title   = "${ps.playerName} — ${getString(R.string.akropolis_stones)}",
                    values  = stonesValues,
                    current = ps.stones,
                    isEdit  = ps.stones != null
                ) { v -> ps.stones = v; buildScoreTable(); checkCompletion() }
            }
        }
    }

    private fun makeTotalCell(ps: AkropolisPlayerScore): TextView {
        val allComplete = playerScores.all { it.isComplete() }
        val total       = ps.getTotal()
        val role        = if (allComplete) ScoreColorRole(
            total, playerScores.map { it.getTotal() }, higherIsBetter = true)
                          else ScoreColorRole.NEUTRAL
        val textColor = when (role) {
            ScoreColorRole.BEST  -> ContextCompat.getColor(this, R.color.score_text_best)
            ScoreColorRole.WORST -> ContextCompat.getColor(this, R.color.score_text_worst)
            else -> ContextCompat.getColor(this, R.color.score_calculated_cell_text)
        }
        return TextView(this).apply {
            text = total.toString(); gravity = Gravity.CENTER; textSize = 15f
            setTypeface(null, Typeface.BOLD); setTextColor(textColor)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            background = borderDrawable(ContextCompat.getColor(
                this@AkropolisGameActivity, R.color.cell_calculated_bg))
        }
    }

    // ─── Picker ───────────────────────────────────────────────────────────────

    private fun showPicker(
        title: String,
        values: List<Int>,
        current: Int?,
        isEdit: Boolean,
        onPicked: (Int) -> Unit
    ) {
        val dialogTitle = if (isEdit) "✏️ $title" else title
        val items = values.map { it.toString() }.toTypedArray()
        val dialog = AlertDialog.Builder(this)
            .setTitle(dialogTitle)
            .setItems(items) { _, which -> onPicked(values[which]) }
            .create()
        dialog.show()
        if (isEdit && current != null) {
            val idx = values.indexOf(current)
            if (idx >= 0) dialog.listView?.setSelection(idx)
        }
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
                val r = if (i > 0 && s == sorted[i - 1].value) rank
                        else { rank = i + 1; rank }
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

    private fun makeSubCell(text: String, bgColor: Int, textColor: Int, bold: Boolean): TextView =
        TextView(this).apply {
            this.text = text; gravity = Gravity.CENTER; textSize = 13f
            if (bold) setTypeface(null, Typeface.BOLD)
            setTextColor(textColor)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            background = GradientDrawable().apply { setColor(bgColor) }
        }

    private fun makeThinDivider(): android.view.View = android.view.View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
        setBackgroundColor(ContextCompat.getColor(this@AkropolisGameActivity, R.color.cell_border))
    }

    private fun makeFixedColumn(widthDp: Int): LinearLayout = LinearLayout(this).apply {
        orientation  = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(dpToPx(widthDp), LinearLayout.LayoutParams.MATCH_PARENT)
    }

    private fun makeWeightColumn(): LinearLayout = LinearLayout(this).apply {
        orientation  = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
    }

    private fun borderDrawable(bgColor: Int): GradientDrawable = GradientDrawable().apply {
        setColor(bgColor)
        setStroke(1, ContextCompat.getColor(this@AkropolisGameActivity, R.color.cell_border))
    }

    private fun dotDrawable(color: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL; setColor(color)
    }

    private fun playerCellDrawable(bgColor: Int): GradientDrawable = GradientDrawable().apply {
        setColor(bgColor)
        setStroke(1, ContextCompat.getColor(this@AkropolisGameActivity, R.color.cell_border))
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_akropolis_game, menu); return true
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
