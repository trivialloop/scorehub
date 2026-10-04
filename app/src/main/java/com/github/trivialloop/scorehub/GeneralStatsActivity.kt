package com.github.trivialloop.scorehub

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.trivialloop.scorehub.data.AppDatabase
import com.github.trivialloop.scorehub.databinding.ActivityGeneralStatsBinding
import com.github.trivialloop.scorehub.utils.GeneralStatsCalculator
import com.github.trivialloop.scorehub.utils.GeneralStatsCalculator.GameStatsCard
import com.github.trivialloop.scorehub.utils.GeneralStatsCalculator.PlayerStatsCard
import com.github.trivialloop.scorehub.utils.LocaleHelper
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GeneralStatsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGeneralStatsBinding
    private lateinit var database: AppDatabase

    private var gameCards: List<GameStatsCard> = emptyList()
    private var playerCards: List<PlayerStatsCard> = emptyList()

    companion object {
        private const val TAB_GAMES = 0
        private const val TAB_PLAYERS = 1
    }

    override fun attachBaseContext(newBase: Context) {
        val language = LocaleHelper.getPersistedLocale(newBase)
        super.attachBaseContext(LocaleHelper.setLocale(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGeneralStatsBinding.inflate(layoutInflater)
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
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.general_statistics)

        binding.recyclerView.layoutManager = LinearLayoutManager(this)

        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.general_stats_tab_games))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.general_stats_tab_players))
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = showTab(tab.position)
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        loadStats()
    }

    private fun loadStats() {
        lifecycleScope.launch {
            val players   = database.playerDao().getAllPlayers().first()
            val dao       = database.gameResultDao()
            val stats     = dao.getAllPlayerGameStats()
            val summaries = dao.getGameSummaries()

            gameCards   = GeneralStatsCalculator.buildGameCards(GameRegistry.ALL_GAMES, players, stats, summaries)
            playerCards = GeneralStatsCalculator.buildPlayerCards(players, stats)
            showTab(binding.tabLayout.selectedTabPosition)
        }
    }

    private fun showTab(position: Int) {
        when (position) {
            TAB_PLAYERS -> {
                binding.recyclerView.adapter = GeneralPlayerAdapter(playerCards)
                binding.textEmpty.visibility = if (playerCards.isEmpty()) View.VISIBLE else View.GONE
            }
            else -> {
                binding.recyclerView.adapter = GeneralGameAdapter(gameCards)
                binding.textEmpty.visibility = if (gameCards.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }
}

private fun View.tintCircle(color: Int) {
    (background?.mutate() as? GradientDrawable)?.setColor(color)
}

// ─── "By game" adapter ───────────────────────────────────────────────────────

class GeneralGameAdapter(private val cards: List<GameStatsCard>) :
    RecyclerView.Adapter<GeneralGameAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.imageGameIcon)
        val name: TextView = view.findViewById(R.id.textGameName)
        val meta: TextView = view.findViewById(R.id.textGameMeta)
        val rowBestPlayer: View = view.findViewById(R.id.rowBestPlayer)
        val dotBestPlayer: View = view.findViewById(R.id.dotBestPlayer)
        val textBestPlayer: TextView = view.findViewById(R.id.textBestPlayer)
        val rowBestScore: View = view.findViewById(R.id.rowBestScore)
        val dotBestScore: View = view.findViewById(R.id.dotBestScore)
        val textBestScore: TextView = view.findViewById(R.id.textBestScore)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_general_game, parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val card = cards[position]
        val ctx = holder.itemView.context

        holder.icon.setImageResource(card.definition.iconResId)
        holder.name.text = ctx.getString(card.definition.nameResId)

        val date = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(card.lastPlayedAt))
        holder.meta.text = listOf(
            ctx.resources.getQuantityString(R.plurals.games_count, card.sessions, card.sessions),
            ctx.getString(R.string.last_played_date, date)
        ).joinToString("  •  ")

        val best = card.bestPlayer
        holder.rowBestPlayer.visibility = if (best != null) View.VISIBLE else View.GONE
        if (best != null) {
            holder.dotBestPlayer.tintCircle(best.player.color)
            holder.textBestPlayer.text = "${best.player.name}: ${best.wins} ${ctx.getString(R.string.wins)} " +
                    "(${"%.1f".format(best.winPercentage)}%)"
        }

        val score = card.bestScore
        holder.rowBestScore.visibility = if (score != null) View.VISIBLE else View.GONE
        if (score != null) {
            holder.dotBestScore.tintCircle(score.player.color)
            holder.textBestScore.text = "${score.player.name}: ${score.score} pts"
        }
    }

    override fun getItemCount() = cards.size
}

// ─── "By player" adapter ─────────────────────────────────────────────────────

class GeneralPlayerAdapter(private val cards: List<PlayerStatsCard>) :
    RecyclerView.Adapter<GeneralPlayerAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val position: TextView = view.findViewById(R.id.textPosition)
        val color: View = view.findViewById(R.id.colorIndicator)
        val name: TextView = view.findViewById(R.id.textPlayerName)
        val summary: TextView = view.findViewById(R.id.textSummary)
        val barWin: View = view.findViewById(R.id.barWin)
        val barDraw: View = view.findViewById(R.id.barDraw)
        val barLoss: View = view.findViewById(R.id.barLoss)
        val winRate: TextView = view.findViewById(R.id.textWinRate)
        val gamesContainer: LinearLayout = view.findViewById(R.id.gamesContainer)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_general_player, parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val card = cards[position]
        val ctx = holder.itemView.context

        holder.position.text = when (position) {
            0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> "${position + 1}"
        }
        holder.color.tintCircle(card.player.color)
        holder.name.text = card.player.name
        holder.summary.text = listOf(
            ctx.resources.getQuantityString(R.plurals.games_count, card.totalGames, card.totalGames),
            "${card.wins} ${ctx.getString(R.string.wins)}"
        ).joinToString("  •  ")

        fun setWeight(view: View, w: Float) {
            (view.layoutParams as LinearLayout.LayoutParams).weight = w
            view.layoutParams = view.layoutParams
        }
        val counted = card.countedGames.coerceAtLeast(1).toFloat()
        setWeight(holder.barWin, card.wins / counted)
        setWeight(holder.barDraw, card.draws / counted)
        setWeight(holder.barLoss, card.losses / counted)
        holder.winRate.text = ctx.getString(R.string.win_percentage_short, card.winPercentage)

        // One row per game played
        holder.gamesContainer.removeAllViews()
        val inflater = LayoutInflater.from(ctx)
        for (line in card.lines) {
            val row = inflater.inflate(R.layout.item_general_player_game_row, holder.gamesContainer, false)
            row.findViewById<ImageView>(R.id.imageGameIcon).setImageResource(line.definition.iconResId)
            row.findViewById<TextView>(R.id.textGameName).text = ctx.getString(line.definition.nameResId)
            row.findViewById<TextView>(R.id.textRatio).text =
                if (line.countedGames > 0) "${line.wins}/${line.countedGames}"
                else ctx.resources.getQuantityString(R.plurals.games_count, line.totalGames, line.totalGames)
            row.findViewById<TextView>(R.id.textPercentage).text =
                if (line.countedGames > 0) "%.0f%%".format(line.winPercentage) else ""
            holder.gamesContainer.addView(row)
        }
    }

    override fun getItemCount() = cards.size
}
