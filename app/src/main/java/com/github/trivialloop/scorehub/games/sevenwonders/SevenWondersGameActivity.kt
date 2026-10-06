package com.github.trivialloop.scorehub.games.sevenwonders

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
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
import com.github.trivialloop.scorehub.databinding.ActivitySevenwondersGameBinding
import com.github.trivialloop.scorehub.ui.GameResultsDialog
import com.github.trivialloop.scorehub.ui.HelpDialogs
import com.github.trivialloop.scorehub.utils.GameSymbols
import com.github.trivialloop.scorehub.utils.LocaleHelper
import com.github.trivialloop.scorehub.utils.ScoreColorRole
import kotlinx.coroutines.launch

/**
 * Row-based one-shot grid for 7 Wonders, same family as HarmoniesGameActivity /
 * TicketToRideGameActivity: fixed categories are single rows, Guilds are entered one card
 * at a time (like Harmonies' Animal cards), and the game is finished manually via a
 * "Finish game" button rather than an automatic completion check — since a variable-length
 * guild list has no natural "all fields filled" trigger.
 *
 * Science is a single row whose cell holds 3 tappable sub-buttons (Compass / Gear / Tablet),
 * with the computed subtotal shown on the row directly below — mirrors the coin row, which
 * also shows the raw input on one row and the computed (÷3) subtotal on the row below it.
 *
 * Category labels are icon-only (no text) — the color + icon combination is enough to
 * identify the row, keeping the grid compact.
 */
class SevenWondersGameActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySevenwondersGameBinding
    private lateinit var database: AppDatabase
    private lateinit var playerIds: LongArray
    private lateinit var playerNames: Array<String>
    private lateinit var playerColors: IntArray
    private lateinit var players: List<SevenWondersPlayerScore>

    private var gameOver = false

    companion object {
        const val GAME_TYPE = "sevenwonders"
        private const val LABEL_COL_DP = 56
        private const val ROW_HEIGHT_DP = 44
        private const val GUILD_ROW_HEIGHT_DP = 38

        private val MILITARY   = GameSymbols.CROSSED_SWORDS
        private val COINS      = GameSymbols.COIN
        private val WONDER     = GameSymbols.TOKYO_TOWER
        private val CIVILIAN   = GameSymbols.HOUSE.copy(color = 0xFF1565C0.toInt())
        private val COMMERCE   = GameSymbols.BALANCE_SCALE
        private val GUILD      = GameSymbols.ALEMBIC
        private val SCIENCE    = GameSymbols.MICROSCOPE
        private val COMPASS_S  = GameSymbols.COMPASS
        private val GEAR_S     = GameSymbols.GEAR
        private val TABLET_S   = GameSymbols.SCROLL
    }

    private enum class FlatCategory { MILITARY, WONDER, CIVILIAN, COMMERCE }
    private enum class ScienceSymbol { COMPASS, GEAR, TABLET }

    override fun attachBaseContext(newBase: Context) {
        val language = LocaleHelper.getPersistedLocale(newBase)
        super.attachBaseContext(LocaleHelper.setLocale(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySevenwondersGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.appBarLayout.setPadding(0, systemBars.top, 0, 0)
            binding.root.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            insets
        }

        database = AppDatabase.getDatabase(this)
        playerIds = intent.getLongArrayExtra("PLAYER_IDS") ?: longArrayOf()
        playerNames = intent.getStringArrayExtra("PLAYER_NAMES") ?: arrayOf()
        playerColors = intent.getIntArrayExtra("PLAYER_COLORS") ?: intArrayOf()

        players = playerIds.indices.map { i ->
            SevenWondersPlayerScore(playerIds[i], playerNames[i], playerColors[i])
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.sevenwonders_game)

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

        binding.tableContainer.addView(buildFlatCategoryRow(FlatCategory.MILITARY, MILITARY, signed = true))
        binding.tableContainer.addView(buildCoinsRow())
        binding.tableContainer.addView(buildFlatCategoryRow(FlatCategory.WONDER, WONDER))
        binding.tableContainer.addView(buildFlatCategoryRow(FlatCategory.CIVILIAN, CIVILIAN))
        binding.tableContainer.addView(buildFlatCategoryRow(FlatCategory.COMMERCE, COMMERCE))

        binding.tableContainer.addView(buildScienceInputRow())
        binding.tableContainer.addView(buildScienceSubtotalRow())

        binding.tableContainer.addView(buildGuildHeaderRow())
        val maxGuilds = players.maxOf { it.guildEntries.size }
        for (slot in 0 until maxGuilds) {
            binding.tableContainer.addView(buildGuildSlotRow(slot))
        }
        binding.tableContainer.addView(buildGuildSubtotalRow())

        binding.tableContainer.addView(buildTotalRow())

        binding.btnFinishGame.isEnabled = !gameOver
        binding.btnFinishGame.alpha = if (gameOver) 0.5f else 1f

        binding.scrollView.post { binding.scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    // ─── Header ────────────────────────────────────────────────────────────────

    private fun buildHeaderRow(): LinearLayout {
        val row = makeRow(ROW_HEIGHT_DP)
        row.addView(makeIconLabelCell("", Color.TRANSPARENT, ROW_HEIGHT_DP, isCalc = false))
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

    // ─── Flat categories (Military / Wonder / Civilian / Commerce) ───────────────

    private fun buildFlatCategoryRow(category: FlatCategory, style: GameSymbols.RowStyle, signed: Boolean = false): LinearLayout {
        val row = makeRow(ROW_HEIGHT_DP)
        row.addView(makeIconLabelCell(style.emoji, style.color, ROW_HEIGHT_DP, isCalc = false))

        val allValues = players.map { flatValue(it, category) }
        for (player in players) {
            val value = flatValue(player, category)
            val role = if (allValues.all { it != null }) ScoreColorRole(value, allValues, higherIsBetter = true) else ScoreColorRole.NEUTRAL
            val bgColor = if (!gameOver) R.color.cell_editable_bg else R.color.score_cell_background
            val text = if (signed && value != null && value > 0) "+$value" else value?.toString() ?: ""
            val cell = makeCell(text, ROW_HEIGHT_DP, bold = role != ScoreColorRole.NEUTRAL && value != null)
            cell.background = cellDrawable(ContextCompat.getColor(this, bgColor))
            if (role != ScoreColorRole.NEUTRAL && value != null) cell.setTextColor(role.toColor(this))
            if (!gameOver) cell.setOnClickListener { showFlatCategoryPicker(player, category) }
            row.addView(cell)
        }
        return row
    }

    private fun flatValue(ps: SevenWondersPlayerScore, category: FlatCategory): Int? = when (category) {
        FlatCategory.MILITARY -> ps.militaryPoints
        FlatCategory.WONDER -> ps.wonderPoints
        FlatCategory.CIVILIAN -> ps.civilianPoints
        FlatCategory.COMMERCE -> ps.commercePoints
    }

    private fun setFlatValue(ps: SevenWondersPlayerScore, category: FlatCategory, value: Int) {
        when (category) {
            FlatCategory.MILITARY -> ps.militaryPoints = value
            FlatCategory.WONDER -> ps.wonderPoints = value
            FlatCategory.CIVILIAN -> ps.civilianPoints = value
            FlatCategory.COMMERCE -> ps.commercePoints = value
        }
    }

    private fun flatValues(category: FlatCategory): List<Int> = when (category) {
        FlatCategory.MILITARY -> SevenWondersValues.MILITARY_VALUES
        FlatCategory.WONDER -> SevenWondersValues.WONDER_VALUES
        FlatCategory.CIVILIAN -> SevenWondersValues.CIVILIAN_VALUES
        FlatCategory.COMMERCE -> SevenWondersValues.COMMERCE_VALUES
    }

    private fun flatStyle(category: FlatCategory): GameSymbols.RowStyle = when (category) {
        FlatCategory.MILITARY -> MILITARY
        FlatCategory.WONDER   -> WONDER
        FlatCategory.CIVILIAN -> CIVILIAN
        FlatCategory.COMMERCE -> COMMERCE
    }

    private fun showFlatCategoryPicker(ps: SevenWondersPlayerScore, category: FlatCategory) {
        val current = flatValue(ps, category)
        val values = flatValues(category)
        val items = values.map { it.toString() }.toTypedArray()
        val icon = flatStyle(category).emoji
        val title = if (current != null) "✏️ ${ps.playerName} — $icon" else "${ps.playerName} — $icon"

        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(items) { _, which ->
                setFlatValue(ps, category, values[which])
                buildTable()
            }
            .create()
        dialog.show()
        val scrollTo = if (current != null) values.indexOf(current) else values.indexOf(0).coerceAtLeast(0)
        if (scrollTo >= 0) dialog.listView?.post { dialog.listView?.setSelection(scrollTo) }
    }

    // ─── Coins: input row + computed points row ───────────────────────────────

    private fun buildCoinsRow(): LinearLayout {
        val row = makeRow(ROW_HEIGHT_DP)
        row.addView(makeIconLabelCell(COINS.emoji, COINS.color, ROW_HEIGHT_DP, isCalc = false))

        val allPts = players.map { if (it.coins != null) it.getCoinPoints() else null }
        val allEntered = allPts.all { it != null }

        for ((i, player) in players.withIndex()) {
            val pts = allPts[i]
            val role = if (allEntered) ScoreColorRole(pts, allPts, higherIsBetter = true) else ScoreColorRole.NEUTRAL
            val bgColor = if (!gameOver) R.color.cell_editable_bg else R.color.score_cell_background

            val cell = makeCoinsCell(pts, player.coins, role)
            cell.background = cellDrawable(ContextCompat.getColor(this, bgColor))
            if (!gameOver) cell.setOnClickListener { showCoinsPicker(player) }
            row.addView(cell)
        }
        return row
    }

    /** Points centered (large), raw coin count bottom-right (small, light grey) — same look as Cactus. */
    private fun makeCoinsCell(points: Int?, coins: Int?, role: ScoreColorRole): FrameLayout = FrameLayout(this).apply {
        layoutParams = LinearLayout.LayoutParams(0, dpToPx(ROW_HEIGHT_DP), 1f)

        addView(TextView(this@SevenWondersGameActivity).apply {
            text = points?.toString() ?: ""
            gravity = Gravity.CENTER
            textSize = 16f
            if (role != ScoreColorRole.NEUTRAL) {
                setTypeface(null, Typeface.BOLD)
                setTextColor(role.toColor(this@SevenWondersGameActivity))
            } else {
                setTextColor(ContextCompat.getColor(this@SevenWondersGameActivity, R.color.score_cell_text))
            }
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        })

        if (coins != null) {
            addView(TextView(this@SevenWondersGameActivity).apply {
                text = coins.toString()
                textSize = 11f
                alpha = 0.45f
                setTextColor(ContextCompat.getColor(this@SevenWondersGameActivity, R.color.score_cell_text))
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM or Gravity.END
                ).also {
                    it.marginEnd = dpToPx(4)
                    it.bottomMargin = dpToPx(2)
                }
            })
        }
    }

    private fun showCoinsPicker(ps: SevenWondersPlayerScore) {
        val current = ps.coins
        val values = SevenWondersValues.COINS_VALUES
        val items = values.map { it.toString() }.toTypedArray()
        val title = if (current != null) "✏️ ${ps.playerName} — ${COINS.emoji}" else "${ps.playerName} — ${COINS.emoji}"

        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(items) { _, which ->
                ps.coins = values[which]
                buildTable()
            }
            .create()
        dialog.show()
        val scrollTo = current ?: 0
        dialog.listView?.post { dialog.listView?.setSelection(scrollTo) }
    }

    // ─── Science: single row with 3 sub-buttons + subtotal row below ──────────

    private fun buildScienceInputRow(): LinearLayout {
        val row = makeRow(ROW_HEIGHT_DP)
        row.addView(makeIconLabelCell(SCIENCE.emoji, SCIENCE.color, ROW_HEIGHT_DP, isCalc = false))
        for (player in players) {
            row.addView(makeScienceTripleCell(player))
        }
        return row
    }

    private fun makeScienceTripleCell(ps: SevenWondersPlayerScore): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            isBaselineAligned = false
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(ROW_HEIGHT_DP), 1f)
            background = cellDrawable(ContextCompat.getColor(this@SevenWondersGameActivity, R.color.header_cell_background))
        }
        container.addView(makeScienceSymbolButton(ps, ScienceSymbol.COMPASS, COMPASS_S.emoji))
        container.addView(makeScienceSymbolButton(ps, ScienceSymbol.GEAR, GEAR_S.emoji))
        container.addView(makeScienceSymbolButton(ps, ScienceSymbol.TABLET, TABLET_S.emoji))
        return container
    }

    private fun makeScienceSymbolButton(ps: SevenWondersPlayerScore, symbol: ScienceSymbol, icon: String): LinearLayout {
        val value = getScienceSymbolValue(ps, symbol)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            if (!gameOver) setOnClickListener { showScienceSymbolPicker(ps, symbol, icon) }
            addView(TextView(this@SevenWondersGameActivity).apply {
                text = if (value != null) "$icon$value" else icon
                gravity = Gravity.CENTER
                textSize = 12f
                alpha = if (value != null) 1f else 0.4f
                setTextColor(ContextCompat.getColor(this@SevenWondersGameActivity, R.color.score_cell_text))
            })
        }
    }

    private fun getScienceSymbolValue(ps: SevenWondersPlayerScore, symbol: ScienceSymbol): Int? = when (symbol) {
        ScienceSymbol.COMPASS -> ps.scienceCompass
        ScienceSymbol.GEAR -> ps.scienceGear
        ScienceSymbol.TABLET -> ps.scienceTablet
    }

    private fun setScienceSymbolValue(ps: SevenWondersPlayerScore, symbol: ScienceSymbol, value: Int) {
        when (symbol) {
            ScienceSymbol.COMPASS -> ps.scienceCompass = value
            ScienceSymbol.GEAR -> ps.scienceGear = value
            ScienceSymbol.TABLET -> ps.scienceTablet = value
        }
    }

    private fun showScienceSymbolPicker(ps: SevenWondersPlayerScore, symbol: ScienceSymbol, icon: String) {
        val current = getScienceSymbolValue(ps, symbol)
        val values = SevenWondersValues.SCIENCE_SYMBOL_VALUES
        val items = values.map { it.toString() }.toTypedArray()
        val label = "${ps.playerName} — $icon"
        val title = if (current != null) "✏️ $label" else label

        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(items) { _, which ->
                setScienceSymbolValue(ps, symbol, values[which])
                buildTable()
            }
            .create()
        dialog.show()
        if (current != null) dialog.listView?.post { dialog.listView?.setSelection(current) }
    }

    private fun hasAnyScienceSymbol(ps: SevenWondersPlayerScore): Boolean =
        ps.scienceCompass != null || ps.scienceGear != null || ps.scienceTablet != null

    private fun buildScienceSubtotalRow(): LinearLayout {
        val row = makeRow(ROW_HEIGHT_DP)
        row.addView(makeIconLabelCell("=", SCIENCE.color, ROW_HEIGHT_DP, isCalc = true))

        // Missing symbols are treated as 0, so the score shows as soon as one symbol is entered
        val allScience = players.map { if (hasAnyScienceSymbol(it)) it.getScienceScore() else null }
        val allEntered = allScience.all { it != null }

        for ((i, player) in players.withIndex()) {
            val value = allScience[i]
            val role = if (allEntered) ScoreColorRole(value, allScience, higherIsBetter = true) else ScoreColorRole.NEUTRAL
            val cell = makeCell(value?.toString() ?: "", ROW_HEIGHT_DP, bold = true)
            cell.background = cellDrawable(ContextCompat.getColor(this, R.color.cell_calculated_bg))
            cell.setTextColor(
                if (role != ScoreColorRole.NEUTRAL) role.toColor(this)
                else ContextCompat.getColor(this, R.color.score_calculated_cell_text)
            )
            row.addView(cell)
        }
        return row
    }

    // ─── Guilds: addable list, like Harmonies' Animal cards ────────────────────

    private fun buildGuildHeaderRow(): LinearLayout {
        val row = makeRow(ROW_HEIGHT_DP)
        row.addView(makeIconLabelCell(GUILD.emoji, GUILD.color, ROW_HEIGHT_DP, isCalc = false))
        for (player in players) {
            val cell = makeCell(if (gameOver) "" else "+", ROW_HEIGHT_DP, bold = true)
            cell.background = cellDrawable(
                ContextCompat.getColor(this, if (!gameOver) R.color.cell_editable_bg else R.color.header_cell_background)
            )
            if (!gameOver) cell.setOnClickListener { showAddGuildDialog(player) }
            row.addView(cell)
        }
        return row
    }

    private fun buildGuildSlotRow(slot: Int): LinearLayout {
        val row = makeRow(GUILD_ROW_HEIGHT_DP)
        row.addView(makeIconLabelCell("", GUILD.color, GUILD_ROW_HEIGHT_DP, isCalc = false))
        for (player in players) {
            val value = player.guildEntries.getOrNull(slot)
            val cell = makeCell(value?.let { "+$it" } ?: "", GUILD_ROW_HEIGHT_DP, bold = value != null, textSize = 13f)
            if (value != null) cell.setTextColor(ContextCompat.getColor(this, R.color.score_text_best))
            cell.background = cellDrawable(ContextCompat.getColor(this, R.color.score_cell_background))
            if (!gameOver && value != null) cell.setOnClickListener { showEditGuildDialog(player, slot) }
            row.addView(cell)
        }
        return row
    }

    private fun buildGuildSubtotalRow(): LinearLayout {
        val row = makeRow(ROW_HEIGHT_DP)
        row.addView(makeIconLabelCell("=", GUILD.color, ROW_HEIGHT_DP, isCalc = true))
        for (player in players) {
            val total = player.getGuildsTotal()
            val cell = makeCell(total.toString(), ROW_HEIGHT_DP, bold = true)
            cell.background = cellDrawable(ContextCompat.getColor(this, R.color.cell_calculated_bg))
            cell.setTextColor(ContextCompat.getColor(this, R.color.score_calculated_cell_text))
            row.addView(cell)
        }
        return row
    }

    private fun showAddGuildDialog(ps: SevenWondersPlayerScore) {
        val values = SevenWondersValues.GUILD_CARD_VALUES
        val items = values.map { it.toString() }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("${ps.playerName} — ${GUILD.emoji}")
            .setItems(items) { _, which ->
                ps.guildEntries.add(values[which])
                buildTable()
            }
            .show()
    }

    private fun showEditGuildDialog(ps: SevenWondersPlayerScore, slot: Int) {
        val current = ps.guildEntries.getOrNull(slot) ?: return
        AlertDialog.Builder(this)
            .setTitle("✏️ ${ps.playerName}")
            .setItems(arrayOf(
                getString(R.string.sevenwonders_edit_entry, current),
                getString(R.string.sevenwonders_delete_entry)
            )) { _, which ->
                when (which) {
                    0 -> showGuildValuePicker(ps, slot, current)
                    1 -> { ps.guildEntries.removeAt(slot); buildTable() }
                }
            }
            .show()
    }

    private fun showGuildValuePicker(ps: SevenWondersPlayerScore, slot: Int, current: Int) {
        val values = SevenWondersValues.GUILD_CARD_VALUES
        val items = values.map { it.toString() }.toTypedArray()
        val dialog = AlertDialog.Builder(this)
            .setTitle("✏️ ${ps.playerName} — ${GUILD.emoji}")
            .setItems(items) { _, which ->
                ps.guildEntries[slot] = values[which]
                buildTable()
            }
            .create()
        dialog.show()
        val idx = values.indexOf(current)
        if (idx >= 0) dialog.listView?.post { dialog.listView?.setSelection(idx) }
    }

    // ─── Total row ─────────────────────────────────────────────────────────────

    private fun buildTotalRow(): LinearLayout {
        val row = makeRow(ROW_HEIGHT_DP)
        row.addView(makeIconLabelCell(GameSymbols.TOTAL, Color.TRANSPARENT, ROW_HEIGHT_DP, isCalc = true))
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

    // ─── Finish game ────────────────────────────────────────────────────────────

    private fun confirmFinishGame() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.sevenwonders_finish_game_title))
            .setMessage(getString(R.string.sevenwonders_finish_game_message))
            .setPositiveButton(getString(R.string.yes)) { _, _ ->
                gameOver = true
                buildTable()
                saveResultsAndShowSummary()
            }
            .setNegativeButton(getString(R.string.no), null)
            .show()
    }

    private fun saveResultsAndShowSummary() {
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
                    isWinner = !isDraw && player in winners,
                    isDraw = isDraw && player in winners,
                    playedAt = playedAt
                )
            })
            val sorted = totals.entries.sortedByDescending { it.value }
            var rank = 1
            val entries = sorted.mapIndexed { i, (p, s) ->
                val r = if (i > 0 && s == sorted[i - 1].value) rank else { rank = i + 1; rank }
                GameResultsDialog.PlayerResult(p.playerName, p.playerColor, s, r)
            }
            GameResultsDialog.show(this@SevenWondersGameActivity, entries, isDraw, " pts") { finish() }
        }
    }

    // ─── Cell builders ─────────────────────────────────────────────────────────

    private fun makeRow(heightDp: Int): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(heightDp))
        isBaselineAligned = false
    }

    /** Icon-only label cell — color + icon identify the row, no text needed. */
    private fun makeIconLabelCell(icon: String, accent: Int, heightDp: Int, isCalc: Boolean): TextView = TextView(this).apply {
        text = icon
        gravity = Gravity.CENTER
        textSize = if (icon.length <= 2) 18f else 12f
        setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(dpToPx(LABEL_COL_DP), dpToPx(heightDp))
        val bg = when {
            accent != Color.TRANSPARENT -> accent
            isCalc -> ContextCompat.getColor(this@SevenWondersGameActivity, R.color.cell_calculated_bg)
            else -> ContextCompat.getColor(this@SevenWondersGameActivity, R.color.header_cell_background)
        }
        background = cellDrawable(bg)
        setTextColor(
            if (accent != Color.TRANSPARENT) Color.WHITE
            else if (isCalc) ContextCompat.getColor(this@SevenWondersGameActivity, R.color.score_calculated_cell_text)
            else ContextCompat.getColor(this@SevenWondersGameActivity, R.color.header_cell_text)
        )
    }

    private fun makeCell(text: String, heightDp: Int, bold: Boolean, textSize: Float = 14f): TextView =
        TextView(this).apply {
            this.text = text
            gravity = Gravity.CENTER
            this.textSize = textSize
            if (bold) setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(heightDp), 1f)
            background = cellDrawable(ContextCompat.getColor(this@SevenWondersGameActivity, R.color.score_cell_background))
            setTextColor(ContextCompat.getColor(this@SevenWondersGameActivity, R.color.score_cell_text))
        }

    private fun cellDrawable(bgColor: Int): GradientDrawable = GradientDrawable().apply {
        setColor(bgColor)
        setStroke(1, ContextCompat.getColor(this@SevenWondersGameActivity, R.color.cell_border))
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private fun showQuitGameDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.sevenwonders_quit_game)
            .setMessage(R.string.sevenwonders_quit_game_message)
            .setPositiveButton(R.string.yes) { _, _ -> finish() }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_sevenwonders_game, menu); return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { showQuitGameDialog(); true }
            R.id.action_help -> { HelpDialogs.showAppHelp(this, GAME_TYPE); true }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
