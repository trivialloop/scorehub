package com.github.trivialloop.scorehub.games.tarot

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.MenuItem
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.github.trivialloop.scorehub.R
import com.github.trivialloop.scorehub.data.AppDatabase
import com.github.trivialloop.scorehub.data.GameResult
import com.github.trivialloop.scorehub.databinding.ActivityTarotGameBinding
import com.github.trivialloop.scorehub.ui.GameResultsDialog
import com.github.trivialloop.scorehub.utils.LocaleHelper
import kotlinx.coroutines.launch

class TarotGameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTarotGameBinding
    private lateinit var database: AppDatabase

    private lateinit var playerIds: LongArray
    private lateinit var playerNames: Array<String>
    private lateinit var playerColors: IntArray
    private lateinit var players: List<TarotPlayerState>

    private val rounds = mutableListOf<TarotRound>()
    private var gameOver = false

    companion object {
        const val GAME_TYPE = "tarot"
        private const val SCORE_LIMIT = 1000
    }

    // ─── Adaptive sizing ───────────────────────────────────────────────────────

    private val cellTextSize: Float
        get() = when {
            players.size <= 3 -> 13f
            players.size <= 4 -> 12f
            else -> 11f
        }

    private val headerRowHeight: Int
        get() = dpToPx(when {
            players.size <= 3 -> 36
            players.size <= 4 -> 32
            else -> 28
        })

    private val scoreRowHeight: Int get() = headerRowHeight * 2

    // ─── Lifecycle ─────────────────────────────────────────────────────────────

    override fun attachBaseContext(newBase: Context) {
        val language = LocaleHelper.getPersistedLocale(newBase)
        super.attachBaseContext(LocaleHelper.setLocale(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTarotGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = AppDatabase.getDatabase(this)
        playerIds = intent.getLongArrayExtra("PLAYER_IDS") ?: longArrayOf()
        playerNames = intent.getStringArrayExtra("PLAYER_NAMES") ?: arrayOf()
        playerColors = intent.getIntArrayExtra("PLAYER_COLORS") ?: intArrayOf()

        players = playerIds.indices.map { i ->
            TarotPlayerState(playerIds[i], playerNames[i], playerColors[i])
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.tarot_game)

        buildTable()
    }

    // ─── Table ────────────────────────────────────────────────────────────────

    private fun buildTable() {
        binding.tableContainer.removeAllViews()
        binding.tableContainer.addView(buildHeaderRow())
        rounds.forEach { binding.tableContainer.addView(buildRoundRow(it)) }
        if (!gameOver) binding.tableContainer.addView(buildAddRoundRow())
        binding.tableContainer.addView(buildTotalRow())
        binding.scrollView.post { binding.scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun buildHeaderRow(): LinearLayout {
        val row = makeRow(headerRowHeight)
        row.addView(makeRoundLabelCell("#", headerRowHeight))
        for (player in players) {
            val cell = makeSingleLineCell(player.playerName, bold = true, height = headerRowHeight)
            cell.background = cellDrawable(player.playerColor)
            cell.setTextColor(Color.WHITE)
            row.addView(cell)
        }
        return row
    }

    private fun buildRoundRow(round: TarotRound): LinearLayout {
        val playerIdList = players.map { it.playerId }
        val scores = round.computeScores(playerIdList)
        val row = makeRow(scoreRowHeight)

        val declarer = players.find { it.playerId == round.declarerId }
        val labelCell = makeRoundLabelCell(round.roundNumber.toString(), scoreRowHeight)
        if (declarer != null) {
            labelCell.background = cellDrawable(declarer.playerColor)
            labelCell.setTextColor(Color.WHITE)
        }
        if (!gameOver) {
            labelCell.setOnClickListener { showPage1Dialog(existingRound = round) }
        }
        row.addView(labelCell)

        for (player in players) {
            val score = scores[player.playerId] ?: 0
            val role = round.getCellRole(player.playerId, playerIdList)
            val scoreColor = when (role) {
                TarotCellRole.DECLARER_WIN, TarotCellRole.PARTNER_WIN,
                TarotCellRole.DEFENDER_WIN ->
                    ContextCompat.getColor(this, R.color.tarot_score_win)
                TarotCellRole.DECLARER_LOSS, TarotCellRole.PARTNER_LOSS,
                TarotCellRole.DEFENDER_LOSS ->
                    ContextCompat.getColor(this, R.color.tarot_score_loss)
            }
            row.addView(makeTwoLineCell(
                line1 = if (score >= 0) "+$score" else "$score",
                line1Color = scoreColor,
                line2 = buildSymbolLine(round, player.playerId, playerIdList),
                height = scoreRowHeight
            ))
        }
        return row
    }

    private fun buildSymbolLine(
        round: TarotRound, playerId: Long, playerIdList: List<Long>
    ): String {
        val parts = mutableListOf<String>()
        val isSolo5 = playerIdList.size == 5 &&
                (round.associatedPlayerId == null || round.associatedPlayerId == round.declarerId)
        val isDeclarerTeam = playerId == round.declarerId ||
                (playerIdList.size == 5 && !isSolo5 && playerId == round.associatedPlayerId)

        if (playerId == round.declarerId) {
            parts.add(round.contract.symbol())
            parts.add(boutsSymbol(round.boutsCount))
        }

        // Partner badge — heart
        if (playerIdList.size == 5 && !isSolo5
            && playerId == round.associatedPlayerId
            && playerId != round.declarerId) {
            parts.add("❤️")
        }

        if (isDeclarerTeam && round.poignees.declarerPoignee != TarotPoigneeLevel.NONE)
            parts.add(round.poignees.declarerPoignee.symbol())
        if (!isDeclarerTeam && round.poignees.defensePoignee != TarotPoigneeLevel.NONE)
            parts.add(round.poignees.defensePoignee.symbol())

        val petitIsDeclarerTeam = round.petitAuBout == TarotPetitAuBout.DECLARER
        val petitIsDefense = round.petitAuBout == TarotPetitAuBout.DEFENSE
        if ((isDeclarerTeam && petitIsDeclarerTeam) || (!isDeclarerTeam && petitIsDefense))
            parts.add(PETIT_AU_BOUT_SYMBOL)

        if (playerId == round.declarerId && round.chelem != TarotChelem.NONE)
            parts.add(round.chelem.symbol())

        return parts.joinToString(" ")
    }

    private fun buildAddRoundRow(): LinearLayout {
        val nextNum = rounds.size + 1
        val row = makeRow(scoreRowHeight)
        val labelCell = makeRoundLabelCell(nextNum.toString(), scoreRowHeight)
        labelCell.alpha = 0.4f
        labelCell.setOnClickListener { showPage1Dialog(existingRound = null) }
        row.addView(labelCell)
        for (player in players) {
            val cell = makeSingleLineCell("", height = scoreRowHeight)
            cell.setOnClickListener { showPage1Dialog(existingRound = null) }
            row.addView(cell)
        }
        return row
    }

    private fun buildTotalRow(): LinearLayout {
        val row = makeRow(headerRowHeight)
        row.addView(makeRoundLabelCell(getString(R.string.tarot_total), headerRowHeight))
        val playerIdList = players.map { it.playerId }
        val totals = players.associate { it.playerId to it.getTotal(rounds, playerIdList) }
        val maxTotal = if (gameOver) totals.values.maxOrNull() else null
        for (player in players) {
            val total = totals[player.playerId] ?: 0
            val cell = makeSingleLineCell(total.toString(), bold = true, height = headerRowHeight)
            if (gameOver && total == maxTotal)
                cell.setTextColor(ContextCompat.getColor(this, R.color.tarot_score_win))
            row.addView(cell)
        }
        return row
    }

    // ─── Dialog Page 1: declarer / contract / bouts / options toggle ───────────

    private fun showPage1Dialog(existingRound: TarotRound?) {
        val view = layoutInflater.inflate(R.layout.dialog_tarot_round, null)

        val spinnerDeclarer = view.findViewById<Spinner>(R.id.spinnerDeclarer)
        val names = players.map { it.playerName }
        spinnerDeclarer.adapter = ArrayAdapter(this,
            android.R.layout.simple_spinner_dropdown_item, names)
        existingRound?.let { r ->
            val idx = players.indexOfFirst { it.playerId == r.declarerId }
            if (idx >= 0) spinnerDeclarer.setSelection(idx)
        }

        val rgContract = view.findViewById<RadioGroup>(R.id.rgContract)
        val contractRadioIds = listOf(
            R.id.rbPrise, R.id.rbGarde, R.id.rbGardeSans, R.id.rbGardeContre)
        val contractValues = TarotContract.values()
        val defaultContract = existingRound?.contract ?: TarotContract.PRISE
        rgContract.check(contractRadioIds[contractValues.indexOf(defaultContract)])

        val rgBouts = view.findViewById<RadioGroup>(R.id.rgBouts)
        val boutsRadioIds = listOf(R.id.rbBouts0, R.id.rbBouts1, R.id.rbBouts2, R.id.rbBouts3)
        rgBouts.check(boutsRadioIds[existingRound?.boutsCount ?: 2])

        val rowPartner = view.findViewById<LinearLayout>(R.id.rowPartner)
        val spinnerPartner = view.findViewById<Spinner>(R.id.spinnerPartner)
        if (players.size == 5) {
            rowPartner.visibility = android.view.View.VISIBLE
            // Simply list all player names with no "(Solo)" annotation
            spinnerPartner.adapter = ArrayAdapter(this,
                android.R.layout.simple_spinner_dropdown_item, names)
            existingRound?.associatedPlayerId?.let { pid ->
                val idx = players.indexOfFirst { it.playerId == pid }
                if (idx >= 0) spinnerPartner.setSelection(idx)
            } ?: run {
                // Default partner = same as declarer (solo), shown as just their name
                spinnerPartner.setSelection(spinnerDeclarer.selectedItemPosition)
            }
            // Keep partner spinner in sync with declarer default when declarer changes
            spinnerDeclarer.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) {
                    if (existingRound == null) spinnerPartner.setSelection(pos)
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        } else {
            rowPartner.visibility = android.view.View.GONE
        }

        val cbHasOptions = view.findViewById<CheckBox>(R.id.cbHasOptions)
        cbHasOptions.isChecked = existingRound?.let {
            it.poignees.declarerPoignee != TarotPoigneeLevel.NONE ||
            it.poignees.defensePoignee != TarotPoigneeLevel.NONE ||
            it.petitAuBout != TarotPetitAuBout.NONE ||
            it.chelem != TarotChelem.NONE
        } ?: false

        val builder = AlertDialog.Builder(this)
            .setTitle(getString(R.string.tarot_round_title,
                existingRound?.roundNumber ?: rounds.size + 1))
            .setView(view)
            .setPositiveButton(getString(R.string.tarot_next), null) // set below to avoid auto-dismiss
            .setNegativeButton(getString(R.string.cancel), null)

        if (existingRound != null) {
            builder.setNeutralButton(getString(R.string.tarot_delete_round)) { _, _ ->
                deleteRound(existingRound)
            }
        }

        val dialog = builder.create()
        dialog.show()

        // Override positive button to avoid auto-dismiss on validation error
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val declarerIdx = spinnerDeclarer.selectedItemPosition
            val declarerId = players[declarerIdx].playerId
            val contract = contractValues[contractRadioIds.indexOf(rgContract.checkedRadioButtonId)]
            val bouts = boutsRadioIds.indexOf(rgBouts.checkedRadioButtonId)
            val partnerId = if (players.size == 5)
                players[spinnerPartner.selectedItemPosition].playerId else null

            dialog.dismiss()

            if (cbHasOptions.isChecked) {
                showPage2Dialog(declarerId, contract, bouts, partnerId, existingRound)
            } else {
                showPointsDialog(declarerId, contract, bouts, partnerId,
                    TarotPoigneeOptions(), TarotPetitAuBout.NONE, TarotChelem.NONE,
                    existingRound, fromOptions = false)
            }
        }
    }

    // ─── Dialog Page 2: options table ─────────────────────────────────────────

    private fun showPage2Dialog(
        declarerId: Long,
        contract: TarotContract,
        bouts: Int,
        partnerId: Long?,
        existingRound: TarotRound?,
        // Pre-filled values when coming back from page 3
        prePoignees: TarotPoigneeOptions = TarotPoigneeOptions(),
        prePetit: TarotPetitAuBout = TarotPetitAuBout.NONE,
        preChelem: TarotChelem = TarotChelem.NONE
    ) {
        val view = layoutInflater.inflate(R.layout.dialog_tarot_round_options, null)

        // ── Poignée setup ──────────────────────────────────────────────────────
        val rgPoigneeDeclarer = view.findViewById<RadioGroup>(R.id.rgPoigneeDeclarer)
        val rgPoigneeDefense = view.findViewById<RadioGroup>(R.id.rgPoigneeDefense)

        val decIds = listOf(R.id.rbPDNone, R.id.rbPDSimple, R.id.rbPDDouble, R.id.rbPDTriple)
        val defIds = listOf(R.id.rbPFNone, R.id.rbPFSimple, R.id.rbPFDouble, R.id.rbPFTriple)
        val levels = TarotPoigneeLevel.values()

        val initDec = existingRound?.poignees?.declarerPoignee ?: prePoignees.declarerPoignee
        val initDef = existingRound?.poignees?.defensePoignee ?: prePoignees.defensePoignee
        rgPoigneeDeclarer.check(decIds[levels.indexOf(initDec)])
        rgPoigneeDefense.check(defIds[levels.indexOf(initDef)])

        /**
         * Mutual exclusion rules (the last click always wins):
         * - If user clicks DOUBLE or TRIPLE on one side → force other side to NONE.
         * - If user clicks SIMPLE on one side AND other side is DOUBLE or TRIPLE → force other to NONE.
         * - If user clicks NONE on one side → do nothing to the other side.
         *
         * Disable the listener temporarily while we programmatically change the other group
         * to avoid infinite loops.
         */
        var updatingDeclarer = false
        var updatingDefense = false

        rgPoigneeDeclarer.setOnCheckedChangeListener { _, checkedId ->
            if (updatingDeclarer) return@setOnCheckedChangeListener
            val idx = decIds.indexOf(checkedId)
            if (idx < 0) return@setOnCheckedChangeListener
            val selectedLevel = levels[idx]
            val otherIdx = defIds.indexOf(rgPoigneeDefense.checkedRadioButtonId)
            val otherLevel = if (otherIdx >= 0) levels[otherIdx] else TarotPoigneeLevel.NONE

            when (selectedLevel) {
                TarotPoigneeLevel.DOUBLE, TarotPoigneeLevel.TRIPLE -> {
                    // Force defense to NONE
                    updatingDefense = true
                    rgPoigneeDefense.check(defIds[0])
                    updatingDefense = false
                }
                TarotPoigneeLevel.SIMPLE -> {
                    // Only reset defense if it's double or triple
                    if (otherLevel == TarotPoigneeLevel.DOUBLE || otherLevel == TarotPoigneeLevel.TRIPLE) {
                        updatingDefense = true
                        rgPoigneeDefense.check(defIds[0])
                        updatingDefense = false
                    }
                }
                TarotPoigneeLevel.NONE -> { /* no cross-effect */ }
            }
        }

        rgPoigneeDefense.setOnCheckedChangeListener { _, checkedId ->
            if (updatingDefense) return@setOnCheckedChangeListener
            val idx = defIds.indexOf(checkedId)
            if (idx < 0) return@setOnCheckedChangeListener
            val selectedLevel = levels[idx]
            val otherIdx = decIds.indexOf(rgPoigneeDeclarer.checkedRadioButtonId)
            val otherLevel = if (otherIdx >= 0) levels[otherIdx] else TarotPoigneeLevel.NONE

            when (selectedLevel) {
                TarotPoigneeLevel.DOUBLE, TarotPoigneeLevel.TRIPLE -> {
                    updatingDeclarer = true
                    rgPoigneeDeclarer.check(decIds[0])
                    updatingDeclarer = false
                }
                TarotPoigneeLevel.SIMPLE -> {
                    if (otherLevel == TarotPoigneeLevel.DOUBLE || otherLevel == TarotPoigneeLevel.TRIPLE) {
                        updatingDeclarer = true
                        rgPoigneeDeclarer.check(decIds[0])
                        updatingDeclarer = false
                    }
                }
                TarotPoigneeLevel.NONE -> { /* no cross-effect */ }
            }
        }

        // ── Petit au Bout ──────────────────────────────────────────────────────
        val rgPetit = view.findViewById<RadioGroup>(R.id.rgPetitAuBout)
        val petitIds = listOf(R.id.rbPetitNone, R.id.rbPetitDeclarer, R.id.rbPetitDefense)
        val petitValues = TarotPetitAuBout.values()
        val initPetit = existingRound?.petitAuBout ?: prePetit
        rgPetit.check(petitIds[petitValues.indexOf(initPetit)])

        // ── Chelem ────────────────────────────────────────────────────────────
        val rgChelem = view.findViewById<RadioGroup>(R.id.rgChelem)
        val chelemIds = listOf(R.id.rbChelemNone, R.id.rbChelemAnnouncedSuccess,
            R.id.rbChelemUnannouncedSuccess, R.id.rbChelemAnnouncedFailure)
        val chelemValues = TarotChelem.values()
        val initChelem = existingRound?.chelem ?: preChelem
        rgChelem.check(chelemIds[chelemValues.indexOf(initChelem)])

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.tarot_options_title))
            .setView(view)
            .setPositiveButton(getString(R.string.tarot_next)) { _, _ ->
                val decIdx = decIds.indexOf(rgPoigneeDeclarer.checkedRadioButtonId)
                val defIdx = defIds.indexOf(rgPoigneeDefense.checkedRadioButtonId)
                val poignees = TarotPoigneeOptions(
                    declarerPoignee = if (decIdx >= 0) levels[decIdx] else TarotPoigneeLevel.NONE,
                    defensePoignee = if (defIdx >= 0) levels[defIdx] else TarotPoigneeLevel.NONE
                )
                val pIdx = petitIds.indexOf(rgPetit.checkedRadioButtonId)
                val petit = if (pIdx >= 0) petitValues[pIdx] else TarotPetitAuBout.NONE
                val cIdx = chelemIds.indexOf(rgChelem.checkedRadioButtonId)
                val chelem = if (cIdx >= 0) chelemValues[cIdx] else TarotChelem.NONE

                showPointsDialog(declarerId, contract, bouts, partnerId,
                    poignees, petit, chelem, existingRound, fromOptions = true)
            }
            // "Précédent" reopens page 1 with current selections preserved
            .setNegativeButton(getString(R.string.tarot_back)) { _, _ ->
                showPage1Dialog(existingRound)
            }
            .show()
    }

    // ─── Dialog Page 3: points made ───────────────────────────────────────────

    private fun showPointsDialog(
        declarerId: Long,
        contract: TarotContract,
        bouts: Int,
        partnerId: Long?,
        poignees: TarotPoigneeOptions,
        petitAuBout: TarotPetitAuBout,
        chelem: TarotChelem,
        existingRound: TarotRound?,
        fromOptions: Boolean
    ) {
        val threshold = TarotRound.threshold(bouts)
        val editText = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.tarot_points_hint, threshold)
            gravity = Gravity.CENTER
            textSize = 20f
            filters = arrayOf(android.text.InputFilter.LengthFilter(2))
            existingRound?.let { setText(it.pointsMade.toString()) }
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(24), dpToPx(8), dpToPx(24), dpToPx(8))
            addView(editText)
            addView(TextView(this@TarotGameActivity).apply {
                text = getString(R.string.tarot_threshold_info, threshold)
                gravity = Gravity.CENTER
                textSize = 13f
                setTextColor(ContextCompat.getColor(this@TarotGameActivity,
                    android.R.color.darker_gray))
            })
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.tarot_points_made))
            .setView(container)
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                val pts = editText.text.toString().trim().toIntOrNull()
                if (pts == null || pts < 0 || pts > 91) {
                    showPointsDialog(declarerId, contract, bouts, partnerId,
                        poignees, petitAuBout, chelem, existingRound, fromOptions)
                    return@setPositiveButton
                }
                val newRound = TarotRound(
                    roundNumber = existingRound?.roundNumber ?: rounds.size + 1,
                    declarerId = declarerId,
                    contract = contract,
                    boutsCount = bouts,
                    pointsMade = pts,
                    poignees = poignees,
                    petitAuBout = petitAuBout,
                    chelem = chelem,
                    associatedPlayerId = partnerId
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
            .setNegativeButton(getString(R.string.tarot_back)) { _, _ ->
                // Go back to page 2 if coming from options, else page 1
                if (fromOptions) {
                    showPage2Dialog(declarerId, contract, bouts, partnerId, existingRound,
                        poignees, petitAuBout, chelem)
                } else {
                    showPage1Dialog(existingRound)
                }
            }
            .show()
        editText.requestFocus()
    }

    private fun deleteRound(round: TarotRound) {
        rounds.remove(round)
        rounds.forEachIndexed { idx, r -> rounds[idx] = r.copy(roundNumber = idx + 1) }
        buildTable()
    }

    // ─── Game logic ────────────────────────────────────────────────────────────

    private fun checkEndOfGame() {
        if (gameOver) return
        val playerIdList = players.map { it.playerId }
        val maxTotal = players.maxOf { it.getTotal(rounds, playerIdList) }
        if (maxTotal >= SCORE_LIMIT) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.tarot_game_over_title))
                .setMessage(getString(R.string.tarot_game_over_confirm))
                .setPositiveButton(getString(R.string.yes)) { _, _ ->
                    gameOver = true
                    buildTable()
                    saveResultsAndShowSummary()
                }
                .setNegativeButton(getString(R.string.no), null)
                .show()
        }
    }

    private fun saveResultsAndShowSummary() {
        val playerIdList = players.map { it.playerId }
        val totals = players.associate { it to it.getTotal(rounds, playerIdList) }
        val maxScore = totals.values.maxOrNull() ?: 0
        val winners = totals.filter { it.value == maxScore }.keys
        val isDraw = winners.size > 1
        lifecycleScope.launch {
            database.gameResultDao().insertGameResults(players.map { player ->
                GameResult(
                    gameType = GAME_TYPE, playerId = player.playerId,
                    playerName = player.playerName,
                    score = player.getTotal(rounds, playerIdList),
                    isWinner = !isDraw && player in winners,
                    isDraw = isDraw && player in winners
                )
            })
            val sorted = totals.entries.sortedByDescending { it.value }
            var rank = 1
            val entries = sorted.mapIndexed { i, (p, s) ->
                val r = if (i > 0 && s == sorted[i - 1].value) rank
                    else { rank = i + 1; rank }
                GameResultsDialog.PlayerResult(p.playerName, p.playerColor, s, r)
            }
            GameResultsDialog.show(this@TarotGameActivity, entries, isDraw, " pts") { finish() }
        }
    }

    // ─── Cell builders ─────────────────────────────────────────────────────────

    private fun makeRow(height: Int): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, height)
    }

    private fun makeRoundLabelCell(text: String, height: Int): TextView = TextView(this).apply {
        this.text = text
        gravity = Gravity.CENTER
        textSize = cellTextSize - 1f
        setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(dpToPx(36), height)
        background = cellDrawable(
            ContextCompat.getColor(this@TarotGameActivity, R.color.header_cell_background))
        setTextColor(
            ContextCompat.getColor(this@TarotGameActivity, R.color.header_cell_text))
    }

    private fun makeSingleLineCell(text: String, bold: Boolean = false, height: Int): TextView =
        TextView(this).apply {
            this.text = text
            gravity = Gravity.CENTER
            textSize = cellTextSize
            if (bold) setTypeface(null, Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, height, 1f)
            background = cellDrawable(
                ContextCompat.getColor(this@TarotGameActivity, R.color.score_cell_background))
            setTextColor(
                ContextCompat.getColor(this@TarotGameActivity, R.color.score_cell_text))
        }

    private fun makeTwoLineCell(
        line1: String, line1Color: Int, line2: String, height: Int
    ): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(0, height, 1f)
        background = cellDrawable(
            ContextCompat.getColor(this@TarotGameActivity, R.color.score_cell_background))

        addView(TextView(this@TarotGameActivity).apply {
            text = line1
            gravity = Gravity.CENTER
            textSize = cellTextSize
            setTypeface(null, Typeface.BOLD)
            setTextColor(line1Color)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            maxLines = 1
        })

        addView(android.view.View(this@TarotGameActivity).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1))
            setBackgroundColor(ContextCompat.getColor(this@TarotGameActivity,
                R.color.tarot_cell_border))
        })

        addView(TextView(this@TarotGameActivity).apply {
            text = line2.ifEmpty { " " }
            gravity = Gravity.CENTER
            textSize = cellTextSize - 2.5f
            setTextColor(ContextCompat.getColor(this@TarotGameActivity, R.color.score_cell_text))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
    }

    private fun cellDrawable(bgColor: Int): GradientDrawable = GradientDrawable().apply {
        setColor(bgColor)
        setStroke(1, ContextCompat.getColor(this@TarotGameActivity, R.color.tarot_cell_border))
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                AlertDialog.Builder(this)
                    .setTitle(R.string.tarot_quit_game)
                    .setMessage(R.string.tarot_quit_game_message)
                    .setPositiveButton(R.string.yes) { _, _ -> finish() }
                    .setNegativeButton(R.string.no, null)
                    .show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
