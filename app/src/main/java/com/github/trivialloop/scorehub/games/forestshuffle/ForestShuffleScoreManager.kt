package com.github.trivialloop.scorehub.games.forestshuffle

/**
 * The 8 base-game tree species, with the maximum number of copies of that
 * species present in the deck (used to bound the dropdown pickers in the UI).
 */
enum class TreeSpecies(val maxCount: Int) {
    BEECH(10),
    BIRCH(10),
    DOUGLAS_FIR(7),
    HORSE_CHESTNUT(11),
    LINDEN(9),
    OAK(7),
    SILVER_FIR(6),
    SYCAMORE(6)
}

/**
 * One player's end-of-game score sheet for Forest Shuffle.
 *
 * Forest Shuffle is a one-shot scoring game (no rounds) — the whole score is
 * entered once at the end of the game, mirroring the official scorepad which
 * splits the forest into: Trees / Top-Bottom dwellers / Left-Right dwellers / Cave.
 *
 * Trees are broken down per species because most tree scoring formulas only
 * depend on counters already present in this same sheet (own species counts,
 * total tree count, or other players' Linden counts) — see [ForestShuffleScoring].
 *
 * Top/Bottom and Left/Right dwellers are NOT broken down per species: there are
 * ~35-45 unique creature/plant/mushroom cards, many of which score based on
 * physical board state (adjacency, tree occupancy) or cross-category symbol
 * counts that aren't tracked here. Maintaining a full card database was
 * explicitly ruled out, so these two categories are manual subtotal entry —
 * the player computes them using the physical reference cards, same as they
 * would with a paper scorepad.
 */
data class ForestShufflePlayerScore(
    val playerId: Long,
    val playerName: String,
    val playerColor: Int,
    val treeCounts: MutableMap<TreeSpecies, Int?> = mutableMapOf(),
    /** Tree saplings (generic trees played face down). Count toward total tree count, not toward any species. */
    var saplings: Int? = null,
    /** Total number of dweller cards attached across ALL Silver Fir trees (not the number of Silver Firs). */
    var silverFirAttachedCards: Int? = null,
    /** Manual subtotal for all Top/Bottom dweller cards. */
    var topBottomPoints: Int? = null,
    /** Manual subtotal for all Left/Right dweller cards. */
    var leftRightPoints: Int? = null,
    /** Number of cards stored under the Cave card (each worth 1 point). */
    var caveCards: Int? = null
) {
    /** Total trees in the forest: sum of all 8 species + saplings. Saplings don't count as a species. */
    fun totalTreeCount(): Int = TreeSpecies.entries.sumOf { treeCounts[it] ?: 0 } + (saplings ?: 0)

    /** Number of distinct tree species actually played (0 to 8). */
    fun distinctSpeciesCount(): Int = TreeSpecies.entries.count { (treeCounts[it] ?: 0) > 0 }

    /** Cave score: 1 point per stored card. */
    fun caveScore(): Int = caveCards ?: 0

    /** True once every field on the sheet has been filled in (0 is a valid, explicit entry). */
    fun isComplete(): Boolean =
        TreeSpecies.entries.all { treeCounts[it] != null } &&
                saplings != null &&
                silverFirAttachedCards != null &&
                topBottomPoints != null &&
                leftRightPoints != null &&
                caveCards != null
}

/**
 * Pure scoring functions for Forest Shuffle trees, kept separate from the data
 * holder so they can be unit tested without any Android dependency.
 */
object ForestShuffleScoring {

    /**
     * Score contributed by a single tree species for one player.
     *
     * Rules (base game):
     *  - Birch: 1 pt / card.
     *  - Douglas Fir: 5 pts / card.
     *  - Beech: 5 pts / card, but ONLY if the player has 4 or more Beeches (else 0).
     *  - Horse Chestnut: n² points, where n = number of Horse Chestnuts.
     *  - Oak: 10 pts / card, but ONLY if the player's forest contains all 8 tree species.
     *  - Sycamore: 1 pt per tree in the player's forest (all species + saplings, itself included).
     *  - Linden: 3 pts / card if the player has the most Lindens of any player (ties included),
     *            otherwise 1 pt / card.
     *  - Silver Fir: not based on its own count — 2 pts per dweller card attached to a Silver Fir,
     *            tracked separately in [ForestShufflePlayerScore.silverFirAttachedCards].
     */
    fun speciesScore(
        species: TreeSpecies,
        playerScore: ForestShufflePlayerScore,
        allPlayers: List<ForestShufflePlayerScore>
    ): Int {
        val count = playerScore.treeCounts[species] ?: 0

        if (species == TreeSpecies.SILVER_FIR) {
            return (playerScore.silverFirAttachedCards ?: 0) * 2
        }
        if (count == 0) return 0

        return when (species) {
            TreeSpecies.BIRCH -> count * 1
            TreeSpecies.DOUGLAS_FIR -> count * 5
            TreeSpecies.BEECH -> if (count >= 4) count * 5 else 0
            TreeSpecies.HORSE_CHESTNUT -> count * count
            TreeSpecies.OAK -> if (playerScore.distinctSpeciesCount() >= 8) count * 10 else 0
            TreeSpecies.SYCAMORE -> count * playerScore.totalTreeCount()
            TreeSpecies.LINDEN -> {
                val maxLinden = allPlayers.maxOfOrNull { it.treeCounts[TreeSpecies.LINDEN] ?: 0 } ?: 0
                if (maxLinden > 0 && count == maxLinden) count * 3 else count * 1
            }
            TreeSpecies.SILVER_FIR -> 0 // unreachable, handled above
        }
    }

    /** Sum of all 8 tree species scores for a player. */
    fun treesTotal(playerScore: ForestShufflePlayerScore, allPlayers: List<ForestShufflePlayerScore>): Int =
        TreeSpecies.entries.sumOf { speciesScore(it, playerScore, allPlayers) }

    /** Full end-of-game score: Trees + Top/Bottom + Left/Right + Cave. */
    fun grandTotal(playerScore: ForestShufflePlayerScore, allPlayers: List<ForestShufflePlayerScore>): Int =
        treesTotal(playerScore, allPlayers) +
                (playerScore.topBottomPoints ?: 0) +
                (playerScore.leftRightPoints ?: 0) +
                playerScore.caveScore()
}
