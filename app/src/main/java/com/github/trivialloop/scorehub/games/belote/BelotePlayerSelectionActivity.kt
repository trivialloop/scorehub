package com.github.trivialloop.scorehub.games.belote

import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.trivialloop.scorehub.R
import com.github.trivialloop.scorehub.data.AppDatabase
import com.github.trivialloop.scorehub.data.Player
import com.github.trivialloop.scorehub.databinding.ActivityPlayerSelectionBinding
import com.github.trivialloop.scorehub.ui.HelpDialogs
import com.github.trivialloop.scorehub.utils.LocaleHelper
import com.github.trivialloop.scorehub.utils.PlayerColors
import kotlinx.coroutines.launch
import java.util.Collections

class BelotePlayerSelectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerSelectionBinding
    private lateinit var adapter: BelotePlayerSelectionAdapter
    private lateinit var database: AppDatabase
    private lateinit var itemTouchHelper: ItemTouchHelper
    private val allPlayers = mutableListOf<Player>()
    private val selectedPlayers = mutableSetOf<Player>()

    companion object {
        const val GAME_TYPE = "belote"
        // Belote is fixed: exactly 4 players, 2 teams of 2.
        private const val REQUIRED_PLAYERS = 4
    }

    override fun attachBaseContext(newBase: Context) {
        val language = LocaleHelper.getPersistedLocale(newBase)
        super.attachBaseContext(LocaleHelper.setLocale(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerSelectionBinding.inflate(layoutInflater)
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
        supportActionBar?.title = getString(R.string.belote_game)

        setupRecyclerView()

        binding.btnAddPlayer.setOnClickListener { showAddPlayerDialog() }
        binding.btnStartGame.setOnClickListener {
            if (selectedPlayers.size != REQUIRED_PLAYERS) {
                Toast.makeText(this,
                    getString(R.string.player_count_min_error, REQUIRED_PLAYERS, REQUIRED_PLAYERS),
                    Toast.LENGTH_SHORT).show()
            } else {
                showTeamModeChooserDialog()
            }
        }

        loadPlayers()
        loadLastPlayerOrder()
    }

    private fun setupRecyclerView() {
        adapter = BelotePlayerSelectionAdapter(
            allPlayers, selectedPlayers,
            onCheckChanged = { player, isChecked ->
                if (isChecked) {
                    if (selectedPlayers.size >= REQUIRED_PLAYERS) {
                        Toast.makeText(this,
                            getString(R.string.player_count_max_error, REQUIRED_PLAYERS, REQUIRED_PLAYERS),
                            Toast.LENGTH_SHORT).show()
                        adapter.notifyDataSetChanged()
                    } else {
                        selectedPlayers.add(player)
                    }
                } else {
                    selectedPlayers.remove(player)
                }
            },
            onEditClick = { showEditPlayerDialog(it) },
            onDeleteClick = { deletePlayer(it) },
            onStartDrag = { itemTouchHelper.startDrag(it) }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder): Boolean {
                Collections.swap(allPlayers, vh.bindingAdapterPosition, t.bindingAdapterPosition)
                adapter.notifyItemMoved(vh.bindingAdapterPosition, t.bindingAdapterPosition)
                return true
            }
            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {}
        }
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(binding.recyclerView)
    }

    private fun loadPlayers() {
        lifecycleScope.launch {
            database.playerDao().getAllPlayers().collect { players ->
                val isFirstLoad = allPlayers.isEmpty()
                allPlayers.clear()
                allPlayers.addAll(players)
                if (isFirstLoad) loadLastPlayerOrder()
                adapter.notifyDataSetChanged()
            }
        }
    }

    // ─── Add / edit / delete player (identical pattern to other games) ────────

    private fun showAddPlayerDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_player, null)
        val editName = dialogView.findViewById<android.widget.EditText>(R.id.editPlayerName)
        val colorPicker = dialogView.findViewById<com.github.trivialloop.scorehub.ui.ColorPickerView>(R.id.colorPickerView)
        val colorPreview = dialogView.findViewById<View>(R.id.colorPreview)

        var selectedColor = PlayerColors.getNextColor()
        colorPicker.setColor(selectedColor)
        (colorPreview.background as? GradientDrawable)?.setColor(selectedColor)
        colorPicker.onColorChanged = { color ->
            selectedColor = color
            (colorPreview.background as? GradientDrawable)?.setColor(color)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.add_player)
            .setView(dialogView)
            .setPositiveButton(R.string.add) { _, _ ->
                val name = editName.text.toString().trim()
                if (name.isEmpty()) Toast.makeText(this, R.string.player_name_empty, Toast.LENGTH_SHORT).show()
                else addPlayer(name, selectedColor)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showEditPlayerDialog(player: Player) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_player, null)
        val editName = dialogView.findViewById<android.widget.EditText>(R.id.editPlayerName)
        val colorPicker = dialogView.findViewById<com.github.trivialloop.scorehub.ui.ColorPickerView>(R.id.colorPickerView)
        val colorPreview = dialogView.findViewById<View>(R.id.colorPreview)

        editName.setText(player.name)
        var selectedColor = player.color
        colorPicker.setColor(selectedColor)
        (colorPreview.background as? GradientDrawable)?.setColor(selectedColor)
        colorPicker.onColorChanged = { color ->
            selectedColor = color
            (colorPreview.background as? GradientDrawable)?.setColor(color)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.edit_player)
            .setView(dialogView)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = editName.text.toString().trim()
                if (name.isEmpty()) Toast.makeText(this, R.string.player_name_empty, Toast.LENGTH_SHORT).show()
                else updatePlayer(player, name, selectedColor)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun addPlayer(name: String, color: Int) {
        lifecycleScope.launch {
            if (database.playerDao().getPlayerByName(name) != null) {
                Toast.makeText(this@BelotePlayerSelectionActivity, R.string.player_name_already_exists, Toast.LENGTH_SHORT).show()
            } else {
                val playerId = database.playerDao().insertPlayer(Player(name = name, color = color))
                selectedPlayers.add(Player(id = playerId, name = name, color = color))
                Toast.makeText(this@BelotePlayerSelectionActivity, getString(R.string.player_added, name), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updatePlayer(player: Player, newName: String, newColor: Int) {
        lifecycleScope.launch {
            val existing = database.playerDao().getPlayerByName(newName)
            if (existing != null && existing.id != player.id) {
                Toast.makeText(this@BelotePlayerSelectionActivity, R.string.player_name_already_exists, Toast.LENGTH_SHORT).show()
            } else {
                database.playerDao().updatePlayer(player.copy(name = newName, color = newColor))
                database.gameResultDao().updatePlayerNameInResults(player.id, newName)
                Toast.makeText(this@BelotePlayerSelectionActivity, getString(R.string.player_updated, newName), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deletePlayer(player: Player) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_player)
            .setMessage(getString(R.string.delete_player_message, player.name))
            .setPositiveButton(R.string.yes) { _, _ ->
                lifecycleScope.launch {
                    database.playerDao().deletePlayer(player)
                    selectedPlayers.remove(player)
                    Toast.makeText(this@BelotePlayerSelectionActivity, getString(R.string.player_deleted, player.name), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    // ─── Team assignment ────────────────────────────────────────────────────────

    private fun showTeamModeChooserDialog() {
        val orderedPlayers = allPlayers.filter { it in selectedPlayers }
        val options = arrayOf(
            getString(R.string.belote_team_mode_random),
            getString(R.string.belote_team_mode_manual)
        )
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.belote_team_mode_title))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> startGame(orderedPlayers, randomTeamAssignment())
                    1 -> launchManualTeamAssignment(orderedPlayers)
                }
            }
            .show()
    }

    private fun randomTeamAssignment(): List<Int> {
        val assignment = mutableListOf(0, 0, 1, 1)
        assignment.shuffle()
        return assignment
    }

    private fun launchManualTeamAssignment(players: List<Player>) {
        savePlayerOrder(players)
        val intent = Intent(this, BeloteTeamAssignmentActivity::class.java).apply {
            putExtra("PLAYER_IDS", players.map { it.id }.toLongArray())
            putExtra("PLAYER_NAMES", players.map { it.name }.toTypedArray())
            putExtra("PLAYER_COLORS", players.map { it.color }.toIntArray())
        }
        startActivity(intent)
    }

    private fun startGame(players: List<Player>, teamAssignment: List<Int>) {
        savePlayerOrder(players)
        val intent = Intent(this, BeloteGameActivity::class.java).apply {
            putExtra("PLAYER_IDS", players.map { it.id }.toLongArray())
            putExtra("PLAYER_NAMES", players.map { it.name }.toTypedArray())
            putExtra("PLAYER_COLORS", players.map { it.color }.toIntArray())
            putExtra("PLAYER_TEAMS", teamAssignment.toIntArray())
        }
        startActivity(intent)
    }

    private fun savePlayerOrder(players: List<Player>) {
        getSharedPreferences("belote_prefs", Context.MODE_PRIVATE)
            .edit().putString("last_player_order", players.joinToString(",") { it.id.toString() }).apply()
    }

    private fun loadLastPlayerOrder() {
        lifecycleScope.launch {
            val saved = getSharedPreferences("belote_prefs", Context.MODE_PRIVATE)
                .getString("last_player_order", null) ?: return@launch
            if (allPlayers.isEmpty()) return@launch
            val savedIds = saved.split(",").mapNotNull { it.toLongOrNull() }
            val ordered = mutableListOf<Player>()
            for (id in savedIds) allPlayers.find { it.id == id }?.let { ordered.add(it) }
            for (p in allPlayers) if (!ordered.contains(p)) ordered.add(p)
            allPlayers.clear()
            allPlayers.addAll(ordered)
            adapter.notifyDataSetChanged()
        }
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_belote_player_selection, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
            R.id.action_belote_game_stats -> { startActivity(Intent(this, BeloteStatsActivity::class.java)); true }
            R.id.action_belote_top20 -> { startActivity(Intent(this, BeloteTop20Activity::class.java)); true }
            R.id.action_help -> { HelpDialogs.showGameHelp(this, GAME_TYPE); true }
            else -> super.onOptionsItemSelected(item)
        }
    }
}

class BelotePlayerSelectionAdapter(
    private val players: List<Player>,
    private val selectedPlayers: Set<Player>,
    private val onCheckChanged: (Player, Boolean) -> Unit,
    private val onEditClick: (Player) -> Unit,
    private val onDeleteClick: (Player) -> Unit,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit
) : RecyclerView.Adapter<BelotePlayerSelectionAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val checkbox: CheckBox = view.findViewById(R.id.checkboxPlayer)
        val colorIndicator: View = view.findViewById(R.id.colorIndicator)
        val textName: TextView = view.findViewById(R.id.textPlayerName)
        val btnEdit: android.widget.ImageButton = view.findViewById(R.id.btnEdit)
        val btnDrag: android.widget.ImageButton = view.findViewById(R.id.btnDrag)
        val btnDelete: android.widget.ImageButton = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_player_selection, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val player = players[position]
        holder.textName.text = player.name
        holder.checkbox.setOnCheckedChangeListener(null)
        holder.checkbox.isChecked = player in selectedPlayers
        (holder.colorIndicator.background as? GradientDrawable)?.setColor(player.color)
        holder.checkbox.setOnCheckedChangeListener { _, isChecked -> onCheckChanged(player, isChecked) }
        holder.btnEdit.setOnClickListener { onEditClick(player) }
        holder.btnDelete.setOnClickListener { onDeleteClick(player) }
        holder.btnDrag.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) onStartDrag(holder)
            false
        }
    }

    override fun getItemCount() = players.size
}
