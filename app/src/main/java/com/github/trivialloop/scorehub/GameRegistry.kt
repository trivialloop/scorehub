package com.github.trivialloop.scorehub

import com.github.trivialloop.scorehub.games.cactus.CactusPlayerSelectionActivity
import com.github.trivialloop.scorehub.games.cribbage.CribbagePlayerSelectionActivity
import com.github.trivialloop.scorehub.games.escoba.EscobaPlayerSelectionActivity
import com.github.trivialloop.scorehub.games.skyjo.SkyjoPlayerSelectionActivity
import com.github.trivialloop.scorehub.games.yahtzee.YahtzeePlayerSelectionActivity
import com.github.trivialloop.scorehub.games.wingspan.WingspanPlayerSelectionActivity

object GameRegistry {

    data class GameDefinition(
        /** DB key stored in [com.github.trivialloop.scorehub.data.GameResult.gameType]. */
        val gameType: String,
        /** English name — used for locale-independent search. */
        val nameEnFallback: String,
        /** String resource id for the localised display name. */
        val nameResId: Int,
        /** Drawable resource id for the game icon. */
        val iconResId: Int,
        /** Activity that handles player selection / game launch. */
        val activityClass: Class<*>
    )

    /**
     * ════════════════════════════════════════════════════
     *  ADD NEW GAMES HERE
     * ════════════════════════════════════════════════════
     */
    val ALL_GAMES: List<GameDefinition> = listOf(
        GameDefinition(
            gameType        = "cactus",
            nameEnFallback  = "Cactus",
            nameResId       = R.string.cactus_game,
            iconResId       = R.drawable.ic_cactus_game,
            activityClass   = CactusPlayerSelectionActivity::class.java
        ),
        GameDefinition(
            gameType        = "cribbage",
            nameEnFallback  = "Cribbage",
            nameResId       = R.string.cribbage_game,
            iconResId       = R.drawable.ic_cribbage_game,
            activityClass   = CribbagePlayerSelectionActivity::class.java
        ),
        GameDefinition(
            gameType        = "escoba",
            nameEnFallback  = "Escoba",
            nameResId       = R.string.escoba_game,
            iconResId       = R.drawable.ic_escoba_game,
            activityClass   = EscobaPlayerSelectionActivity::class.java
        ),
        GameDefinition(
            gameType        = "skyjo",
            nameEnFallback  = "Skyjo",
            nameResId       = R.string.skyjo_game,
            iconResId       = R.drawable.ic_skyjo_game,
            activityClass   = SkyjoPlayerSelectionActivity::class.java
        ),
        GameDefinition(
            gameType        = "wingspan",
            nameEnFallback  = "Wingspan",
            nameResId       = R.string.wingspan_game,
            iconResId       = R.drawable.ic_wingspan_game,
            activityClass   = WingspanPlayerSelectionActivity::class.java
        ),
        GameDefinition(
            gameType        = "yahtzee",
            nameEnFallback  = "Yahtzee",
            nameResId       = R.string.yahtzee_game,
            iconResId       = R.drawable.ic_yahtzee_game,
            activityClass   = YahtzeePlayerSelectionActivity::class.java
        ),
    )

    /** Convenience lookup by gameType string. */
    fun findByType(gameType: String): GameDefinition? =
        ALL_GAMES.firstOrNull { it.gameType == gameType }
}
