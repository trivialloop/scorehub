package com.github.trivialloop.scorehub.games.farkle

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.trivialloop.scorehub.R
import com.github.trivialloop.scorehub.data.AppDatabase
import com.github.trivialloop.scorehub.data.Player
import com.github.trivialloop.scorehub.databinding.ActivityFarkleStatsBinding
import com.github.trivialloop.scorehub.utils.LocaleHelper
import kotlinx.coroutines.launch

class FarkleStatsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFarkleStatsBinding
    private lateinit var database: AppDatabase

    companion object {
        const val GAME_TYPE = "farkle"
    }

    override fun attachBaseContext(newBase: Context) {
        val language = LocaleHelper.getPersistedLocale(newBase)
        super.attachBaseContext(LocaleHelper.setLocale(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFarkleStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())

            binding.appBarLayout.setPadding(
                0,
                statusBarInsets.top,
                0,
                0
            )

            insets
        }

        database = AppDatabase.getDatabase(this)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.game_statistics)

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        loadStats()
    }

    private fun loadStats() {
        lifecycleScope.launch {
            database.playerDao().getAllPlayers().collect { players ->
                val statsList = mutableListOf<FarklePlayerStatsEntry>()

                for (player in players) {
                    val totalGames = database.gameResultDao().getGamesPlayedByPlayer(player.id, GAME_TYPE)
                    if (totalGames > 0) {
                        val countedGames = database.gameResultDao().getCountedGamesPlayedByPlayer(player.id, GAME_TYPE)
                        val wins = database.gameResultDao().getWinsByPlayer(player.id, GAME_TYPE)
                        val draws = database.gameResultDao().getDrawsByPlayer(player.id, GAME_TYPE)
                        val losses = countedGames - wins - draws
                        val bestScore = database.gameResultDao().getBestScoreByPlayer(player.id, GAME_TYPE) ?: 0
                        val worstScore = database.gameResultDao().getWorstScoreByPlayer(player.id, GAME_TYPE) ?: 0

                        val winPct = if (countedGames > 0) wins * 100f / countedGames else 0f
                        val drawPct = if (countedGames > 0) draws * 100f / countedGames else 0f
                        val lossPct = if (countedGames > 0) losses * 100f / countedGames else 0f

                        statsList.add(
                            FarklePlayerStatsEntry(
                                player, totalGames, countedGames,
                                winPct, drawPct, lossPct, bestScore, worstScore
                            )
                        )
                    }
                }

                statsList.sortWith(
                    compareByDescending<FarklePlayerStatsEntry> { it.winPercentage }
                        .thenByDescending { it.drawPercentage }
                        .thenByDescending { (it.winPercentage / 100f * it.countedGames).toInt() }
                        .thenBy { (it.lossPercentage / 100f * it.countedGames).toInt() }
                        .thenByDescending { it.bestScore }
                        .thenByDescending { it.worstScore }
                        .thenBy { it.player.id }
                )

                binding.recyclerView.adapter = FarkleStatsAdapter(statsList)
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

data class FarklePlayerStatsEntry(
    val player: Player,
    val totalGames: Int,
    val countedGames: Int,
    val winPercentage: Float,
    val drawPercentage: Float,
    val lossPercentage: Float,
    val bestScore: Int,
    val worstScore: Int
)

class FarkleStatsAdapter(private val stats: List<FarklePlayerStatsEntry>) :
    RecyclerView.Adapter<FarkleStatsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textPosition: android.widget.TextView = view.findViewById(R.id.textPosition)
        val colorIndicator: View = view.findViewById(R.id.colorIndicator)
        val textPlayerName: android.widget.TextView = view.findViewById(R.id.textPlayerName)
        val textGamesInfo: android.widget.TextView = view.findViewById(R.id.textGamesInfo)
        val textBestScore: android.widget.TextView = view.findViewById(R.id.textBestScore)
        val textWorstScore: android.widget.TextView = view.findViewById(R.id.textWorstScore)
        val barWin: View = view.findViewById(R.id.barWin)
        val barDraw: View = view.findViewById(R.id.barDraw)
        val barLoss: View = view.findViewById(R.id.barLoss)
        val textWinPercentage: android.widget.TextView = view.findViewById(R.id.textWinPercentage)
        val textDrawPercentage: android.widget.TextView = view.findViewById(R.id.textDrawPercentage)
        val textLossPercentage: android.widget.TextView = view.findViewById(R.id.textLossPercentage)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_player_stats, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val stat = stats[position]
        val ctx = holder.itemView.context

        holder.textPosition.text = when (position) {
            0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> "${position + 1}"
        }
        (holder.colorIndicator.background as? GradientDrawable)?.setColor(stat.player.color)
        holder.textPlayerName.text = stat.player.name
        holder.textGamesInfo.text = ctx.getString(R.string.games_info, stat.totalGames, stat.countedGames)
        holder.textBestScore.text = ctx.getString(R.string.best_score_short, stat.bestScore)
        holder.textWorstScore.text = ctx.getString(R.string.worst_score_short, stat.worstScore)

        fun setWeight(view: View, w: Float) {
            (view.layoutParams as LinearLayout.LayoutParams).weight = w
            view.layoutParams = view.layoutParams
        }
        setWeight(holder.barWin, stat.winPercentage / 100f)
        setWeight(holder.barDraw, stat.drawPercentage / 100f)
        setWeight(holder.barLoss, stat.lossPercentage / 100f)

        holder.textWinPercentage.text = ctx.getString(R.string.win_percentage_short, stat.winPercentage)
        holder.textDrawPercentage.text = ctx.getString(R.string.draw_percentage_short, stat.drawPercentage)
        holder.textLossPercentage.text = ctx.getString(R.string.loss_percentage_short, stat.lossPercentage)
    }

    override fun getItemCount() = stats.size
}
