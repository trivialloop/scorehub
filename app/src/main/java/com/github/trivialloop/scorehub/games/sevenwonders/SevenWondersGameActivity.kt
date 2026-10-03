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

        private const val ICON_MILITARY = "⚔️"
        private const val ICON_COINS = "🪙"
        private const val ICON_WONDER = "🗼"
        private const val ICON_CIVILIAN = "🏠"
        private const val ICON_COMMERCE = "⚖️"
        private const val ICON_GUILD = "⚗️"
        private const val ICON_SCIENCE = "🔬"
        private const val ICON_COMPASS = "🧭"
        private const val ICON_GEAR = "⚙️"
        private const val ICON_TABLET = "📜"

        private val COLOR_MILITARY = 0xFFC62828.toInt()
        private val COLOR_COINS = 0xFFC9A227.toInt()
        private val COLOR_WONDER = 0xFF6D4C41.toInt()
        private val COLOR_CIVILIAN = 0xFF1565C0.toInt()
        private val COLOR_COMMERCE = 0xFFFBC02D.toInt()
        private val COLOR_GUILD = 0xFF6A1B9A.toInt()
        private val COLOR_SCIENCE = 0xFF2E7D32.toInt()
        private val COLOR_COMPASS = 0xFF1565C0.toInt()
        private val COLOR_GEAR = 0xFF757575.toInt()
        private val COLOR_TABLET = 0xFF2E7D32.toInt()
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

        binding.tableContainer.addView(buildFlatCategoryRow(FlatCategory.MILITARY, ICON_MILITARY, COLOR_MILITARY, signed = true))
        binding.tableContainer.addView(buildCoinsInputRow())
        binding.tableContainer.addView(buildCoinsPointsRow())
        binding.tableContainer.addView(buildFlatCategoryRow(FlatCategory.WONDER, ICON_WONDER, COLOR_WONDER))
        binding.tableContainer.addView(buildFlatCategoryRow(FlatCategory.CIVILIAN, ICON_CIVILIAN, COLOR_CIVILIAN))
        binding.tableContainer.addView(buildFlatCategoryRow(FlatCategory.COMMERCE, ICON_COMMERCE, COLOR_COMMERCE))

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

    private fun buildFlatCategoryRow(category: FlatCategory, icon: String, accent: Int, signed: Boolean = false): LinearLayout {
        val row = makeRow(ROW_HEIGHT_DP)
        row.addView(makeIconLabelCell(icon, accent, ROW_HEIGHT_DP, isCalc = false))

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

    private fun flatIcon(category: FlatCategory): String = when (category) {
        FlatCategory.MILITARY -> ICON_MILITARY
        FlatCategory.WONDER -> ICON_WONDER
        FlatCategory.CIVILIAN -> ICON_CIVILIAN
        FlatCategory.COMMERCE -> ICON_COMMERCE
    }

    private fun showFlatCategoryPicker(ps: SevenWondersPlayerScore, category: FlatCategory) {
        val current = flatValue(ps, category)
        val values = flatValues(category)
        val items = values.map { it.toString() }.toTypedArray()
        val title = if (current != null) "✏️ ${ps.playerName} — ${flatIcon(category)}" else "${ps.playerName} — ${flatIcon(category)}"

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

    private fun buildCoinsInputRow(): LinearLayout {
        val row = makeRow(ROW_HEIGHT_DP)
        row.addView(makeIconLabelCell(ICON_COINS, COLOR_COINS, ROW_HEIGHT_DP, isCalc = false))
        for (player in players) {
            val bgColor = if (!gameOver) R.color.cell_editable_bg else R.color.score_cell_background
            val cell = makeCell(player.coins?.toString() ?: "", ROW_HEIGHT_DP, bold = false)
            cell.background = cellDrawable(ContextCompat.getColor(this, bgColor))
            if (!gameOver) cell.setOnClickListener { showCoinsPicker(player) }
            row.addView(cell)
        }
        return row
    }

    private fun buildCoinsPointsRow(): LinearLayout {
        val row = makeRow(ROW_HEIGHT_DP)
        row.addView(makeIconLabelCell("=", COLOR_COINS, ROW_HEIGHT_DP, isCalc = true))
        val allPts = players.map { if (it.coins != null) it.getCoinPoints() else null }
        for (player in players) {
            val pts = if (player.coins != null) player.getCoinPoints() else null
            val role = if (allPts.all { it != null }) ScoreColorRole(pts, allPts, higherIsBetter = true) else ScoreColorRole.NEUTRAL
            val cell = makeCell(pts?.toString() ?: "", ROW_HEIGHT_DP, bold = true)
            cell.background = cellDrawable(ContextCompat.getColor(this, R.color.cell_calculated_bg))
            cell.setTextColor(
                if (role != ScoreColorRole.NEUTRAL) role.toColor(this)
                else ContextCompat.getColor(this, R.color.score_calculated_cell_text)
            )
            row.addView(cell)
        }
        return row
    }

    private fun showCoinsPicker(ps: SevenWondersPlayerScore) {
        val current = ps.coins
        val values = SevenWondersValues.COINS_VALUES
        val items = values.map { it.toString() }.toTypedArray()
        val title = if (current != null) "✏️ ${ps.playerName} — $ICON_COINS" else "${ps.playerName} — $ICON_COINS"

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
        row.addView(makeIconLabelCell(ICON_SCIENCE, COLOR_SCIENCE, ROW_HEIGHT_DP, isCalc = false))
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
        container.addView(makeScienceSymbolButton(ps, ScienceSymbol.COMPASS, ICON_COMPASS, COLOR_COMPASS))
        container.addView(makeScienceSymbolButton(ps, ScienceSymbol.GEAR, ICON_GEAR, COLOR_GEAR))
        container.addView(makeScienceSymbolButton(ps, ScienceSymbol.TABLET, ICON_TABLET, COLOR_TABLET))
        return container
    }

    private fun makeScienceSymbolButton(ps: SevenWondersPlayerScore, symbol: ScienceSymbol, icon: String, accent: Int): LinearLayout {
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

    private fun buildScienceSubtotalRow(): LinearLayout {
        val row = makeRow(ROW_HEIGHT_DP)
        row.addView(makeIconLabelCell("=", COLOR_SCIENCE, ROW_HEIGHT_DP, isCalc = true))
        val allScience = players.map {
            if (it.scienceCompass != null && it.scienceGear != null && it.scienceTablet != null) it.getScienceScore() else null
        }
        for (player in players) {
            val value = if (player.scienceCompass != null && player.scienceGear != null && player.scienceTablet != null)
                player.getScienceScore() else null
            val role = if (allScience.all { it != null }) ScoreColorRole(value, allScience, higherIsBetter = true) else ScoreColorRole.NEUTRAL
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
        row.addView(makeIconLabelCell(ICON_GUILD, COLOR_GUILD, ROW_HEIGHT_DP, isCalc = false))
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
        row.addView(makeIconLabelCell("", Color.TRANSPARENT, GUILD_ROW_HEIGHT_DP, isCalc = false))
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
        row.addView(makeIconLabelCell("=", COLOR_GUILD, ROW_HEIGHT_DP, isCalc = true))
        val allTotals = players.map { it.getGuildsTotal() }
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
            .setTitle("${ps.playerName} — $ICON_GUILD")
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
            .setTitle("✏️ ${ps.playerName} — $ICON_GUILD")
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
        row.addView(makeIconLabelCell(getString(R.string.sevenwonders_total), Color.TRANSPARENT, ROW_HEIGHT_DP, isCalc = true))
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
