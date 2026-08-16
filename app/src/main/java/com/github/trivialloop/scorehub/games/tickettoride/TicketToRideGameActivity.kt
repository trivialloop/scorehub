package com.github.trivialloop.scorehub.games.tickettoride

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
import com.github.trivialloop.scorehub.databinding.ActivityTickettorideGameBinding
import com.github.trivialloop.scorehub.ui.GameResultsDialog
import com.github.trivialloop.scorehub.ui.HelpDialogs
import com.github.trivialloop.scorehub.utils.LocaleHelper
import com.github.trivialloop.scorehub.utils.ScoreColorRole
import kotlinx.coroutines.launch

class TicketToRideGameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTickettorideGameBinding
    private lateinit var database: AppDatabase
    private lateinit var playerIds: LongArray
    private lateinit var playerNames: Array<String>
    private lateinit var playerColors: IntArray
    private lateinit var players: List<TicketToRidePlayerScore>

    private var gameOver = false

    companion object {
        const val GAME_TYPE = "ticket_to_ride"
        private const val LABEL_COL_DP = 65
        private const val ROW_HEIGHT_DP = 44
        private const val TICKET_ROW_HEIGHT_DP = 38
    }

    override fun attachBaseContext(newBase: Context) {
        val language = LocaleHelper.getPersistedLocale(newBase)
        super.attachBaseContext(LocaleHelper.setLocale(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTickettorideGameBinding.inflate(layoutInflater)
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
            TicketToRidePlayerScore(playerIds[i], playerNames[i], playerColors[i])
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.tickettoride_game)

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

        for (length in TicketToRidePlayerScore.ROUTE_LENGTHS) {
            binding.tableContainer.addView(buildRouteRow(length))
        }
        binding.tableContainer.addView(buildSubtotalRow(
            getString(R.string.tickettoride_route_points)
        ) { it.getRoutePoints() })

        binding.tableContainer.addView(buildTicketSectionHeaderRow(
            getString(R.string.tickettoride_completed_tickets), isFailed = false
        ))
        val maxCompleted = players.maxOf { it.completedTickets.size }
        for (slot in 0 until maxCompleted) {
            binding.tableContainer.addView(buildTicketSlotRow(slot, isFailed = false))
        }
        binding.tableContainer.addView(buildSubtotalRow(
            getString(R.string.tickettoride_subtotal)
        ) { it.getCompletedTicketsPoints() })

        binding.tableContainer.addView(buildTicketSectionHeaderRow(
            getString(R.string.tickettoride_failed_tickets), isFailed = true
        ))
        val maxFailed = players.maxOf { it.failedTickets.size }
        for (slot in 0 until maxFailed) {
            binding.tableContainer.addView(buildTicketSlotRow(slot, isFailed = true))
        }
        binding.tableContainer.addView(buildSubtotalRow(
            getString(R.string.tickettoride_subtotal)
        ) { -it.getFailedTicketsPoints() })

        binding.tableContainer.addView(buildLongestPathRow())
        binding.tableContainer.addView(buildTotalRow())

        binding.btnFinishGame.isEnabled = !gameOver
        binding.btnFinishGame.alpha = if (gameOver) 0.5f else 1f

        binding.scrollView.post { binding.scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    // ─── Rows ─────────────────────────────────────────────────────────────────

    private fun buildHeaderRow(): LinearLayout {
        val row = makeRow(ROW_HEIGHT_DP)
        row.addView(makeLabelCell("", ROW_HEIGHT_DP, isCalc = false))
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

    private fun buildRouteRow(length: Int): LinearLayout {
        val row = makeRow(ROW_HEIGHT_DP)
        row.addView(makeLabelCell(
            getString(R.string.tickettoride_route_length_label, length), ROW_HEIGHT_DP, isCalc = false
        ))
        for (player in players) {
            val count = player.routeCounts[length] ?: 0
            val bgColor = if (!gameOver) R.color.cell_editable_bg else R.color.score_cell_background
            val cell = makeCell(count.toString(), ROW_HEIGHT_DP, bold = count > 0)
            cell.background = cellDrawable(ContextCompat.getColor(this, bgColor))
            if (!gameOver) cell.setOnClickListener { showRouteCountPicker(player, length) }
            row.addView(cell)
        }
        return row
    }

    private fun buildSubtotalRow(label: String, valueOf: (TicketToRidePlayerScore) -> Int): LinearLayout {
        val row = makeRow(ROW_HEIGHT_DP)
        row.addView(makeLabelCell(label, ROW_HEIGHT_DP, isCalc = true))
        for (player in players) {
            val value = valueOf(player)
            val cell = makeCell(if (value >= 0) value.toString() else value.toString(), ROW_HEIGHT_DP, bold = true)
            cell.background = cellDrawable(ContextCompat.getColor(this, R.color.cell_calculated_bg))
            cell.setTextColor(ContextCompat.getColor(this, R.color.score_calculated_cell_text))
            row.addView(cell)
        }
        return row
    }

    private fun buildTicketSectionHeaderRow(title: String, isFailed: Boolean): LinearLayout {
        val row = makeRow(ROW_HEIGHT_DP)
        row.addView(makeLabelCell(title, ROW_HEIGHT_DP, isCalc = false))
        for (player in players) {
            val cell = makeCell(if (gameOver) "" else "+", ROW_HEIGHT_DP, bold = true)
            cell.background = cellDrawable(
                ContextCompat.getColor(this, if (!gameOver) R.color.cell_editable_bg else R.color.header_cell_background)
            )
            if (!gameOver) cell.setOnClickListener { showAddTicketDialog(player, isFailed) }
            row.addView(cell)
        }
        return row
    }

    private fun buildTicketSlotRow(slot: Int, isFailed: Boolean): LinearLayout {
        val row = makeRow(TICKET_ROW_HEIGHT_DP)
        row.addView(makeLabelCell("", TICKET_ROW_HEIGHT_DP, isCalc = false))
        for (player in players) {
            val list = if (isFailed) player.failedTickets else player.completedTickets
            val value = list.getOrNull(slot)
            val text = value?.let { if (isFailed) "−$it" else "+$it" } ?: ""
            val textColor = when {
                value == null -> ContextCompat.getColor(this, R.color.score_cell_text)
                isFailed -> ContextCompat.getColor(this, R.color.score_text_worst)
                else -> ContextCompat.getColor(this, R.color.score_text_best)
            }
            val cell = makeCell(text, TICKET_ROW_HEIGHT_DP, bold = value != null, textSize = 13f)
            cell.setTextColor(textColor)
            cell.background = cellDrawable(ContextCompat.getColor(this, R.color.score_cell_background))
            if (!gameOver && value != null) {
                cell.setOnClickListener { showEditTicketDialog(player, isFailed, slot) }
            }
            row.addView(cell)
        }
        return row
    }

    private fun buildLongestPathRow(): LinearLayout {
        val row = makeRow(ROW_HEIGHT_DP)
        row.addView(makeLabelCell(getString(R.string.tickettoride_longest_path), ROW_HEIGHT_DP, isCalc = false))
        for (player in players) {
            val text = if (player.hasLongestPath) "🏆 +${TicketToRidePlayerScore.LONGEST_PATH_BONUS}" else ""
            val cell = makeCell(text, ROW_HEIGHT_DP, bold = player.hasLongestPath)
            if (player.hasLongestPath) {
                cell.setTextColor(ContextCompat.getColor(this, R.color.score_text_best))
            }
            cell.background = cellDrawable(
                ContextCompat.getColor(
                    this,
                    if (!gameOver) R.color.cell_editable_bg else R.color.score_cell_background
                )
            )
            if (!gameOver) cell.setOnClickListener {
                player.hasLongestPath = !player.hasLongestPath
                buildTable()
            }
            row.addView(cell)
        }
        return row
    }

    private fun buildTotalRow(): LinearLayout {
        val row = makeRow(ROW_HEIGHT_DP)
        row.addView(makeLabelCell(getString(R.string.tickettoride_total), ROW_HEIGHT_DP, isCalc = true))
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

    private fun showRouteCountPicker(player: TicketToRidePlayerScore, length: Int) {
        val current = player.routeCounts[length] ?: 0
        val maxCount = TicketToRidePlayerScore.MAX_ROUTE_COUNT[length] ?: 20
        val values = (0..maxCount).toList()
        val items = values.map { it.toString() }.toTypedArray()
        val title = "${player.playerName} — ${getString(R.string.tickettoride_route_length_label, length)}"

        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(items) { _, which ->
                player.routeCounts[length] = values[which]
                buildTable()
            }
            .create()
        dialog.show()
        dialog.listView?.post { dialog.listView?.setSelection(current) }
    }

    private fun showAddTicketDialog(player: TicketToRidePlayerScore, isFailed: Boolean) {
        val values = TicketToRidePlayerScore.TICKET_VALUES
        val items = values.map { it.toString() }.toTypedArray()
        val title = "${player.playerName} — ${getString(R.string.tickettoride_add_ticket_title)}"

        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(items) { _, which ->
                if (isFailed) player.failedTickets.add(values[which])
                else player.completedTickets.add(values[which])
                buildTable()
            }
            .show()
    }

    private fun showEditTicketDialog(player: TicketToRidePlayerScore, isFailed: Boolean, slot: Int) {
        val list = if (isFailed) player.failedTickets else player.completedTickets
        val current = list.getOrNull(slot) ?: return

        AlertDialog.Builder(this)
            .setTitle("✏️ ${player.playerName}")
            .setItems(arrayOf(
                getString(R.string.tickettoride_edit_entry, current),
                getString(R.string.tickettoride_delete_entry)
            )) { _, which ->
                when (which) {
                    0 -> showTicketValuePicker(player, isFailed, slot, current)
                    1 -> { list.removeAt(slot); buildTable() }
                }
            }
            .show()
    }

    private fun showTicketValuePicker(player: TicketToRidePlayerScore, isFailed: Boolean, slot: Int, current: Int) {
        val values = TicketToRidePlayerScore.TICKET_VALUES
        val items = values.map { it.toString() }.toTypedArray()
        val list = if (isFailed) player.failedTickets else player.completedTickets

        val dialog = AlertDialog.Builder(this)
            .setTitle("✏️ ${player.playerName} — ${getString(R.string.tickettoride_add_ticket_title)}")
            .setItems(items) { _, which ->
                list[slot] = values[which]
                buildTable()
            }
            .create()
        dialog.show()
        val idx = values.indexOf(current)
        if (idx >= 0) dialog.listView?.post { dialog.listView?.setSelection(idx) }
    }

    private fun confirmFinishGame() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.tickettoride_finish_game_title))
            .setMessage(getString(R.string.tickettoride_finish_game_message))
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
        val totals   = players.associate { it to it.getTotal() }
        val maxScore = totals.values.maxOrNull() ?: 0
        val winners  = totals.filter { it.value == maxScore }.keys
        val isDraw   = winners.size > 1
        lifecycleScope.launch {
            database.gameResultDao().insertGameResults(players.map { player ->
                GameResult(
                    gameType   = GAME_TYPE, playerId = player.playerId,
                    playerName = player.playerName, score = player.getTotal(),
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
            GameResultsDialog.show(this@TicketToRideGameActivity, entries, isDraw, " pts") { finish() }
        }
    }

    // ─── Cell builders ─────────────────────────────────────────────────────────

    private fun makeRow(heightDp: Int): LinearLayout = LinearLayout(this).apply {
        orientation  = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(heightDp))
        // Without this, LinearLayout tries to align all children on the text baseline of
        // the tallest/most-prominent child. Rows that mix empty cells (e.g. no ticket yet,
        // no longest-path bonus) with filled/bold ones then render each cell at a slightly
        // different vertical offset, producing a visible row-to-row misalignment even
        // though every cell shares the same fixed height.
        isBaselineAligned = false
    }

    private fun makeLabelCell(text: String, heightDp: Int, isCalc: Boolean): TextView = TextView(this).apply {
        this.text = text; gravity = Gravity.CENTER; textSize = 9f; setTypeface(null, Typeface.BOLD)
        maxLines = 3
        setPadding(dpToPx(2), 0, dpToPx(2), 0)
        layoutParams = LinearLayout.LayoutParams(dpToPx(LABEL_COL_DP), dpToPx(heightDp))
        val bg = if (isCalc) R.color.cell_calculated_bg else R.color.header_cell_background
        val fg = if (isCalc) R.color.score_calculated_cell_text else R.color.header_cell_text
        background = cellDrawable(ContextCompat.getColor(this@TicketToRideGameActivity, bg))
        setTextColor(ContextCompat.getColor(this@TicketToRideGameActivity, fg))
    }

    private fun makeCell(text: String, heightDp: Int, bold: Boolean, textSize: Float = 14f): TextView =
        TextView(this).apply {
            this.text = text; gravity = Gravity.CENTER; this.textSize = textSize
            if (bold) setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(heightDp), 1f)
            background = cellDrawable(ContextCompat.getColor(this@TicketToRideGameActivity, R.color.score_cell_background))
            setTextColor(ContextCompat.getColor(this@TicketToRideGameActivity, R.color.score_cell_text))
        }

    private fun cellDrawable(bgColor: Int): GradientDrawable = GradientDrawable().apply {
        setColor(bgColor)
        setStroke(1, ContextCompat.getColor(this@TicketToRideGameActivity, R.color.cell_border))
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private fun showQuitGameDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.tickettoride_quit_game)
            .setMessage(R.string.tickettoride_quit_game_message)
            .setPositiveButton(R.string.yes) { _, _ -> finish() }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_tickettoride_game, menu)
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
