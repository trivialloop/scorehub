package com.github.trivialloop.scorehub

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.github.trivialloop.scorehub.data.AppDatabase
import com.github.trivialloop.scorehub.data.Player
import com.github.trivialloop.scorehub.data.backup.*
import com.github.trivialloop.scorehub.databinding.ActivityBackupImportBinding
import com.github.trivialloop.scorehub.ui.BackupMessages
import com.github.trivialloop.scorehub.ui.ColorPickerView
import com.github.trivialloop.scorehub.utils.LocaleHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Merge screen: link or create each imported player, preview games, then import. */
class BackupImportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBackupImportBinding
    private lateinit var repo: BackupRepository
    private lateinit var backup: BackupFile
    private lateinit var analysis: ImportAnalysis
    private var localPlayers: List<Player> = emptyList()
    private var localOptions: List<Player> = emptyList()   // local players a source can be linked to
    private var spByUuid: Map<String, SourcePlayer> = emptyMap()

    private val decisions = mutableMapOf<String, PlayerDecision>()
    private val newNames = mutableMapOf<String, String>()
    private val newColors = mutableMapOf<String, Int>()
    private val errorViews = mutableMapOf<String, TextView>()
    private lateinit var newGamesContainer: LinearLayout
    private lateinit var presentGamesContainer: LinearLayout
    private var ready = false

    override fun attachBaseContext(newBase: Context) {
        val language = LocaleHelper.getPersistedLocale(newBase)
        super.attachBaseContext(LocaleHelper.setLocale(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBackupImportBinding.inflate(layoutInflater)
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

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.backup_merge_title)

        repo = BackupRepository(AppDatabase.getDatabase(this))
        binding.btnImport.setOnClickListener { runImport() }

        val uri = intent.data
        if (uri == null) { finish(); return }

        lifecycleScope.launch {
            try {
                val text = BackupFiles.read(contentResolver, uri)
                backup = withContext(Dispatchers.Default) { repo.parseAndCheck(text) }
                localPlayers = repo.localPlayers()
                analysis = repo.analyze(backup)
            } catch (e: Exception) {
                Toast.makeText(this@BackupImportActivity, BackupMessages.of(this@BackupImportActivity, e), Toast.LENGTH_LONG).show()
                finish()
                return@launch
            }
            buildContent()
        }
    }

    // ─── Content ──────────────────────────────────────────────────────────────

    private fun buildContent() {
        spByUuid = analysis.sourcePlayers.associateBy { it.player.uuid }
        val autoLocalIds = analysis.sourcePlayers
            .mapNotNull { (it.status as? SourceStatus.AutoMatched)?.localPlayerId }.toSet()
        localOptions = localPlayers.filter { it.id !in autoLocalIds }

        val c = binding.contentContainer
        c.removeAllViews()

        c.addView(sectionTitle(getString(R.string.backup_section_players)))
        // Players needing a decision first, then the ones matched automatically
        val ordered = analysis.sourcePlayers.sortedBy { it.status is SourceStatus.AutoMatched }
        ordered.forEach { c.addView(buildPlayerCard(it)) }

        c.addView(sectionTitle(getString(R.string.backup_section_new_games, analysis.newGames.size)))
        newGamesContainer = column().also { c.addView(it) }

        c.addView(sectionTitle(getString(R.string.backup_section_present_games, analysis.alreadyPresentGames.size)))
        presentGamesContainer = column().also { c.addView(it) }

        ready = true
        refresh()
    }

    private fun buildPlayerCard(sp: SourcePlayer): LinearLayout {
        val card = column().apply {
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(8) }
            background = GradientDrawable().apply {
                setColor(ContextCompat.getColor(this@BackupImportActivity, R.color.score_cell_background))
                setStroke(1, ContextCompat.getColor(this@BackupImportActivity, R.color.cell_border))
                cornerRadius = dp(8).toFloat()
            }
        }

        // Header: color dot + source name
        card.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(swatch(sp.player.color, 16))
            addView(TextView(this@BackupImportActivity).apply {
                text = sp.player.name
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                maxLines = 1
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .also { it.marginStart = dp(8) }
            })
        })

        when (val st = sp.status) {
            is SourceStatus.AutoMatched -> {
                val local = localPlayers.first { it.id == st.localPlayerId }
                val reason = getString(
                    if (st.reason == MatchReason.SAME_UUID) R.string.backup_match_same_uuid
                    else R.string.backup_match_linked_uuid
                )
                card.addView(secondaryText("→ ${local.name}  ·  $reason"))
            }
            is SourceStatus.NeedsDecision -> buildDecisionControls(card, sp.player, st)
        }
        return card
    }

    private fun buildDecisionControls(card: LinearLayout, s: BackupPlayer, st: SourceStatus.NeedsDecision) {
        val uuid = s.uuid
        newNames[uuid] = st.suggestedName
        newColors[uuid] = s.color

        if (st.conflictingLocalIds.isNotEmpty()) card.addView(secondaryText(getString(R.string.backup_ambiguous)))

        // Spinner: 0 = undecided, 1 = new player, 2.. = link to localOptions[i - 2]
        val labels = buildList {
            add(getString(R.string.backup_choice_undecided))
            add(getString(R.string.backup_choice_new))
            localOptions.forEach { l ->
                add(getString(
                    if (l.id == st.sameNameLocalId) R.string.backup_choice_link_same_name else R.string.backup_choice_link,
                    l.name
                ))
            }
        }
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@BackupImportActivity, android.R.layout.simple_spinner_item, labels)
                .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            minimumHeight = dp(48)
        }
        card.addView(spinner)

        // Name + color, only visible for "new player"
        val colorSwatch = swatch(s.color, 36).apply { isClickable = true }
        val nameEdit = EditText(this).apply {
            setText(st.suggestedName)
            hint = getString(R.string.backup_name_hint)
            setSingleLine()
            filters = arrayOf(InputFilter.LengthFilter(PLAYER_NAME_MAX_LENGTH + 10)) // validated, not silently cut
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .also { it.marginStart = dp(8) }
        }
        val nameRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            addView(colorSwatch)
            addView(nameEdit)
        }
        card.addView(nameRow)

        fun syncNewDecision() {
            if (decisions[uuid] is PlayerDecision.CreateNew) {
                decisions[uuid] = PlayerDecision.CreateNew(newNames.getValue(uuid), newColors.getValue(uuid))
            }
        }

        nameEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(e: Editable?) {
                newNames[uuid] = e?.toString().orEmpty()
                syncNewDecision()
                refresh()
            }
        })

        colorSwatch.setOnClickListener {
            showColorDialog(newColors.getValue(uuid)) { color ->
                newColors[uuid] = color
                colorSwatch.background = oval(color)
                syncNewDecision()
                refresh()
            }
        }

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, position: Int, id: Long) {
                when (position) {
                    0 -> decisions.remove(uuid)
                    1 -> decisions[uuid] = PlayerDecision.CreateNew(newNames.getValue(uuid), newColors.getValue(uuid))
                    else -> decisions[uuid] = PlayerDecision.LinkTo(localOptions[position - 2].id)
                }
                nameRow.visibility = if (position == 1) View.VISIBLE else View.GONE
                refresh()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        val error = TextView(this).apply {
            textSize = 12f
            setTextColor(ContextCompat.getColor(this@BackupImportActivity, R.color.score_text_worst))
            visibility = View.GONE
        }
        errorViews[uuid] = error
        card.addView(error)
    }

    // ─── Live validation and preview ─────────────────────────────────────────

    private fun refresh() {
        if (!ready) return
        val issues = BackupMergePlanner.validate(analysis, decisions, localPlayers)

        for ((uuid, view) in errorViews) {
            val issue = issues[uuid]
            if (issue == null || issue == PlayerIssue.UNDECIDED) {
                view.visibility = View.GONE
            } else {
                view.text = getString(issueMessage(issue))
                view.visibility = View.VISIBLE
            }
        }

        binding.textStatus.apply {
            if (issues.isEmpty()) visibility = View.GONE
            else { text = getString(R.string.backup_players_left, issues.size); visibility = View.VISIBLE }
        }
        binding.btnImport.isEnabled = issues.isEmpty()

        fillGames(newGamesContainer, analysis.newGames)
        fillGames(presentGamesContainer, analysis.alreadyPresentGames)
    }

    private fun issueMessage(issue: PlayerIssue): Int = when (issue) {
        PlayerIssue.UNDECIDED -> R.string.backup_choice_undecided
        PlayerIssue.UNKNOWN_LOCAL -> R.string.backup_issue_unknown_local
        PlayerIssue.LOCAL_ALREADY_USED -> R.string.backup_issue_local_used
        PlayerIssue.NAME_EMPTY -> R.string.player_name_empty
        PlayerIssue.NAME_TOO_LONG -> R.string.backup_issue_name_too_long
        PlayerIssue.NAME_TAKEN -> R.string.backup_issue_name_taken
    }

    /** Name shown in the preview: local name when matched/linked, typed name for a new player. */
    private fun displayName(uuid: String): String {
        val sp = spByUuid.getValue(uuid)
        val localId = when (val st = sp.status) {
            is SourceStatus.AutoMatched -> st.localPlayerId
            is SourceStatus.NeedsDecision -> (decisions[uuid] as? PlayerDecision.LinkTo)?.localPlayerId
        }
        if (localId != null) localPlayers.firstOrNull { it.id == localId }?.let { return it.name }
        (decisions[uuid] as? PlayerDecision.CreateNew)?.name?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        return sp.player.name
    }

    private fun fillGames(container: LinearLayout, games: List<BackupGame>) {
        container.removeAllViews()
        if (games.isEmpty()) { container.addView(secondaryText(getString(R.string.backup_no_games))); return }

        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        for (g in games.sortedBy { it.playedAt }) {
            val gameName = GameRegistry.findByType(g.gameType)?.let { getString(it.nameResId) } ?: g.gameType
            container.addView(column().apply {
                setPadding(0, dp(4), 0, dp(4))
                addView(TextView(this@BackupImportActivity).apply {
                    text = "$gameName  ·  ${dateFormat.format(Date(g.playedAt))}"
                    textSize = 13f
                    setTypeface(null, Typeface.BOLD)
                })
                addView(secondaryText(g.results.joinToString("  ·  ") { "${displayName(it.playerUuid)} ${it.score}" }))
            })
        }
    }

    // ─── Import ───────────────────────────────────────────────────────────────

    private fun runImport() {
        binding.btnImport.isEnabled = false
        lifecycleScope.launch {
            try {
                val plan = BackupMergePlanner.plan(analysis, decisions, localPlayers)
                repo.applyMerge(plan)
                Toast.makeText(
                    this@BackupImportActivity,
                    getString(R.string.backup_merge_done, plan.newGames.size, plan.newPlayers.size),
                    Toast.LENGTH_LONG
                ).show()
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@BackupImportActivity, BackupMessages.of(this@BackupImportActivity, e), Toast.LENGTH_LONG).show()
                refresh()
            }
        }
    }

    // ─── View helpers ─────────────────────────────────────────────────────────

    private fun showColorDialog(current: Int, onPicked: (Int) -> Unit) {
        var selected = current
        val picker = ColorPickerView(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(220), dp(220), Gravity.CENTER_HORIZONTAL)
            setColor(current)
            onColorChanged = { selected = it }
        }
        val container = FrameLayout(this).apply {
            setPadding(dp(16), dp(16), dp(16), dp(16))
            addView(picker)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.select_color)
            .setView(container)
            .setPositiveButton(R.string.ok) { _, _ -> onPicked(selected) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

    private fun sectionTitle(text: String) = TextView(this).apply {
        this.text = text
        textSize = 14f
        setTypeface(null, Typeface.BOLD)
        setTextColor(ContextCompat.getColor(this@BackupImportActivity, R.color.teal_700))
        setPadding(0, dp(16), 0, dp(8))
    }

    private fun secondaryText(text: String) = TextView(this).apply {
        this.text = text
        textSize = 12f
        alpha = 0.65f
        setPadding(0, dp(2), 0, dp(2))
    }

    private fun oval(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        setStroke(1, ContextCompat.getColor(this@BackupImportActivity, R.color.cell_border))
    }

    private fun swatch(color: Int, sizeDp: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
        background = oval(color)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> { finish(); true }
        else -> super.onOptionsItemSelected(item)
    }
}
