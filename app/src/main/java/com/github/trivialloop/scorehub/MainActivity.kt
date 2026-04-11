package com.github.trivialloop.scorehub

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.trivialloop.scorehub.data.AppDatabase
import com.github.trivialloop.scorehub.databinding.ActivityMainBinding
import com.github.trivialloop.scorehub.utils.LocaleHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─── Sort order ───────────────────────────────────────────────────────────────

enum class GameSortOrder {
    ALPHABETICAL,
    RECENTLY_PLAYED,
    MOST_PLAYED
}

// ─── Runtime game entry (definition + live DB stats) ─────────────────────────

data class GameEntry(
    val definition: GameRegistry.GameDefinition,
    val nameLocalized: String,
    var lastPlayedAt: Long? = null,
    var totalGames: Int = 0
)

// ─── MainActivity ─────────────────────────────────────────────────────────────

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var database: AppDatabase

    /** Full list built from [GameRegistry], enriched with DB stats. */
    private val allGames = mutableListOf<GameEntry>()

    /** Filtered + sorted subset shown in the RecyclerView. */
    private val filteredGames = mutableListOf<GameEntry>()
    private lateinit var adapter: GameListAdapter

    private var currentSort = GameSortOrder.ALPHABETICAL
    private var currentQuery = ""

    companion object {
        private const val PREFS_NAME = "main_prefs"
        private const val KEY_SORT_ORDER = "sort_order"
    }

    // ─── Locale ───────────────────────────────────────────────────────────────

    override fun attachBaseContext(newBase: Context) {
        val language = LocaleHelper.getPersistedLocale(newBase)
        super.attachBaseContext(LocaleHelper.setLocale(newBase, language))
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = AppDatabase.getDatabase(this)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.app_name)

        currentSort = loadSortOrder()

        setupRecyclerView()
        setupSearchView()
        setupSortButton()
        buildGameList()
        updateSortLabel()
    }

    // ─── Game list construction ───────────────────────────────────────────────

    /**
     * Converts every [GameRegistry.GameDefinition] into a runtime [GameEntry],
     * then fetches DB stats (last played, session count) asynchronously.
     *
     * Adding a new game to the app only requires registering it in [GameRegistry].
     * No change to this function is ever needed.
     */
    private fun buildGameList() {
        allGames.clear()
        allGames.addAll(
            GameRegistry.ALL_GAMES.map { def ->
                GameEntry(
                    definition    = def,
                    nameLocalized = getString(def.nameResId)
                )
            }
        )

        // Show the list immediately with static data, then enrich with DB stats
        applyFilterAndSort()

        lifecycleScope.launch {
            for (game in allGames) {
                game.lastPlayedAt = database.gameResultDao().getLastPlayedAt(game.definition.gameType)
                game.totalGames   = database.gameResultDao().getTotalSessionCount(game.definition.gameType)
            }
            applyFilterAndSort()
        }
    }

    // ─── RecyclerView ─────────────────────────────────────────────────────────

    private fun setupRecyclerView() {
        adapter = GameListAdapter(filteredGames) { game ->
            startActivity(Intent(this, game.definition.activityClass))
        }
        binding.recyclerViewGames.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewGames.adapter = adapter
    }

    // ─── Search ───────────────────────────────────────────────────────────────

    private fun setupSearchView() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                currentQuery = newText.orEmpty().trim()
                applyFilterAndSort()
                return true
            }
        })
    }

    // ─── Sort ─────────────────────────────────────────────────────────────────

    private fun setupSortButton() {
        binding.btnSort.setOnClickListener {
            val options = arrayOf(
                getString(R.string.sort_alphabetical),
                getString(R.string.sort_recently_played),
                getString(R.string.sort_most_played)
            )
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.sort_games))
                .setSingleChoiceItems(options, currentSort.ordinal) { dialog, which ->
                    currentSort = GameSortOrder.entries[which]
                    saveSortOrder(currentSort)
                    applyFilterAndSort()
                    updateSortLabel()
                    dialog.dismiss()
                }
                .show()
        }
    }

    private fun updateSortLabel() {
        binding.textSortLabel.text = when (currentSort) {
            GameSortOrder.ALPHABETICAL    -> getString(R.string.sort_label_alphabetical)
            GameSortOrder.RECENTLY_PLAYED -> getString(R.string.sort_label_recently_played)
            GameSortOrder.MOST_PLAYED     -> getString(R.string.sort_label_most_played)
        }
    }

    // ─── Filter + Sort logic ──────────────────────────────────────────────────

    /**
     * Filters by the active search query (matched against both the English
     * fallback name and the localised name), then sorts by [currentSort].
     *
     * The English fallback ensures that typing "yahtzee" always matches
     * regardless of the active locale.
     */
    private fun applyFilterAndSort() {
        val query = currentQuery.lowercase(Locale.getDefault())

        val filtered = if (query.isEmpty()) {
            allGames.toMutableList()
        } else {
            allGames.filter { game ->
                game.definition.nameEnFallback.lowercase(Locale.getDefault()).contains(query) ||
                        game.nameLocalized.lowercase(Locale.getDefault()).contains(query)
            }.toMutableList()
        }

        val sorted = when (currentSort) {
            GameSortOrder.ALPHABETICAL    -> filtered.sortedBy { it.nameLocalized }
            GameSortOrder.RECENTLY_PLAYED -> filtered.sortedByDescending { it.lastPlayedAt ?: Long.MIN_VALUE }
            GameSortOrder.MOST_PLAYED     -> filtered.sortedByDescending { it.totalGames }
        }

        filteredGames.clear()
        filteredGames.addAll(sorted)
        adapter.notifyDataSetChanged()
    }

    // ─── Persistence ──────────────────────────────────────────────────────────

    private fun saveSortOrder(order: GameSortOrder) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_SORT_ORDER, order.name).apply()
    }

    private fun loadSortOrder(): GameSortOrder {
        val saved = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SORT_ORDER, GameSortOrder.ALPHABETICAL.name)
        return try {
            GameSortOrder.valueOf(saved ?: GameSortOrder.ALPHABETICAL.name)
        } catch (_: IllegalArgumentException) {
            GameSortOrder.ALPHABETICAL
        }
    }

    // ─── Options menu ─────────────────────────────────────────────────────────

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_general_stats -> {
                startActivity(Intent(this, GeneralStatsActivity::class.java))
                true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}

// ─── Adapter ──────────────────────────────────────────────────────────────────

class GameListAdapter(
    private val games: List<GameEntry>,
    private val onClick: (GameEntry) -> Unit
) : RecyclerView.Adapter<GameListAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageIcon: ImageView = view.findViewById(R.id.imageGameIcon)
        val textName: TextView   = view.findViewById(R.id.textGameName)
        val textMeta: TextView   = view.findViewById(R.id.textGameMeta)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_game_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val game = games[position]
        val ctx  = holder.itemView.context

        holder.imageIcon.setImageResource(game.definition.iconResId)
        holder.textName.text = game.nameLocalized
        holder.textMeta.text = buildMetaText(ctx, game)

        holder.itemView.setOnClickListener { onClick(game) }
    }

    override fun getItemCount() = games.size

    private fun buildMetaText(ctx: Context, game: GameEntry): String {
        val parts = mutableListOf<String>()

        if (game.totalGames > 0) {
            parts.add(
                ctx.resources.getQuantityString(R.plurals.games_count, game.totalGames, game.totalGames)
            )
        }

        game.lastPlayedAt?.let { ts ->
            val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(ts))
            parts.add(ctx.getString(R.string.last_played_date, dateStr))
        }

        return if (parts.isEmpty()) ctx.getString(R.string.no_games_played) else parts.joinToString("  •  ")
    }
}
