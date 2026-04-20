package com.github.trivialloop.scorehub.games.yahtzee

data class YahtzeePlayerScore(
    val playerId: Long,
    val playerName: String,
    val playerColor: Int,
    val scores: MutableMap<YahtzeeCategory, Int?> = mutableMapOf()
) {
    fun getUpperTotal(): Int =
        (scores[YahtzeeCategory.ONES] ?: 0) + (scores[YahtzeeCategory.TWOS] ?: 0) +
        (scores[YahtzeeCategory.THREES] ?: 0) + (scores[YahtzeeCategory.FOURS] ?: 0) +
        (scores[YahtzeeCategory.FIVES] ?: 0) + (scores[YahtzeeCategory.SIXES] ?: 0)

    fun getBonus(): Int = if (getUpperTotal() >= 63) 35 else 0

    fun getBonusProgress(): Int = (63 - getUpperTotal()).coerceAtLeast(0)

    fun getLowerTotal(): Int =
        (scores[YahtzeeCategory.CHANCE] ?: 0) + (scores[YahtzeeCategory.THREE_OF_KIND] ?: 0) +
        (scores[YahtzeeCategory.FOUR_OF_KIND] ?: 0) + (scores[YahtzeeCategory.FULL_HOUSE] ?: 0) +
        (scores[YahtzeeCategory.SMALL_STRAIGHT] ?: 0) + (scores[YahtzeeCategory.LARGE_STRAIGHT] ?: 0) +
        (scores[YahtzeeCategory.YAHTZEE] ?: 0)

    fun getGrandTotal(): Int = getUpperTotal() + getBonus() + getLowerTotal()

    fun isComplete(): Boolean = YahtzeeCategory.values().all { scores[it] != null }
}

enum class YahtzeeCategory {
    ONES, TWOS, THREES, FOURS, FIVES, SIXES,
    CHANCE, THREE_OF_KIND, FOUR_OF_KIND, FULL_HOUSE,
    SMALL_STRAIGHT, LARGE_STRAIGHT, YAHTZEE;

    /**
     * Possible values for this category.
     * CHANCE keeps ascending order (5..30); the dialog scrolls to the bottom so the
     * user sees high values first without reversing the logical order.
     */
    fun getPossibleValues(): List<Int> = when (this) {
        ONES         -> listOf(0, 1, 2, 3, 4, 5)
        TWOS         -> listOf(0, 2, 4, 6, 8, 10)
        THREES       -> listOf(0, 3, 6, 9, 12, 15)
        FOURS        -> listOf(0, 4, 8, 12, 16, 20)
        FIVES        -> listOf(0, 5, 10, 15, 20, 25)
        SIXES        -> listOf(0, 6, 12, 18, 24, 30)
        CHANCE       -> (5..30).toList()
        THREE_OF_KIND -> listOf(0, 3, 6, 9, 12, 15, 18)
        FOUR_OF_KIND  -> listOf(0, 4, 8, 12, 16, 20, 24)
        FULL_HOUSE    -> listOf(0, 25)
        SMALL_STRAIGHT -> listOf(0, 30)
        LARGE_STRAIGHT -> listOf(0, 40)
        YAHTZEE       -> listOf(0, 50)
    }

    /** True if the score picker should scroll to the bottom on open (so large values are visible first). */
    fun scrollToBottom(): Boolean = this == CHANCE
}
