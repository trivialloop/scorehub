package com.github.trivialloop.scorehub.games.yahtzee

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.trivialloop.scorehub.R
import com.github.trivialloop.scorehub.data.AppDatabase
import com.github.trivialloop.scorehub.data.Player
import com.github.trivialloop.scorehub.databinding.ActivityYahtzeeStatsBinding
import com.github.trivialloop.scorehub.utils.LocaleHelper
import kotlinx.coroutines.launch

class YahtzeeStatsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityYahtzeeStatsBinding
    private lateinit var database: AppDatabase

    companion object {
        const val GAME_TYPE = "yahtzee"
    }

    override fun attachBaseContext(newBase: Context) {
        val language = LocaleHelper.getPersistedLocale(newBase)
        super.attachBaseContext(LocaleHelper.setLocale(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityYahtzeeStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
                val statsList = mutableListOf<PlayerStatsEntry>()

                for (player in players) {
                    val totalGames = database.gameResultDao()
                        .getGamesPlayedByPlayer(player.id, GAME_TYPE)

                    if (totalGames > 0) {
                        // Count only not solo games
                        val countedGames = database.gameResultDao()
                            .getCountedGamesPlayedByPlayer(player.id, GAME_TYPE)

                        val wins = database.gameResultDao()
                            .getWinsByPlayer(player.id, GAME_TYPE)
                        val draws = database.gameResultDao()
                            .getDrawsByPlayer(player.id, GAME_TYPE)
                        val losses = countedGames - wins - draws
                        val bestScore = database.gameResultDao()
                            .getBestScoreByPlayer(player.id, GAME_TYPE) ?: 0
                        val worstScore = database.gameResultDao()
                            .getWorstScoreByPlayer(player.id, GAME_TYPE) ?: 0

                        val winPercentage = if (countedGames > 0) (wins * 100f / countedGames) else 0f
                        val drawPercentage = if (countedGames > 0) (draws * 100f / countedGames) else 0f
                        val lossPercentage = if (countedGames > 0) (losses * 100f / countedGames) else 0f

                        statsList.add(
                            PlayerStatsEntry(
                                player = player,
                                totalGames = totalGames,
                                countedGames = countedGames,
                                winPercentage = winPercentage,
                                drawPercentage = drawPercentage,
                                lossPercentage = lossPercentage,
                                bestScore = bestScore,
                                worstScore = worstScore
                            )
                        )
                    }
                }

                statsList.sortWith(
                    compareByDescending<PlayerStatsEntry> { it.winPercentage }
                        .thenByDescending { it.drawPercentage }
                        .thenByDescending { (it.winPercentage / 100f * it.countedGames).toInt() } // Victory number
                        .thenBy { (it.lossPercentage / 100f * it.countedGames).toInt() } // Lose number
                        .thenByDescending { it.bestScore }
                        .thenByDescending { it.worstScore }
                        .thenBy { it.player.id }
                )

                binding.recyclerView.adapter = PlayerStatsAdapter(statsList)
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}

data class PlayerStatsEntry(
    val player: Player,
    val totalGames: Int,
    val countedGames: Int,
    val winPercentage: Float,
    val drawPercentage: Float,
    val lossPercentage: Float,
    val bestScore: Int,
    val worstScore: Int
)

class PlayerStatsAdapter(private val stats: List<PlayerStatsEntry>) :
    RecyclerView.Adapter<PlayerStatsAdapter.ViewHolder>() {

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
        val context = holder.itemView.context

        // Trophy for the 3 firsts players
        holder.textPosition.text = when (position) {
            0 -> "🥇"
            1 -> "🥈"
            2 -> "🥉"
            else -> "${position + 1}"
        }

        // Player color
        val drawable = holder.colorIndicator.background as? GradientDrawable
        drawable?.setColor(stat.player.color)

        // Name
        holder.textPlayerName.text = stat.player.name

        // Games info
        holder.textGamesInfo.text = context.getString(
            R.string.games_info,
            stat.totalGames,
            stat.countedGames
        )

        // Scores
        holder.textBestScore.text = context.getString(R.string.best_score_short, stat.bestScore)
        holder.textWorstScore.text = context.getString(R.string.worst_score_short, stat.worstScore)

        // Percentage bar
        val winWeight = stat.winPercentage / 100f
        val drawWeight = stat.drawPercentage / 100f
        val lossWeight = stat.lossPercentage / 100f

        val winParams = holder.barWin.layoutParams as LinearLayout.LayoutParams
        winParams.weight = winWeight
        holder.barWin.layoutParams = winParams

        val drawParams = holder.barDraw.layoutParams as LinearLayout.LayoutParams
        drawParams.weight = drawWeight
        holder.barDraw.layoutParams = drawParams

        val lossParams = holder.barLoss.layoutParams as LinearLayout.LayoutParams
        lossParams.weight = lossWeight
        holder.barLoss.layoutParams = lossParams

        // Percentage texts
        holder.textWinPercentage.text = context.getString(
            R.string.win_percentage_short,
            stat.winPercentage
        )
        holder.textDrawPercentage.text = context.getString(
            R.string.draw_percentage_short,
            stat.drawPercentage
        )
        holder.textLossPercentage.text = context.getString(
            R.string.loss_percentage_short,
            stat.lossPercentage
        )
    }

    override fun getItemCount() = stats.size
}
