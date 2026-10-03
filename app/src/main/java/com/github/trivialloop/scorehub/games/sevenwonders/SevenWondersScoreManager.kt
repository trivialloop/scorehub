package com.github.trivialloop.scorehub.games.sevenwonders

/**
 * 7 Wonders scoring — one-shot tally per player (no rounds), same family as
 * Akropolis / Wingspan / Harmonies: ScoreHub does not simulate card drafting; the
 * player computes each category from their physical city and Wonder board and
 * enters the subtotal directly.
 *
 * Categories (official scoring-sheet order):
 *  1. Military      — net sum of Victory/Defeat conflict tokens (+1/+3/+5 per age won, -1 per loss). Signed.
 *  2. Coins         — player enters raw coin count; getCoinPoints() = coins / 3 (rounded down).
 *  3. Wonder        — fixed points printed on the built Wonder stages. Single subtotal.
 *  4. Civilian (blue cards)      — sum of points printed on built blue cards. Single subtotal.
 *  5. Commerce (yellow cards)    — Age III commercial structures that grant points. Single subtotal.
 *  6. Guilds (purple cards)      — entered one card at a time, like Harmonies' Animal cards:
 *       each entry in [guildEntries] is the point value of one completed guild card, since a
 *       player can have anywhere from 0 to several guilds depending on the number of players.
 *  7. Science (green cards)      — 3 symbols: Compass, Gear, Tablet.
 *       score = compass² + gear² + tablet² + 7 × (number of complete sets of all three)
 *       where the number of complete sets = min(compass, gear, tablet).
 */
data class SevenWondersPlayerScore(
    val playerId: Long,
    val playerName: String,
    val playerColor: Int,
    var militaryPoints: Int? = null,   // signed: can be negative
    var coins: Int? = null,            // raw coin count, not victory points
    var wonderPoints: Int? = null,
    var civilianPoints: Int? = null,
    var commercePoints: Int? = null,
    val guildEntries: MutableList<Int> = mutableListOf(),
    var scienceCompass: Int? = null,
    var scienceGear: Int? = null,
    var scienceTablet: Int? = null
) {
    /** 1 victory point per 3 coins, rounded down. */
    fun getCoinPoints(): Int = (coins ?: 0) / 3

    /** Sum of all completed guild card values. */
    fun getGuildsTotal(): Int = guildEntries.sum()

    /**
     * Science score: each symbol type scores its count squared, plus 7 bonus points
     * per complete set of all three different symbols (set count = min of the three).
     */
    fun getScienceScore(): Int {
        val compass = scienceCompass ?: 0
        val gear = scienceGear ?: 0
        val tablet = scienceTablet ?: 0
        val sets = minOf(compass, gear, tablet)
        return compass * compass + gear * gear + tablet * tablet + 7 * sets
    }

    fun getTotal(): Int =
        (militaryPoints ?: 0) +
                getCoinPoints() +
                (wonderPoints ?: 0) +
                (civilianPoints ?: 0) +
                (commercePoints ?: 0) +
                getGuildsTotal() +
                getScienceScore()

    /**
     * True once every fixed category is entered. Guilds are excluded on purpose — an empty
     * guild list is a perfectly valid final state (same convention as Harmonies' Animal cards),
     * so completion is only meaningful for the score picker fields. The game itself ends via
     * the manual "Finish game" button, not an automatic completion check.
     */
    fun isComplete(): Boolean =
        militaryPoints != null &&
                coins != null &&
                wonderPoints != null &&
                civilianPoints != null &&
                commercePoints != null &&
                scienceCompass != null &&
                scienceGear != null &&
                scienceTablet != null
}

/** Selectable picker ranges for each category's dialog. */
object SevenWondersValues {
    val MILITARY_VALUES: List<Int> = (-6..18).toList()          // -6 (all losses) to +18 (all wins, all ages)
    val COINS_VALUES: List<Int> = (0..99).toList()
    val WONDER_VALUES: List<Int> = (0..40).toList()
    val CIVILIAN_VALUES: List<Int> = (0..60).toList()
    val COMMERCE_VALUES: List<Int> = (0..30).toList()
    val SCIENCE_SYMBOL_VALUES: List<Int> = (0..12).toList()      // per-symbol card count
    val GUILD_CARD_VALUES: List<Int> = (0..20).toList()          // single guild card's point value
}
