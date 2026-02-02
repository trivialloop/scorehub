package com.github.trivialloop.scorehub.games.yahtzee

import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.trivialloop.scorehub.R
import com.github.trivialloop.scorehub.data.AppDatabase
import com.github.trivialloop.scorehub.data.Player
import com.github.trivialloop.scorehub.databinding.ActivityPlayerSelectionBinding
import com.github.trivialloop.scorehub.utils.LocaleHelper
import com.github.trivialloop.scorehub.utils.PlayerColors
import kotlinx.coroutines.launch
import java.util.Collections

class YahtzeePlayerSelectionActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPlayerSelectionBinding
    private lateinit var adapter: PlayerSelectionAdapter
    private lateinit var database: AppDatabase
    private lateinit var itemTouchHelper: ItemTouchHelper
    private val allPlayers = mutableListOf<Player>()
    private val selectedPlayers = mutableSetOf<Player>()

    companion object {
        const val GAME_TYPE = "yahtzee"
    }

    override fun attachBaseContext(newBase: Context) {
        val language = LocaleHelper.getPersistedLocale(newBase)
        super.attachBaseContext(LocaleHelper.setLocale(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = AppDatabase.getDatabase(this)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.player_selection_title)

        setupRecyclerView()

        binding.btnAddPlayer.setOnClickListener {
            showAddPlayerDialog()
        }

        binding.btnStartGame.setOnClickListener {
            if (selectedPlayers.size < 1) {
                Toast.makeText(this, R.string.minimum_one_player, Toast.LENGTH_SHORT).show()
            } else {
                startGame()
            }
        }

        loadPlayers()
        loadLastPlayerOrder()
    }

    private fun setupRecyclerView() {
        adapter = PlayerSelectionAdapter(
            allPlayers,
            selectedPlayers,
            onCheckChanged = { player, isChecked ->
                if (isChecked) {
                    selectedPlayers.add(player)
                } else {
                    selectedPlayers.remove(player)
                }
            },
            onEditClick = { player ->
                showEditPlayerDialog(player)
            },
            onDeleteClick = { player ->
                deletePlayer(player)
            },
            onStartDrag = { viewHolder ->
                itemTouchHelper.startDrag(viewHolder)
            }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.adapterPosition
                val toPosition = target.adapterPosition
                Collections.swap(allPlayers, fromPosition, toPosition)
                adapter.notifyItemMoved(fromPosition, toPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
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

                if (isFirstLoad) {
                    loadLastPlayerOrder()
                }
                // Always notify adapter so new players are displayed
                adapter.notifyDataSetChanged()
            }
        }
    }

    private fun showAddPlayerDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_player, null)
        val editName = dialogView.findViewById<android.widget.EditText>(R.id.editPlayerName)
        val colorRecyclerView = dialogView.findViewById<RecyclerView>(R.id.colorRecyclerView)

        var selectedColor = PlayerColors.getNextColor()

        val colorAdapter = ColorSelectionAdapter(
            PlayerColors.getAvailableColors(),
            initialColor = selectedColor
        ) { color ->
            selectedColor = color
        }

        colorRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        colorRecyclerView.adapter = colorAdapter

        AlertDialog.Builder(this)
            .setTitle(R.string.add_player)
            .setView(dialogView)
            .setPositiveButton(R.string.add) { _, _ ->
                val name = editName.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, R.string.player_name_empty, Toast.LENGTH_SHORT).show()
                } else {
                    addPlayer(name, selectedColor)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showEditPlayerDialog(player: Player) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_player, null)
        val editName = dialogView.findViewById<android.widget.EditText>(R.id.editPlayerName)
        val colorRecyclerView = dialogView.findViewById<RecyclerView>(R.id.colorRecyclerView)

        editName.setText(player.name)
        var selectedColor = player.color

        val colorAdapter = ColorSelectionAdapter(
            PlayerColors.getAvailableColors(),
            initialColor = player.color
        ) { color ->
            selectedColor = color
        }

        colorRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        colorRecyclerView.adapter = colorAdapter

        AlertDialog.Builder(this)
            .setTitle(R.string.edit_player)
            .setView(dialogView)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = editName.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, R.string.player_name_empty, Toast.LENGTH_SHORT).show()
                } else {
                    updatePlayer(player, name, selectedColor)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun addPlayer(name: String, color: Int) {
        lifecycleScope.launch {
            val existingPlayer = database.playerDao().getPlayerByName(name)
            if (existingPlayer != null) {
                Toast.makeText(
                    this@YahtzeePlayerSelectionActivity,
                    R.string.player_name_already_exists,
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                val playerId = database.playerDao().insertPlayer(Player(name = name, color = color))
                val newPlayer = Player(id = playerId, name = name, color = color)
                selectedPlayers.add(newPlayer)
                Toast.makeText(
                    this@YahtzeePlayerSelectionActivity,
                    getString(R.string.player_added, name),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun updatePlayer(player: Player, newName: String, newColor: Int) {
        lifecycleScope.launch {
            // Vérifier si le nouveau nom existe déjà (sauf si c'est le même joueur)
            val existingPlayer = database.playerDao().getPlayerByName(newName)
            if (existingPlayer != null && existingPlayer.id != player.id) {
                Toast.makeText(
                    this@YahtzeePlayerSelectionActivity,
                    R.string.player_name_already_exists,
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                val updatedPlayer = player.copy(name = newName, color = newColor)
                database.playerDao().updatePlayer(updatedPlayer)

                // Mettre à jour les noms dans les résultats de jeux
                database.gameResultDao().updatePlayerNameInResults(player.id, newName)

                Toast.makeText(
                    this@YahtzeePlayerSelectionActivity,
                    getString(R.string.player_updated, newName),
                    Toast.LENGTH_SHORT
                ).show()
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
                    Toast.makeText(
                        this@YahtzeePlayerSelectionActivity,
                        getString(R.string.player_deleted, player.name),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    private fun startGame() {
        val playersInOrder = allPlayers.filter { it in selectedPlayers }

        // Sauvegarder l'ordre des joueurs
        savePlayerOrder(playersInOrder)

        val intent = Intent(this, YahtzeeGameActivity::class.java)
        intent.putExtra("PLAYER_IDS", playersInOrder.map { it.id }.toLongArray())
        intent.putExtra("PLAYER_NAMES", playersInOrder.map { it.name }.toTypedArray())
        intent.putExtra("PLAYER_COLORS", playersInOrder.map { it.color }.toIntArray())
        startActivity(intent)
    }

    private fun savePlayerOrder(players: List<Player>) {
        val prefs = getSharedPreferences("yahtzee_prefs", Context.MODE_PRIVATE)
        val playerIds = players.joinToString(",") { it.id.toString() }
        prefs.edit().putString("last_player_order", playerIds).apply()
    }

    private fun loadLastPlayerOrder() {
        lifecycleScope.launch {
            val prefs = getSharedPreferences("yahtzee_prefs", Context.MODE_PRIVATE)
            val savedOrder = prefs.getString("last_player_order", null)

            if (savedOrder != null && allPlayers.isNotEmpty()) {
                val savedIds = savedOrder.split(",").mapNotNull { it.toLongOrNull() }
                val orderedPlayers = mutableListOf<Player>()

                // Réorganiser selon l'ordre sauvegardé
                for (id in savedIds) {
                    allPlayers.find { it.id == id }?.let { orderedPlayers.add(it) }
                }

                // Ajouter les joueurs qui n'étaient pas dans la liste sauvegardée
                for (player in allPlayers) {
                    if (!orderedPlayers.contains(player)) {
                        orderedPlayers.add(player)
                    }
                }

                allPlayers.clear()
                allPlayers.addAll(orderedPlayers)
                adapter.notifyDataSetChanged()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_player_selection, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_game_stats -> {
                val intent = Intent(this, YahtzeeStatsActivity::class.java)
                startActivity(intent)
                true
            }
            R.id.action_top20 -> {
                val intent = Intent(this, YahtzeeTop20Activity::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}

class PlayerSelectionAdapter(
    private val players: List<Player>,
    private val selectedPlayers: Set<Player>,
    private val onCheckChanged: (Player, Boolean) -> Unit,
    private val onEditClick: (Player) -> Unit,
    private val onDeleteClick: (Player) -> Unit,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit
) : RecyclerView.Adapter<PlayerSelectionAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val checkbox: android.widget.CheckBox = view.findViewById(R.id.checkboxPlayer)
        val colorIndicator: View = view.findViewById(R.id.colorIndicator)
        val textName: android.widget.TextView = view.findViewById(R.id.textPlayerName)
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
        holder.checkbox.isChecked = player in selectedPlayers

        // Set player color
        val drawable = holder.colorIndicator.background as? GradientDrawable
        drawable?.setColor(player.color)

        holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
            onCheckChanged(player, isChecked)
        }

        holder.btnEdit.setOnClickListener {
            onEditClick(player)
        }

        holder.btnDelete.setOnClickListener {
            onDeleteClick(player)
        }

        holder.btnDrag.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                onStartDrag(holder)
            }
            false
        }
    }

    override fun getItemCount() = players.size
}

class ColorSelectionAdapter(
    private val colors: List<Int>,
    private val initialColor: Int? = null,
    private val onColorSelected: (Int) -> Unit
) : RecyclerView.Adapter<ColorSelectionAdapter.ViewHolder>() {

    private var selectedPosition = initialColor?.let { color ->
        colors.indexOf(color).takeIf { it >= 0 } ?: 0
    } ?: 0

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val colorCircle: View = view.findViewById(R.id.colorCircle)
        val checkIcon: android.widget.ImageView = view.findViewById(R.id.checkIcon)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_color_selection, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val color = colors[position]
        val drawable = holder.colorCircle.background as? GradientDrawable
        drawable?.setColor(color)

        holder.checkIcon.visibility = if (position == selectedPosition) View.VISIBLE else View.GONE

        holder.itemView.setOnClickListener {
            val oldPosition = selectedPosition
            selectedPosition = position
            notifyItemChanged(oldPosition)
            notifyItemChanged(selectedPosition)
            onColorSelected(color)
        }
    }

    override fun getItemCount() = colors.size
}
