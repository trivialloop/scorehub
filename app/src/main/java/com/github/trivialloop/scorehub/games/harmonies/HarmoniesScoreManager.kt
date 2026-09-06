package com.github.trivialloop.scorehub.games.harmonies

/**
 * Harmonies (Libellud, 2024) scoring — one-shot tally per player (no rounds).
 *
 * Like Akropolis / Wingspan, ScoreHub does not simulate the personal board itself: the player
 * computes each category's subtotal from their physical board and enters it directly.
 *
 * Trees, Mountains, Fields, Buildings and River are each a single subtotal (see
 * [HarmoniesCategory]). Animal cards are entered one at a time — like Ticket to Ride's
 * destination tickets — since a player can complete anywhere from 0 to several cards (including
 * the optional Nature's Spirit card); each entry in [animalEntries] is the point value of one
 * completed card.
 */
data class HarmoniesPlayerScore(
    val playerId: Long,
    val playerName: String,
    val playerColor: Int,
    val scores: MutableMap<HarmoniesCategory, Int?> = mutableMapOf(),
    val animalEntries: MutableList<Int> = mutableListOf()
) {
    /** Sum of the 5 fixed categories (Trees, Mountains, Fields, Buildings, River). */
    fun getCategoryTotal(): Int = HarmoniesCategory.entries.sumOf { scores[it] ?: 0 }

    /** Sum of all completed Animal card scores. */
    fun getAnimalsTotal(): Int = animalEntries.sum()

    /** Final score: fixed categories + animal cards. */
    fun getTotal(): Int = getCategoryTotal() + getAnimalsTotal()
}

enum class HarmoniesCategory {
    TREES,
    MOUNTAINS,
    FIELDS,
    BUILDINGS,
    RIVER;

    /** Selectable values for the picker dialog for this category. */
    fun getPossibleValues(): List<Int> = when (this) {
        TREES, MOUNTAINS -> (0..50).toList()
        FIELDS, BUILDINGS -> (0..50 step 5).toList()
        RIVER -> (0..40).toList()
    }
}

/** Selectable point values when adding a single Animal card score. */
val HARMONIES_ANIMAL_CARD_VALUES: List<Int> = (0..30).toList()
