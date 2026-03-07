package com.github.trivialloop.scorehub

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.github.trivialloop.scorehub.data.AppDatabase
import com.github.trivialloop.scorehub.data.Player
import com.github.trivialloop.scorehub.databinding.ActivityGeneralStatsBinding
import com.github.trivialloop.scorehub.utils.LocaleHelper
import kotlinx.coroutines.launch

class GeneralStatsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityGeneralStatsBinding
    private lateinit var database: AppDatabase

    override fun attachBaseContext(newBase: Context) {
        val language = LocaleHelper.getPersistedLocale(newBase)
        super.attachBaseContext(LocaleHelper.setLocale(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGeneralStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = AppDatabase.getDatabase(this)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.general_statistics)

        loadGeneralStats()
    }

    private fun loadGeneralStats() {
        lifecycleScope.launch {
            var hasData = false

            // Best player first
            database.playerDao().getAllPlayers().collect { players ->
                data class PlayerRanking(
                    val player: Player,
                    val wins: Int,
                    val countedGames: Int,
                    val winPercentage: Float,
                    val drawPercentage: Float,
                    val bestScore: Int,
                    val worstScore: Int
                )

                val rankings = mutableListOf<PlayerRanking>()

                for (player in players) {
                    val countedGames = database.gameResultDao()
                        .getCountedGamesPlayedByPlayer(player.id, "yahtzee")

                    if (countedGames > 0) {
                        val wins = database.gameResultDao()
                            .getWinsByPlayer(player.id, "yahtzee")
                        val draws = database.gameResultDao()
                            .getDrawsByPlayer(player.id, "yahtzee")
                        val winPercentage = (wins * 100f / countedGames)
                        val drawPercentage = (draws * 100f / countedGames)
                        val bestScore = database.gameResultDao()
                            .getBestScoreByPlayer(player.id, "yahtzee") ?: 0
                        val worstScore = database.gameResultDao()
                            .getWorstScoreByPlayer(player.id, "yahtzee") ?: 0

                        rankings.add(
                            PlayerRanking(
                                player = player,
                                wins = wins,
                                countedGames = countedGames,
                                winPercentage = winPercentage,
                                drawPercentage = drawPercentage,
                                bestScore = bestScore,
                                worstScore = worstScore
                            )
                        )
                    }
                }

                // Same sorting as stats page
                rankings.sortWith(
                    compareByDescending<PlayerRanking> { it.winPercentage }
                        .thenByDescending { it.drawPercentage }
                        .thenByDescending { it.wins }
                        .thenBy { (it.countedGames - it.wins - (it.drawPercentage / 100f * it.countedGames).toInt()) }
                        .thenByDescending { it.bestScore }
                        .thenByDescending { it.worstScore }
                        .thenBy { it.player.id }
                )

                if (rankings.isNotEmpty()) {
                    hasData = true
                    val bestPlayer = rankings.first()
                    binding.textBestPlayer.text = "${bestPlayer.player.name}: ${bestPlayer.wins} ${getString(R.string.wins)} (${"%.1f".format(bestPlayer.winPercentage)}%)"

                    // Display player color as a circle
                    val drawable = GradientDrawable()
                    drawable.shape = GradientDrawable.OVAL
                    drawable.setColor(bestPlayer.player.color)
                    binding.bestPlayerColorIndicator.background = drawable
                    binding.bestPlayerColorIndicator.visibility = View.VISIBLE

                    binding.bestPlayerSection.visibility = View.VISIBLE
                } else {
                    binding.bestPlayerSection.visibility = View.GONE
                }

                // Best score second (top 1 from Top20)
                val top20 = database.gameResultDao().getTop20ByGameType("yahtzee")
                if (top20.isNotEmpty()) {
                    hasData = true
                    val bestResult = top20.first()
                    binding.textBestScore.text = "${bestResult.playerName}: ${bestResult.score} pts"

                    // Display color for player with best score
                    val bestScorePlayer = players.find { it.name == bestResult.playerName }
                    if (bestScorePlayer != null) {
                        val drawable = GradientDrawable()
                        drawable.shape = GradientDrawable.OVAL
                        drawable.setColor(bestScorePlayer.color)
                        binding.bestScoreColorIndicator.background = drawable
                        binding.bestScoreColorIndicator.visibility = View.VISIBLE
                    }

                    binding.bestScoreSection.visibility = View.VISIBLE
                } else {
                    binding.bestScoreSection.visibility = View.GONE
                }

                // Show or hide "no data" message
                if (!hasData) {
                    binding.textNoData.visibility = View.VISIBLE
                    binding.bestPlayerSection.visibility = View.GONE
                    binding.bestScoreSection.visibility = View.GONE
                } else {
                    binding.textNoData.visibility = View.GONE
                }
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
