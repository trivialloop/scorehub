package com.github.trivialloop.scorehub

import com.github.trivialloop.scorehub.games.akropolis.AkropolisPlayerSelectionActivity
import com.github.trivialloop.scorehub.games.cactus.CactusPlayerSelectionActivity
import com.github.trivialloop.scorehub.games.cribbage.CribbagePlayerSelectionActivity
import com.github.trivialloop.scorehub.games.escoba.EscobaPlayerSelectionActivity
import com.github.trivialloop.scorehub.games.farkle.FarklePlayerSelectionActivity
import com.github.trivialloop.scorehub.games.flip7.Flip7PlayerSelectionActivity
import com.github.trivialloop.scorehub.games.free_game.FreeGamePlayerSelectionActivity
import com.github.trivialloop.scorehub.games.oh_hell.OhHellPlayerSelectionActivity
import com.github.trivialloop.scorehub.games.skyjo.SkyjoPlayerSelectionActivity
import com.github.trivialloop.scorehub.games.tarot.TarotPlayerSelectionActivity
import com.github.trivialloop.scorehub.games.wingspan.WingspanPlayerSelectionActivity
import com.github.trivialloop.scorehub.games.yahtzee.YahtzeePlayerSelectionActivity

/**
 * Types of physical equipment required to play a game.
 *
 * [CARDS]  — a standard deck of cards (or game-specific cards that most people own).
 * [DICE]   — standard six-sided dice.
 * [BOARD]  — a proprietary board game box is required (cannot be improvised).
 */
enum class Equipment {
    CARDS,
    DICE,
    BOARD
}

/**
 * Central registry of all games supported by ScoreHub.
 *
 * **How to add a new game:**
 * 1. Add a [GameDefinition] entry to [ALL_GAMES] below — that is the ONLY change
 *    needed here. The home screen, search, sort, and filter all pick it up automatically.
 * 2. Create the game package under `games/<gamename>/` (see CLAUDE.md for the full
 *    checklist).
 */
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
        val activityClass: Class<*>,
        /** Minimum number of players supported. */
        val minPlayers: Int,
        /** Maximum number of players supported. */
        val maxPlayers: Int,
        /**
         * True if the game is played in teams.
         * False (the default) means every player plays for themselves.
         */
        val teamMode: Boolean = false,
        /** Physical equipment required to play. */
        val equipment: Set<Equipment>
    ) {
        /** Returns true if [playerCount] is within the supported range. */
        fun supportsPlayerCount(playerCount: Int): Boolean =
            playerCount in minPlayers..maxPlayers
    }

    /**
     * ════════════════════════════════════════════════════
     *  ADD NEW GAMES HERE
     * ════════════════════════════════════════════════════
     */
    val ALL_GAMES: List<GameDefinition> = listOf(
        GameDefinition(
            gameType       = "akropolis",
            nameEnFallback = "Akropolis",
            nameResId      = R.string.akropolis_game,
            iconResId      = R.drawable.ic_akropolis_game,
            activityClass  = AkropolisPlayerSelectionActivity::class.java,
            minPlayers     = 2,
            maxPlayers     = 4,
            teamMode       = false,
            equipment      = setOf(Equipment.BOARD)
        ),
        GameDefinition(
            gameType       = "cactus",
            nameEnFallback = "Cactus",
            nameResId      = R.string.cactus_game,
            iconResId      = R.drawable.ic_cactus_game,
            activityClass  = CactusPlayerSelectionActivity::class.java,
            minPlayers     = 2,
            maxPlayers     = 8,
            teamMode       = false,
            equipment      = setOf(Equipment.CARDS)
        ),
        GameDefinition(
            gameType       = "cribbage",
            nameEnFallback = "Cribbage",
            nameResId      = R.string.cribbage_game,
            iconResId      = R.drawable.ic_cribbage_game,
            activityClass  = CribbagePlayerSelectionActivity::class.java,
            minPlayers     = 2,
            maxPlayers     = 2,
            teamMode       = false,
            equipment      = setOf(Equipment.CARDS)
        ),
        GameDefinition(
            gameType       = "escoba",
            nameEnFallback = "Escoba",
            nameResId      = R.string.escoba_game,
            iconResId      = R.drawable.ic_escoba_game,
            activityClass  = EscobaPlayerSelectionActivity::class.java,
            minPlayers     = 2,
            maxPlayers     = 2,
            teamMode       = false,
            equipment      = setOf(Equipment.CARDS)
        ),
        GameDefinition(
            gameType       = "farkle",
            nameEnFallback = "Farkle",
            nameResId      = R.string.farkle_game,
            iconResId      = R.drawable.ic_farkle_game,
            activityClass  = FarklePlayerSelectionActivity::class.java,
            minPlayers     = 2,
            maxPlayers     = 8,
            teamMode       = false,
            equipment      = setOf(Equipment.DICE)
        ),
        GameDefinition(
            gameType       = "flip7",
            nameEnFallback = "Flip 7",
            nameResId      = R.string.flip7_game,
            iconResId      = R.drawable.ic_flip7_game,
            activityClass  = Flip7PlayerSelectionActivity::class.java,
            minPlayers     = 3,
            maxPlayers     = 10,
            teamMode       = false,
            equipment      = setOf(Equipment.BOARD)
        ),
        GameDefinition(
            gameType       = "free_game",
            nameEnFallback = "Free Game",
            nameResId      = R.string.free_game_game,
            iconResId      = R.drawable.ic_free_game_game,          // create a simple icon
            activityClass  = FreeGamePlayerSelectionActivity::class.java,
            minPlayers     = 1,
            maxPlayers     = 10,
            teamMode       = false,
            equipment      = setOf(Equipment.CARDS, Equipment.DICE, Equipment.BOARD)
        ),
        GameDefinition(
            gameType       = "oh_hell",
            nameEnFallback = "Oh Hell",
            nameResId      = R.string.oh_hell_game,
            iconResId      = R.drawable.ic_oh_hell_game,
            activityClass  = OhHellPlayerSelectionActivity::class.java,
            minPlayers     = 3,
            maxPlayers     = 8,
            teamMode       = false,
            equipment      = setOf(Equipment.CARDS)
        ),
        GameDefinition(
            gameType       = "skyjo",
            nameEnFallback = "Skyjo",
            nameResId      = R.string.skyjo_game,
            iconResId      = R.drawable.ic_skyjo_game,
            activityClass  = SkyjoPlayerSelectionActivity::class.java,
            minPlayers     = 2,
            maxPlayers     = 8,
            teamMode       = false,
            equipment      = setOf(Equipment.BOARD)
        ),
        GameDefinition(
            gameType       = "tarot",
            nameEnFallback = "Tarot",
            nameResId      = R.string.tarot_game,
            iconResId      = R.drawable.ic_tarot_game,
            activityClass  = TarotPlayerSelectionActivity::class.java,
            minPlayers     = 3,
            maxPlayers     = 5,
            teamMode       = false,
            equipment      = setOf(Equipment.BOARD)
        ),
        GameDefinition(
            gameType       = "wingspan",
            nameEnFallback = "Wingspan",
            nameResId      = R.string.wingspan_game,
            iconResId      = R.drawable.ic_wingspan_game,
            activityClass  = WingspanPlayerSelectionActivity::class.java,
            minPlayers     = 2,
            maxPlayers     = 5,
            teamMode       = false,
            equipment      = setOf(Equipment.BOARD)
        ),
        GameDefinition(
            gameType       = "yahtzee",
            nameEnFallback = "Yahtzee",
            nameResId      = R.string.yahtzee_game,
            iconResId      = R.drawable.ic_yahtzee_game,
            activityClass  = YahtzeePlayerSelectionActivity::class.java,
            minPlayers     = 1,
            maxPlayers     = 8,
            teamMode       = false,
            equipment      = setOf(Equipment.DICE)
        ),
    )

    /** Convenience lookup by gameType string. */
    fun findByType(gameType: String): GameDefinition? =
        ALL_GAMES.firstOrNull { it.gameType == gameType }
}
