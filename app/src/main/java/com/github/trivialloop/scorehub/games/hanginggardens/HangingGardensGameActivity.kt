package com.github.trivialloop.scorehub.games.hanginggardens

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
import com.github.trivialloop.scorehub.databinding.ActivityHanginggardensGameBinding
import com.github.trivialloop.scorehub.ui.GameResultsDialog
import com.github.trivialloop.scorehub.ui.HelpDialogs
import com.github.trivialloop.scorehub.utils.GameSymbols
import com.github.trivialloop.scorehub.utils.GameSymbols.RowStyle
import com.github.trivialloop.scorehub.utils.LocaleHelper
import com.github.trivialloop.scorehub.utils.ScoreColorRole
import kotlinx.coroutines.launch

class HangingGardensGameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHanginggardensGameBinding
    private lateinit var database: AppDatabase
    private lateinit var playerIds: LongArray
    private lateinit var playerNames: Array<String>
    private lateinit var playerColors: IntArray
    private lateinit var players: List<HangingGardensPlayerScore>

    private var gameOver = false

    companion object {
        const val GAME_TYPE = "hanginggardens"
        private const val LABEL_COL_DP = GameSymbols.LABEL_COL_DP_ONE_SHOT
        private const val ROW_HEIGHT_DP = 44
        private const val ENTRY_ROW_HEIGHT_DP = 38

        private val FLOWER_EMOJI = GameSymbols.BLOSSOM.emoji

        private val STYLE_IRRIGATION = GameSymbols.DROPLET
        private val STYLE_ANIMALS    = GameSymbols.PEACOCK
        private val STYLE_HUMANS     = GameSymbols.PERSON
        private val STYLE_OBJECTIVES = GameSymbols.CROWN

        // Flowers: the dialog title is prefixed with the matching colored square
        private val STYLE_FLOWER_BLUE   = GameSymbols.BLOSSOM.copy(color = 0xFF1565C0.toInt(), dialogEmoji = "🟦🌸")
        private val STYLE_FLOWER_RED    = GameSymbols.BLOSSOM.copy(color = 0xFFC62828.toInt(), dialogEmoji = "🟥🌸")
        private val STYLE_FLOWER_YELLOW = GameSymbols.BLOSSOM.copy(color = 0xFFF9A825.toInt(), dialogEmoji = "🟨🌸")

        // All trees (3 species + subtotal) share the same green
        private val COLOR_TREES       = GameSymbols.DECIDUOUS_TREE.color
        private val STYLE_TREE_DRAGON = GameSymbols.DECIDUOUS_TREE
        private val STYLE_TREE_CEDAR  = GameSymbols.EVERGREEN_TREE
        private val STYLE_TREE_PALM   = GameSymbols.PALM_TREE
    }

    override fun attachBaseContext(newBase: Context) {
        val language = LocaleHelper.getPersistedLocale(newBase)
        super.attachBaseContext(LocaleHelper.setLocale(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHanginggardensGameBinding.inflate(layoutInflater)
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

        database = AppDatabase.getDatabase(this)
        playerIds = intent.getLongArrayExtra("PLAYER_IDS") ?: longArrayOf()
        playerNames = intent.getStringArrayExtra("PLAYER_NAMES") ?: arrayOf()
        playerColors = intent.getIntArrayExtra("PLAYER_COLORS") ?: intArrayOf()

        players = playerIds.indices.map { i ->
            HangingGardensPlayerScore(playerIds[i], playerNames[i], playerColors[i])
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.hanginggardens_game)

        binding.btnFinishGame.setOnClickListener { confirmFinishGame() }

        buildTable()

        onBackPressedDispatcher.addCallback(this) { showQuitGameDialog() }
    }

    // ─── Table construction ────────────────────────────────────────────────────

    private fun buildTable() {
        binding.headerContainer.removeAllViews()
        binding.headerContainer.addView(buildHeaderRow())

        val c = binding.tableContainer
        c.removeAllViews()

        // Irrigation
        c.addView(buildSingleValueRow(
            STYLE_IRRIGATION, { it.irrigation }, HANGING_GARDENS_IRRIGATION_VALUES
        ) { p, v -> p.irrigation = v })

        // Flowers (one row per color)
        for (flower in HangingGardensFlower.entries) {
            c.addView(buildSingleValueRow(
                flowerStyle(flower), { it.flowers[flower] }, flower.getPossibleValues()
            ) { p, v -> p.flowers[flower] = v })
        }

        // Trees (one section per species + subtotal in its own green)
        for (tree in HangingGardensTree.entries) {
            addEntrySection(treeStyle(tree), { it.treeEntries.getValue(tree) },
                tree.getPossibleValues())
        }
        c.addView(buildSubtotalRow(COLOR_TREES) { it.getTreesTotal() })

        // Animals
        addEntrySection(STYLE_ANIMALS, { it.animalEntries }, HANGING_GARDENS_ANIMAL_VALUES)
        c.addView(buildSubtotalRow(STYLE_ANIMALS.color) { it.getAnimalsTotal() })

        // Humans
        addEntrySection(STYLE_HUMANS, { it.humanEntries }, HANGING_GARDENS_HUMAN_VALUES)
        c.addView(buildSubtotalRow(STYLE_HUMANS.color) { it.getHumansTotal() })

        // Royal objectives
        c.addView(buildSingleValueRow(
            STYLE_OBJECTIVES, { it.objectives }, HANGING_GARDENS_OBJECTIVES_VALUES
        ) { p, v -> p.objectives = v })

        c.addView(buildTotalRow())

        binding.btnFinishGame.isEnabled = !gameOver
        binding.btnFinishGame.alpha = if (gameOver) 0.5f else 1f

        binding.scrollView.post { binding.scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun addEntrySection(
        style: RowStyle,
        entries: (HangingGardensPlayerScore) -> MutableList<Int>,
        values: List<Int>
    ) {
        binding.tableContainer.addView(buildEntryHeaderRow(style, entries, values))
        val maxSlots = players.maxOf { entries(it).size }
        for (slot in 0 until maxSlots) {
            binding.tableContainer.addView(buildEntrySlotRow(style, slot, entries, values))
        }
    }

    // ─── Rows ─────────────────────────────────────────────────────────────────

    private fun buildHeaderRow(): LinearLayout {
        val row = makeRow(ROW_HEIGHT_DP)
        row.addView(makeLabelCell("", ROW_HEIGHT_DP,
            ContextCompat.getColor(this, R.color.header_cell_background)))
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

    /** One picker cell per player (irrigation, flowers, objectives). */
    private fun buildSingleValueRow(
        style: RowStyle,
        getter: (HangingGardensPlayerScore) -> Int?,
        values: List<Int>,
        setter: (HangingGardensPlayerScore, Int) -> Unit
    ): LinearLayout {
        val row = makeRow(ROW_HEIGHT_DP)
        row.addView(makeLabelCell(style.emoji, ROW_HEIGHT_DP, style.color))
        val allValues = players.map(getter)
        for (player in players) {
            val score = getter(player)
            val role = ScoreColorRole(score, allValues, higherIsBetter = true)
            val bg = if (!gameOver) R.color.cell_editable_bg else R.color.score_cell_background
            val cell = makeCell(score?.toString() ?: "", ROW_HEIGHT_DP,
                bold = role != ScoreColorRole.NEUTRAL && score != null)
            cell.background = cellDrawable(ContextCompat.getColor(this, bg))
            if (role != ScoreColorRole.NEUTRAL && score != null) cell.setTextColor(role.toColor(this))
            if (!gameOver) cell.setOnClickListener {
                showValuePicker("${player.playerName} — ${style.dialogEmoji}", values, score) { v ->
                    setter(player, v); buildTable()
                }
            }
            row.addView(cell)
        }
        return row
    }

    /** "+" header row of an entry section (trees / animals / humans). */
    private fun buildEntryHeaderRow(
        style: RowStyle,
        entries: (HangingGardensPlayerScore) -> MutableList<Int>,
        values: List<Int>
    ): LinearLayout {
        val row = makeRow(ROW_HEIGHT_DP)
        row.addView(makeLabelCell(style.emoji, ROW_HEIGHT_DP, style.color))
        for (player in players) {
            val cell = makeCell(if (gameOver) "" else "+", ROW_HEIGHT_DP, bold = true)
            cell.background = cellDrawable(ContextCompat.getColor(
                this, if (!gameOver) R.color.cell_editable_bg else R.color.header_cell_background))
            if (!gameOver) cell.setOnClickListener {
                showValuePicker("${player.playerName} — ${style.emoji} +", values, null) { v ->
                    entries(player).add(v); buildTable()
                }
            }
            row.addView(cell)
        }
        return row
    }

    private fun buildEntrySlotRow(
        style: RowStyle,
        slot: Int,
        entries: (HangingGardensPlayerScore) -> MutableList<Int>,
        values: List<Int>
    ): LinearLayout {
        val row = makeRow(ENTRY_ROW_HEIGHT_DP)
        // Same color as the section header so the section reads as one colored block
        row.addView(makeLabelCell("", ENTRY_ROW_HEIGHT_DP, style.color))
        for (player in players) {
            val value = entries(player).getOrNull(slot)
            val cell = makeCell(value?.let { "+$it" } ?: "", ENTRY_ROW_HEIGHT_DP,
                bold = value != null, textSize = 13f)
            if (value != null) cell.setTextColor(ContextCompat.getColor(this, R.color.score_text_best))
            if (!gameOver && value != null) cell.setOnClickListener {
                showEditEntryDialog(player, style.emoji, entries(player), slot, values)
            }
            row.addView(cell)
        }
        return row
    }

    /** Subtotal row: calculated-style value cells. */
    private fun buildSubtotalRow(sectionColor: Int, valueOf: (HangingGardensPlayerScore) -> Int): LinearLayout {
        val row = makeRow(ROW_HEIGHT_DP)
        val label = makeLabelCell(GameSymbols.SUBTOTAL, ROW_HEIGHT_DP, sectionColor)
        label.setTextColor(Color.WHITE)
        row.addView(label)
        for (player in players) {
            val cell = makeCell(valueOf(player).toString(), ROW_HEIGHT_DP, bold = true)
            cell.background = cellDrawable(ContextCompat.getColor(this, R.color.cell_calculated_bg))
            cell.setTextColor(ContextCompat.getColor(this, R.color.score_calculated_cell_text))
            row.addView(cell)
        }
        return row
    }

    private fun buildTotalRow(): LinearLayout {
        val row = makeRow(ROW_HEIGHT_DP)
        row.addView(makeLabelCell(GameSymbols.TOTAL, ROW_HEIGHT_DP, ContextCompat.getColor(this, R.color.cell_calculated_bg), calc = true))
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

    // ─── Dialogs ───────────────────────────────────────────────────────────────

    private fun showValuePicker(title: String, values: List<Int>, current: Int?, onPicked: (Int) -> Unit) {
        val dialogTitle = if (current != null) "✏️ $title" else title
        val dialog = AlertDialog.Builder(this)
            .setTitle(dialogTitle)
            .setItems(values.map { it.toString() }.toTypedArray()) { _, which -> onPicked(values[which]) }
            .create()
        dialog.show()
        val idx = if (current != null) values.indexOf(current) else 0
        if (idx >= 0) dialog.listView?.post { dialog.listView?.setSelection(idx) }
    }

    private fun showEditEntryDialog(
        player: HangingGardensPlayerScore, emoji: String,
        list: MutableList<Int>, slot: Int, values: List<Int>
    ) {
        val current = list.getOrNull(slot) ?: return
        AlertDialog.Builder(this)
            .setTitle("✏️ ${player.playerName} — $emoji")
            .setItems(arrayOf(
                getString(R.string.hanginggardens_edit_entry, current),
                getString(R.string.hanginggardens_delete_entry)
            )) { _, which ->
                when (which) {
                    0 -> showValuePicker("${player.playerName} — $emoji", values, current) { v ->
                        list[slot] = v; buildTable()
                    }
                    1 -> { list.removeAt(slot); buildTable() }
                }
            }
            .show()
    }

    private fun confirmFinishGame() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.hanginggardens_finish_game_title))
            .setMessage(getString(R.string.hanginggardens_finish_game_message))
            .setPositiveButton(getString(R.string.yes)) { _, _ ->
                gameOver = true
                buildTable()
                saveResultsAndShowSummary()
            }
            .setNegativeButton(getString(R.string.no), null)
            .show()
    }

    // ─── Save results ──────────────────────────────────────────────────────────

    private fun saveResultsAndShowSummary() {
        val isSoloGame = players.size == 1
        val totals = players.associate { it to it.getTotal() }
        val maxScore = totals.values.maxOrNull() ?: 0
        val winners = totals.filter { it.value == maxScore }.keys
        val isDraw = winners.size > 1
        val playedAt = System.currentTimeMillis()   // computed ONCE for all players
        lifecycleScope.launch {
            database.gameResultDao().insertGameResults(players.map { player ->
                GameResult(
                    gameType = GAME_TYPE, playerId = player.playerId,
                    playerName = player.playerName, score = player.getTotal(),
                    isWinner = if (isSoloGame) false else (!isDraw && player in winners),
                    isDraw = if (isSoloGame) false else (isDraw && player in winners),
                    playedAt = playedAt
                )
            })
            val sorted = totals.entries.sortedByDescending { it.value }
            var rank = 1
            val entries = sorted.mapIndexed { i, (p, s) ->
                val r = if (i > 0 && s == sorted[i - 1].value) rank else { rank = i + 1; rank }
                GameResultsDialog.PlayerResult(p.playerName, p.playerColor, s, r)
            }
            GameResultsDialog.show(this@HangingGardensGameActivity, entries, isDraw && !isSoloGame, " pts") { finish() }
        }
    }

    // ─── Styles & cell builders ────────────────────────────────────────────────

    private fun flowerStyle(f: HangingGardensFlower) = when (f) {
        HangingGardensFlower.BLUE   -> STYLE_FLOWER_BLUE
        HangingGardensFlower.RED    -> STYLE_FLOWER_RED
        HangingGardensFlower.YELLOW -> STYLE_FLOWER_YELLOW
    }

    private fun treeStyle(t: HangingGardensTree) = when (t) {
        HangingGardensTree.DRAGON -> STYLE_TREE_DRAGON
        HangingGardensTree.CEDAR  -> STYLE_TREE_CEDAR
        HangingGardensTree.PALM   -> STYLE_TREE_PALM
    }

    private fun makeRow(heightDp: Int): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(heightDp))
        isBaselineAligned = false   // mandatory for every hand-built grid row
    }

    /** Icon label cell: emoji (or symbol) centered on a colored background. */
    private fun makeLabelCell(text: String, heightDp: Int, bgColor: Int, calc: Boolean = false): TextView =
        TextView(this).apply {
            this.text = text
            gravity = Gravity.CENTER
            textSize = if (calc) 16f else 22f
            setTypeface(null, Typeface.BOLD)
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(dpToPx(LABEL_COL_DP), dpToPx(heightDp))
            background = cellDrawable(bgColor)
            setTextColor(ContextCompat.getColor(
                this@HangingGardensGameActivity,
                if (calc) R.color.score_calculated_cell_text else R.color.header_cell_text))
        }

    private fun makeCell(text: String, heightDp: Int, bold: Boolean, textSize: Float = 14f): TextView =
        TextView(this).apply {
            this.text = text
            gravity = Gravity.CENTER
            this.textSize = textSize
            if (bold) setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(heightDp), 1f)
            background = cellDrawable(ContextCompat.getColor(this@HangingGardensGameActivity, R.color.score_cell_background))
            setTextColor(ContextCompat.getColor(this@HangingGardensGameActivity, R.color.score_cell_text))
        }

    private fun cellDrawable(bgColor: Int): GradientDrawable = GradientDrawable().apply {
        setColor(bgColor)
        setStroke(1, ContextCompat.getColor(this@HangingGardensGameActivity, R.color.cell_border))
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private fun showQuitGameDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.hanginggardens_quit_game)
            .setMessage(R.string.hanginggardens_quit_game_message)
            .setPositiveButton(R.string.yes) { _, _ -> finish() }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_hanginggardens_game, menu); return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> { showQuitGameDialog(); true }
        R.id.action_help -> { HelpDialogs.showAppHelp(this, GAME_TYPE); true }
        else -> super.onOptionsItemSelected(item)
    }
}
