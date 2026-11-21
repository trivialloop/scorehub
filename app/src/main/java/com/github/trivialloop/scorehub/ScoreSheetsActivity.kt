package com.github.trivialloop.scorehub

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.trivialloop.scorehub.data.AppDatabase
import com.github.trivialloop.scorehub.data.ScoreSheet
import com.github.trivialloop.scorehub.databinding.ActivityScoreSheetsBinding
import com.github.trivialloop.scorehub.utils.LocaleHelper
import com.github.trivialloop.scorehub.utils.ScoreSheetManager
import com.github.trivialloop.scorehub.utils.ScoreSheetPrefs
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** A sheet + the players of its session (derived from game_results, always up to date). */
data class SheetItem(val sheet: ScoreSheet, val players: List<Pair<Long, String>>)

class ScoreSheetsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScoreSheetsBinding
    private lateinit var database: AppDatabase

    private var items: List<SheetItem> = emptyList()
    private var gameValues: List<String?> = listOf(null)
    private var playerValues: List<Long?> = listOf(null)
    private var gameFilter: String? = null
    private var playerFilter: Long? = null

    companion object {
        /** Optional: restrict the list to one game (used by the "History" button of each game). */
        const val EXTRA_GAME_TYPE = "GAME_TYPE"

        fun start(context: Context, gameType: String? = null) {
            context.startActivity(Intent(context, ScoreSheetsActivity::class.java).apply {
                if (gameType != null) putExtra(EXTRA_GAME_TYPE, gameType)
            })
        }
    }

    override fun attachBaseContext(newBase: Context) {
        val language = LocaleHelper.getPersistedLocale(newBase)
        super.attachBaseContext(LocaleHelper.setLocale(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScoreSheetsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.appBarLayout.setPadding(0, systemBars.top, 0, 0)
            binding.root.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            insets
        }

        database = AppDatabase.getDatabase(this)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.score_sheets_title)

        binding.recyclerView.layoutManager = LinearLayoutManager(this)

        // Opened from a game: the game filter is fixed
        intent.getStringExtra(EXTRA_GAME_TYPE)?.let {
            gameFilter = it
            binding.spinnerGameFilter.isEnabled = false
            supportActionBar?.title = GameRegistry.findByType(it)?.let { d -> getString(d.nameResId) }
                ?: getString(R.string.score_sheets_title)
        }

        binding.spinnerGameFilter.onItemSelectedListener = selectionListener { pos ->
            gameFilter = gameValues.getOrNull(pos); applyFilter()
        }
        binding.spinnerPlayerFilter.onItemSelectedListener = selectionListener { pos ->
            playerFilter = playerValues.getOrNull(pos); applyFilter()
        }
    }

    // Reloaded on resume so a deletion made in the viewer is reflected immediately
    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            val dao = database.scoreSheetDao()
            val all = dao.getAll()

            // Files can be missing (e.g. after a restore): drop the orphan rows
            val (present, orphans) = all.partition { ScoreSheetManager.fileOf(this@ScoreSheetsActivity, it).exists() }
            orphans.forEach { dao.deleteById(it.id) }

            val resultsBySession = database.gameResultDao().getAllResults()
                .groupBy { it.gameType to it.playedAt }
            items = present.map { sheet ->
                val players = resultsBySession[sheet.gameType to sheet.playedAt].orEmpty()
                    .map { it.playerId to it.playerName }.distinctBy { it.first }
                SheetItem(sheet, players)
            }
            refreshSpinners()
            applyFilter()
        }
    }

    private fun gameName(type: String) =
        GameRegistry.findByType(type)?.let { getString(it.nameResId) } ?: type

    private fun refreshSpinners() {
        val games = items.map { it.sheet.gameType }.distinct().sortedBy { gameName(it) }
        gameValues = listOf<String?>(null) + games
        binding.spinnerGameFilter.adapter = spinnerAdapter(
            listOf(getString(R.string.filter_all_games)) + games.map { gameName(it) })
        binding.spinnerGameFilter.setSelection(gameValues.indexOf(gameFilter).coerceAtLeast(0))

        val players = items.flatMap { it.players }.distinctBy { it.first }.sortedBy { it.second.lowercase() }
        playerValues = listOf<Long?>(null) + players.map { it.first }
        binding.spinnerPlayerFilter.adapter = spinnerAdapter(
            listOf(getString(R.string.filter_all_players)) + players.map { it.second })
        binding.spinnerPlayerFilter.setSelection(playerValues.indexOf(playerFilter).coerceAtLeast(0))
    }

    private fun applyFilter() {
        val shown = items.filter { item ->
            (gameFilter == null || item.sheet.gameType == gameFilter) &&
                (playerFilter == null || item.players.any { it.first == playerFilter })
        }
        binding.recyclerView.adapter = ScoreSheetAdapter(shown) { sheet ->
            startActivity(Intent(this, ScoreSheetViewerActivity::class.java)
                .putExtra(ScoreSheetViewerActivity.EXTRA_SHEET_ID, sheet.id)
                .putExtra(ScoreSheetViewerActivity.EXTRA_SHEET_IDS, shown.map { it.sheet.id }.toLongArray()))
        }
        binding.textEmpty.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
        binding.textEmpty.setText(
            if (items.isEmpty() && !ScoreSheetPrefs.isEnabled(this)) R.string.score_sheets_disabled
            else R.string.score_sheets_empty
        )
    }

    private fun spinnerAdapter(labels: List<String>) =
        ArrayAdapter(this, android.R.layout.simple_spinner_item, labels).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

    private fun selectionListener(onSelected: (Int) -> Unit) = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = onSelected(position)
        override fun onNothingSelected(parent: AdapterView<*>?) {}
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> { finish(); true }
        else -> super.onOptionsItemSelected(item)
    }
}

class ScoreSheetAdapter(
    private val items: List<SheetItem>,
    private val onClick: (ScoreSheet) -> Unit
) : RecyclerView.Adapter<ScoreSheetAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.imageGameIcon)
        val name: TextView = view.findViewById(R.id.textGameName)
        val meta: TextView = view.findViewById(R.id.textGameMeta)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_game_card, parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val sheet = item.sheet
        val def = GameRegistry.findByType(sheet.gameType)
        if (def != null) {
            holder.icon.setImageResource(def.iconResId)
            holder.name.text = holder.itemView.context.getString(def.nameResId)
        } else {
            holder.name.text = sheet.gameType
        }
        val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(sheet.playedAt))
        holder.meta.text = if (item.players.isEmpty()) date
        else "$date\n${item.players.joinToString(", ") { it.second }}"
        holder.itemView.setOnClickListener { onClick(sheet) }
    }

    override fun getItemCount() = items.size
}
