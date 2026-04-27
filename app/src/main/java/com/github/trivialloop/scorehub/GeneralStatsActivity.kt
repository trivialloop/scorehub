package com.github.trivialloop.scorehub

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.github.trivialloop.scorehub.data.AppDatabase
import com.github.trivialloop.scorehub.data.Player
import com.github.trivialloop.scorehub.databinding.ActivityGeneralStatsBinding
import com.github.trivialloop.scorehub.utils.LocaleHelper
import kotlinx.coroutines.launch

class GeneralStatsActivity : BaseActivity() {
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
            database.playerDao().getAllPlayers().collect { players ->

                // ── Cactus ────────────────────────────────────────────────
                loadGameStats(
                    gameType          = "cactus",
                    players           = players,
                    bestPlayerView    = binding.textBestPlayerCactus,
                    bestPlayerColor   = binding.bestPlayerColorIndicatorCactus,
                    bestPlayerSection = binding.bestPlayerSectionCactus,
                    bestScoreView     = binding.textBestScoreCactus,
                    bestScoreColor    = binding.bestScoreColorIndicatorCactus,
                    bestScoreSection  = binding.bestScoreSectionCactus,
                    noDataView        = binding.textNoDataCactus,
                    bestScoreIsLowest = false   // highest score wins in Cactus
                )

                // ── Cribbage ──────────────────────────────────────────────
                loadGameStats(
                    gameType          = "cribbage",
                    players           = players,
                    bestPlayerView    = binding.textBestPlayerCribbage,
                    bestPlayerColor   = binding.bestPlayerColorIndicatorCribbage,
                    bestPlayerSection = binding.bestPlayerSectionCribbage,
                    bestScoreView     = binding.textBestScoreCribbage,
                    bestScoreColor    = binding.bestScoreColorIndicatorCribbage,
                    bestScoreSection  = binding.bestScoreSectionCribbage,
                    noDataView        = binding.textNoDataCribbage,
                    bestScoreIsLowest = false   // highest score wins in Cribbage
                )

                // ── Escoba ────────────────────────────────────────────────
                loadGameStats(
                    gameType          = "escoba",
                    players           = players,
                    bestPlayerView    = binding.textBestPlayerEscoba,
                    bestPlayerColor   = binding.bestPlayerColorIndicatorEscoba,
                    bestPlayerSection = binding.bestPlayerSectionEscoba,
                    bestScoreView     = binding.textBestScoreEscoba,
                    bestScoreColor    = binding.bestScoreColorIndicatorEscoba,
                    bestScoreSection  = binding.bestScoreSectionEscoba,
                    noDataView        = binding.textNoDataEscoba,
                    bestScoreIsLowest = false
                )

                // ── Skyjo ─────────────────────────────────────────────────
                loadGameStats(
                    gameType          = "skyjo",
                    players           = players,
                    bestPlayerView    = binding.textBestPlayerSkyjo,
                    bestPlayerColor   = binding.bestPlayerColorIndicatorSkyjo,
                    bestPlayerSection = binding.bestPlayerSectionSkyjo,
                    bestScoreView     = binding.textBestScoreSkyjo,
                    bestScoreColor    = binding.bestScoreColorIndicatorSkyjo,
                    bestScoreSection  = binding.bestScoreSectionSkyjo,
                    noDataView        = binding.textNoDataSkyjo,
                    bestScoreIsLowest = true
                )

                // ── Tarot ─────────────────────────────────────────────────────
                loadGameStats(
                    gameType          = "tarot",
                    players           = players,
                    bestPlayerView    = binding.textBestPlayerTarot,
                    bestPlayerColor   = binding.bestPlayerColorIndicatorTarot,
                    bestPlayerSection = binding.bestPlayerSectionTarot,
                    bestScoreView     = binding.textBestScoreTarot,
                    bestScoreColor    = binding.bestScoreColorIndicatorTarot,
                    bestScoreSection  = binding.bestScoreSectionTarot,
                    noDataView        = binding.textNoDataTarot,
                    bestScoreIsLowest = false
                )

                // ── Wingspan ──────────────────────────────────────────────
                loadGameStats(
                    gameType          = "wingspan",
                    players           = players,
                    bestPlayerView    = binding.textBestPlayerWingspan,
                    bestPlayerColor   = binding.bestPlayerColorIndicatorWingspan,
                    bestPlayerSection = binding.bestPlayerSectionWingspan,
                    bestScoreView     = binding.textBestScoreWingspan,
                    bestScoreColor    = binding.bestScoreColorIndicatorWingspan,
                    bestScoreSection  = binding.bestScoreSectionWingspan,
                    noDataView        = binding.textNoDataWingspan,
                    bestScoreIsLowest = false
                )

                // ── Yahtzee ───────────────────────────────────────────────
                loadGameStats(
                    gameType          = "yahtzee",
                    players           = players,
                    bestPlayerView    = binding.textBestPlayerYahtzee,
                    bestPlayerColor   = binding.bestPlayerColorIndicatorYahtzee,
                    bestPlayerSection = binding.bestPlayerSectionYahtzee,
                    bestScoreView     = binding.textBestScoreYahtzee,
                    bestScoreColor    = binding.bestScoreColorIndicatorYahtzee,
                    bestScoreSection  = binding.bestScoreSectionYahtzee,
                    noDataView        = binding.textNoDataYahtzee,
                    bestScoreIsLowest = false
                )
            }
        }
    }

    private suspend fun loadGameStats(
        gameType: String,
        players: List<Player>,
        bestPlayerView: android.widget.TextView,
        bestPlayerColor: View,
        bestPlayerSection: View,
        bestScoreView: android.widget.TextView,
        bestScoreColor: View,
        bestScoreSection: View,
        noDataView: android.widget.TextView,
        bestScoreIsLowest: Boolean
    ) {
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
                .getCountedGamesPlayedByPlayer(player.id, gameType)
            if (countedGames > 0) {
                val wins  = database.gameResultDao().getWinsByPlayer(player.id, gameType)
                val draws = database.gameResultDao().getDrawsByPlayer(player.id, gameType)
                val best  = database.gameResultDao().getBestScoreByPlayer(player.id, gameType) ?: 0
                val worst = database.gameResultDao().getWorstScoreByPlayer(player.id, gameType) ?: 0
                rankings.add(
                    PlayerRanking(
                        player         = player,
                        wins           = wins,
                        countedGames   = countedGames,
                        winPercentage  = wins * 100f / countedGames,
                        drawPercentage = draws * 100f / countedGames,
                        bestScore      = best,
                        worstScore     = worst
                    )
                )
            }
        }

        // Same ranking logic as stats screens
        rankings.sortWith(
            compareByDescending<PlayerRanking> { it.winPercentage }
                .thenByDescending { it.drawPercentage }
                .thenByDescending { it.wins }
                .thenBy { (it.countedGames - it.wins - (it.drawPercentage / 100f * it.countedGames).toInt()) }
                .thenByDescending { it.bestScore }
                .thenByDescending { it.worstScore }
                .thenBy { it.player.id }
        )

        val hasData = rankings.isNotEmpty()

        if (hasData) {
            // Best player (most wins / best ratio)
            val best = rankings.first()
            bestPlayerView.text = "${best.player.name}: ${best.wins} ${getString(R.string.wins)} (${"%.1f".format(best.winPercentage)}%)"
            bestPlayerColor.background = ovalDrawable(best.player.color)
            bestPlayerColor.visibility = View.VISIBLE
            bestPlayerSection.visibility = View.VISIBLE

            // Best score
            val top20 = database.gameResultDao().getTop20ByGameType(gameType)
            if (top20.isNotEmpty()) {
                // Yahtzee → first entry is highest; Skyjo → last entry is lowest (sorted DESC)
                val bestResult = if (bestScoreIsLowest) top20.minByOrNull { it.score }!! else top20.first()
                bestScoreView.text = "${bestResult.playerName}: ${bestResult.score} pts"
                val scorePlayer = players.find { it.id == bestResult.playerId }
                if (scorePlayer != null) {
                    bestScoreColor.background = ovalDrawable(scorePlayer.color)
                    bestScoreColor.visibility = View.VISIBLE
                }
                bestScoreSection.visibility = View.VISIBLE
            } else {
                bestScoreSection.visibility = View.GONE
            }

            noDataView.visibility = View.GONE
        } else {
            bestPlayerSection.visibility = View.GONE
            bestScoreSection.visibility  = View.GONE
            noDataView.visibility        = View.VISIBLE
        }
    }

    private fun ovalDrawable(color: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
