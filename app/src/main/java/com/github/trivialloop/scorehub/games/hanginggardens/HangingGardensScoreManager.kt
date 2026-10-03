package com.github.trivialloop.scorehub.games.hanginggardens

/**
 * Hanging Gardens (Gigamic, 2025) scoring — one-shot tally per player (no rounds).
 *
 * The player computes each block from their physical garden and enters it directly.
 * Trees, animals and humans are entered one entry at a time (variable count per player).
 */
data class HangingGardensPlayerScore(
    val playerId: Long,
    val playerName: String,
    val playerColor: Int,
    var irrigation: Int? = null,
    val flowers: MutableMap<HangingGardensFlower, Int?> = mutableMapOf(),
    val treeEntries: MutableMap<HangingGardensTree, MutableList<Int>> =
        HangingGardensTree.entries.associateWith { mutableListOf<Int>() }.toMutableMap(),
    val animalEntries: MutableList<Int> = mutableListOf(),
    val humanEntries: MutableList<Int> = mutableListOf(),
    var objectives: Int? = null
) {
    fun getIrrigationTotal(): Int = irrigation ?: 0
    fun getFlowersTotal(): Int = HangingGardensFlower.entries.sumOf { flowers[it] ?: 0 }
    fun getTreeTotal(tree: HangingGardensTree): Int = treeEntries[tree]?.sum() ?: 0
    fun getTreesTotal(): Int = HangingGardensTree.entries.sumOf { getTreeTotal(it) }
    fun getAnimalsTotal(): Int = animalEntries.sum()
    fun getHumansTotal(): Int = humanEntries.sum()
    fun getObjectivesTotal(): Int = objectives ?: 0

    fun getTotal(): Int = getIrrigationTotal() + getFlowersTotal() + getTreesTotal() +
            getAnimalsTotal() + getHumansTotal() + getObjectivesTotal()
}

enum class HangingGardensFlower {
    BLUE, RED, YELLOW;

    /** Only the largest connected group of each color scores, 1 pt per flower. */
    fun getPossibleValues(): List<Int> = (0..24).toList()
}

enum class HangingGardensTree {
    DRAGON, CEDAR, PALM;

    /** Points available for one group of this tree species. */
    fun getPossibleValues(): List<Int> = when (this) {
        DRAGON -> listOf(2, 7)
        CEDAR  -> listOf(2, 5, 9)
        PALM   -> listOf(3, 7, 12, 18)
    }
}
val HANGING_GARDENS_IRRIGATION_POINTS: Map<Int, Int> = mapOf(
    1 to 1, 2 to 2, 3 to 4, 4 to 7, 5 to 10, 6 to 15
)

val HANGING_GARDENS_IRRIGATION_VALUES: List<Int> =
    listOf(0) + HANGING_GARDENS_IRRIGATION_POINTS.values.toList()

val HANGING_GARDENS_ANIMAL_VALUES: List<Int> = (0..30).toList()
val HANGING_GARDENS_HUMAN_VALUES: List<Int> = (0..30).toList()
val HANGING_GARDENS_OBJECTIVES_VALUES: List<Int> = (0..14).toList()
