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
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
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

// ─── Active filter state ──────────────────────────────────────────────────────

/**
 * Holds all active filter criteria.
 * Null / empty values mean "no filter on this dimension".
 *
 * @param playerCount  Exact number of players to match (null = any).
 * @param soloOnly     true = solo only, false = team only, null = both.
 * @param equipment    Required equipment types; empty = no equipment filter.
 */
data class GameFilter(
    val playerCount: Int? = null,
    val soloOnly: Boolean? = null,
    val equipment: Set<Equipment> = emptySet()
) {
    val isEmpty: Boolean
        get() = playerCount == null && soloOnly == null && equipment.isEmpty()
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

    private val allGames = mutableListOf<GameEntry>()
    private val filteredGames = mutableListOf<GameEntry>()
    private lateinit var adapter: GameListAdapter

    private var currentSort = GameSortOrder.ALPHABETICAL
    private var currentQuery = ""
    private var currentFilter = GameFilter()

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
        setupFilterButton()
        buildGameList()
        updateStatusLabel()
    }

    // ─── Game list construction ───────────────────────────────────────────────

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
                    updateStatusLabel()
                    dialog.dismiss()
                }
                .show()
        }
    }

    // ─── Filter ───────────────────────────────────────────────────────────────

    private fun setupFilterButton() {
        binding.btnFilter.setOnClickListener { showFilterDialog() }
    }

    private fun showFilterDialog() {
        val sheet = BottomSheetDialog(this)
        val view  = layoutInflater.inflate(R.layout.dialog_filter, null)
        sheet.setContentView(view)

        // Local pending state — only committed to currentFilter on "Apply"
        var pendingPlayerCount = currentFilter.playerCount

        val textPlayersValue: TextView = view.findViewById(R.id.textPlayersValue)
        val textPlayersHint: TextView  = view.findViewById(R.id.textPlayersHint)
        val chipSolo:  Chip = view.findViewById(R.id.chipModeSolo)
        val chipTeam:  Chip = view.findViewById(R.id.chipModeTeam)
        val chipCards: Chip = view.findViewById(R.id.chipEquipCards)
        val chipDice:  Chip = view.findViewById(R.id.chipEquipDice)
        val chipBoard: Chip = view.findViewById(R.id.chipEquipBoard)

        // Restore current filter state into the dialog
        chipSolo.isChecked  = currentFilter.soloOnly == true
        chipTeam.isChecked  = currentFilter.soloOnly == false
        chipCards.isChecked = Equipment.CARDS in currentFilter.equipment
        chipDice.isChecked  = Equipment.DICE  in currentFilter.equipment
        chipBoard.isChecked = Equipment.BOARD in currentFilter.equipment

        // ── Builds a GameFilter from the current pending dialog state ──────────
        fun pendingFilter() = GameFilter(
            playerCount = pendingPlayerCount,
            soloOnly    = when {
                chipSolo.isChecked -> true
                chipTeam.isChecked -> false
                else               -> null
            },
            equipment = buildSet {
                if (chipCards.isChecked) add(Equipment.CARDS)
                if (chipDice.isChecked)  add(Equipment.DICE)
                if (chipBoard.isChecked) add(Equipment.BOARD)
            }
        )

        // ── Counts games matching the full pending filter (all criteria) ───────
        fun countMatching(): Int = allGames.count { game ->
            val f = pendingFilter()
            val playerOk = f.playerCount?.let { game.definition.supportsPlayerCount(it) } ?: true
            val modeOk   = f.soloOnly?.let { game.definition.teamMode == !it } ?: true
            val equipOk  = f.equipment.isEmpty() || f.equipment.all { it in game.definition.equipment }
            playerOk && modeOk && equipOk
        }

        // ── Refresh the player count line and hint ─────────────────────────────
        fun updatePlayerDisplay() {
            textPlayersValue.text = pendingPlayerCount?.toString()
                ?: getString(R.string.filter_players_any)

            val matching = countMatching()
            textPlayersHint.text = if (pendingFilter().isEmpty) ""
            else resources.getQuantityString(R.plurals.filter_players_hint, matching, matching)
        }

        updatePlayerDisplay()

        // ── Player count buttons ───────────────────────────────────────────────
        view.findViewById<View>(R.id.btnPlayersDecrement).setOnClickListener {
            pendingPlayerCount = when {
                pendingPlayerCount == null -> null
                pendingPlayerCount!! <= 1  -> null  // back to "any"
                else                       -> pendingPlayerCount!! - 1
            }
            updatePlayerDisplay()
        }

        view.findViewById<View>(R.id.btnPlayersIncrement).setOnClickListener {
            pendingPlayerCount = when (pendingPlayerCount) {
                null -> 1
                else -> (pendingPlayerCount!! + 1).coerceAtMost(8)
            }
            updatePlayerDisplay()
        }

        // ── Mode chips — mutually exclusive, both de-selectable ───────────────
        chipSolo.setOnCheckedChangeListener { _, checked ->
            if (checked) chipTeam.isChecked = false
            updatePlayerDisplay()
        }
        chipTeam.setOnCheckedChangeListener { _, checked ->
            if (checked) chipSolo.isChecked = false
            updatePlayerDisplay()
        }

        // ── Equipment chips — multi-select ────────────────────────────────────
        chipCards.setOnCheckedChangeListener { _, _ -> updatePlayerDisplay() }
        chipDice.setOnCheckedChangeListener  { _, _ -> updatePlayerDisplay() }
        chipBoard.setOnCheckedChangeListener { _, _ -> updatePlayerDisplay() }

        // ── Reset ──────────────────────────────────────────────────────────────
        view.findViewById<View>(R.id.btnFilterReset).setOnClickListener {
            pendingPlayerCount  = null
            chipSolo.isChecked  = false
            chipTeam.isChecked  = false
            chipCards.isChecked = false
            chipDice.isChecked  = false
            chipBoard.isChecked = false
            updatePlayerDisplay()
        }

        // ── Apply ──────────────────────────────────────────────────────────────
        view.findViewById<View>(R.id.btnFilterApply).setOnClickListener {
            currentFilter = pendingFilter()
            applyFilterAndSort()
            updateStatusLabel()
            updateFilterBadge()
            sheet.dismiss()
        }

        sheet.show()
    }

    private fun updateFilterBadge() {
        binding.filterActiveBadge.visibility =
            if (currentFilter.isEmpty) View.GONE else View.VISIBLE
    }

    // ─── Core filter + sort ───────────────────────────────────────────────────

    /**
     * Applies [currentQuery], [currentFilter] and [currentSort] to [allGames]
     * and refreshes [filteredGames].
     */
    private fun applyFilterAndSort() {
        val query = currentQuery.lowercase(Locale.getDefault())

        var result = allGames.asSequence()

        // 1. Search (EN fallback + localised name)
        if (query.isNotEmpty()) {
            result = result.filter { game ->
                game.definition.nameEnFallback.lowercase().contains(query) ||
                        game.nameLocalized.lowercase().contains(query)
            }
        }

        // 2. Player count (exact match within supported range)
        currentFilter.playerCount?.let { count ->
            result = result.filter { it.definition.supportsPlayerCount(count) }
        }

        // 3. Game mode
        currentFilter.soloOnly?.let { soloOnly ->
            result = result.filter { it.definition.teamMode == !soloOnly }
        }

        // 4. Equipment (game must require ALL selected types)
        if (currentFilter.equipment.isNotEmpty()) {
            result = result.filter { game ->
                currentFilter.equipment.all { eq -> eq in game.definition.equipment }
            }
        }

        // 5. Sort
        val sorted = when (currentSort) {
            GameSortOrder.ALPHABETICAL    -> result.sortedBy { it.nameLocalized }
            GameSortOrder.RECENTLY_PLAYED -> result.sortedByDescending { it.lastPlayedAt ?: Long.MIN_VALUE }
            GameSortOrder.MOST_PLAYED     -> result.sortedByDescending { it.totalGames }
        }

        filteredGames.clear()
        filteredGames.addAll(sorted)
        adapter.notifyDataSetChanged()
    }

    // ─── Status label ─────────────────────────────────────────────────────────

    private fun updateStatusLabel() {
        val parts = mutableListOf<String>()

        parts.add(when (currentSort) {
            GameSortOrder.ALPHABETICAL    -> getString(R.string.sort_label_alphabetical)
            GameSortOrder.RECENTLY_PLAYED -> getString(R.string.sort_label_recently_played)
            GameSortOrder.MOST_PLAYED     -> getString(R.string.sort_label_most_played)
        })

        if (!currentFilter.isEmpty) {
            val filterParts = mutableListOf<String>()
            currentFilter.playerCount?.let {
                filterParts.add(getString(R.string.filter_active_players, it))
            }
            currentFilter.soloOnly?.let {
                filterParts.add(
                    if (it) getString(R.string.filter_mode_solo)
                    else    getString(R.string.filter_mode_team)
                )
            }
            if (currentFilter.equipment.isNotEmpty()) {
                filterParts.add(currentFilter.equipment.joinToString(", ") { equipmentLabel(it) })
            }
            parts.add(getString(R.string.filter_active_summary, filterParts.joinToString(" · ")))
        }

        binding.textSortLabel.text = parts.joinToString("  |  ")
    }

    private fun equipmentLabel(equipment: Equipment): String = when (equipment) {
        Equipment.CARDS -> getString(R.string.filter_equipment_cards)
        Equipment.DICE  -> getString(R.string.filter_equipment_dice)
        Equipment.BOARD -> getString(R.string.filter_equipment_board)
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

        return if (parts.isEmpty()) ctx.getString(R.string.no_games_played)
        else parts.joinToString("  •  ")
    }
}