package com.github.trivialloop.scorehub.games.oh_hell

import org.junit.Assert.*
import org.junit.Test

class OhHellScoreManagerTest {

    // ── totalRoundsForPlayers ─────────────────────────────

    @Test fun totalRounds_3players() = assertEquals(34, totalRoundsForPlayers(3))
    @Test fun totalRounds_4players() = assertEquals(24, totalRoundsForPlayers(4))
    @Test fun totalRounds_5players() = assertEquals(20, totalRoundsForPlayers(5))
    @Test fun totalRounds_6players() = assertEquals(16, totalRoundsForPlayers(6))
    @Test fun totalRounds_7players() = assertEquals(14, totalRoundsForPlayers(7))
    @Test fun totalRounds_8players() = assertEquals(12, totalRoundsForPlayers(8))

    // ── maxCardsForRound ──────────────────────────────────

    @Test
    fun maxCards_4players_ascending() {
        val total = totalRoundsForPlayers(4)
        assertEquals(1,  maxCardsForRound(1,  total))
        assertEquals(6,  maxCardsForRound(6,  total))
        assertEquals(12, maxCardsForRound(12, total))
    }

    @Test
    fun maxCards_4players_descending() {
        val total = totalRoundsForPlayers(4)
        assertEquals(12, maxCardsForRound(13, total))
        assertEquals(6,  maxCardsForRound(19, total))
        assertEquals(1,  maxCardsForRound(24, total))
    }

    @Test
    fun maxCards_symmetry_4players() {
        val total = totalRoundsForPlayers(4)
        for (r in 1..total) {
            val mirror = total - r + 1
            assertEquals(maxCardsForRound(r, total), maxCardsForRound(mirror, total))
        }
    }

    @Test
    fun maxCards_6players_two_central_rounds_equal() {
        val total = totalRoundsForPlayers(6) // 16
        // rounds 8 and 9 are the two central ones — both should be the peak
        assertEquals(maxCardsForRound(8, total), maxCardsForRound(9, total))
        assertEquals(8, maxCardsForRound(8, total))
    }

    // ── startPlayerIndexForRound / biddingOrderForRound ───

    @Test
    fun startPlayer_rotates_each_round() {
        assertEquals(0, startPlayerIndexForRound(1, 4))
        assertEquals(1, startPlayerIndexForRound(2, 4))
        assertEquals(2, startPlayerIndexForRound(3, 4))
        assertEquals(3, startPlayerIndexForRound(4, 4))
        assertEquals(0, startPlayerIndexForRound(5, 4))
    }

    @Test
    fun biddingOrder_starts_at_correct_player() {
        val order = biddingOrderForRound(2, 4)  // round 2 → start index 1
        assertEquals(listOf(1, 2, 3, 0), order)
    }

    @Test
    fun biddingOrder_covers_all_players() {
        val numPlayers = 5
        val order = biddingOrderForRound(3, numPlayers)
        assertEquals(numPlayers, order.distinct().size)
        assertTrue(order.containsAll((0 until numPlayers).toList()))
    }

    // ── OhHellRound.allowedContracts ──────────────────────

    private fun makeRound(
        maxCards: Int,
        startPlayerIndex: Int,
        playerIds: List<Long>,
        contractsSoFar: Map<Long, Int> = emptyMap()
    ): OhHellRound {
        val round = OhHellRound(
            roundNumber      = 1,
            maxCards         = maxCards,
            startPlayerIndex = startPlayerIndex
        )
        contractsSoFar.forEach { (id, v) -> round.contracts[id] = v }
        return round
    }

    @Test
    fun allowedContracts_notLastBidder_allValuesAllowed() {
        // 4 players (ids 0L..3L), startPlayer=0, maxCards=5
        // player 0 bids first → not the last bidder
        val ids = listOf(0L, 1L, 2L, 3L)
        val round = makeRound(maxCards = 5, startPlayerIndex = 0, playerIds = ids)
        val allowed = round.allowedContracts(playerIndex = 0, playerIds = ids)
        assertEquals((0..5).toList(), allowed)
    }

    @Test
    fun allowedContracts_lastBidder_excludesForbiddenValue() {
        // startPlayer=0, lastBidder=3 (index 3 = start-1 mod 4)
        // players 0,1,2 already bid 1,1,1 → sum=3; forbidden = 5-3 = 2
        val ids = listOf(0L, 1L, 2L, 3L)
        val round = makeRound(
            maxCards         = 5,
            startPlayerIndex = 0,
            playerIds        = ids,
            contractsSoFar   = mapOf(0L to 1, 1L to 1, 2L to 1)
        )
        val allowed = round.allowedContracts(playerIndex = 3, playerIds = ids)
        assertFalse(allowed.contains(2))
        assertTrue(allowed.containsAll(listOf(0, 1, 3, 4, 5)))
    }

    @Test
    fun allowedContracts_lastBidder_forbiddenNegative_allAllowed() {
        // sum of others already exceeds maxCards → no forbidden value
        val ids = listOf(0L, 1L, 2L, 3L)
        val round = makeRound(
            maxCards         = 3,
            startPlayerIndex = 0,
            playerIds        = ids,
            contractsSoFar   = mapOf(0L to 2, 1L to 2, 2L to 2)
        )
        val allowed = round.allowedContracts(playerIndex = 3, playerIds = ids)
        assertEquals((0..3).toList(), allowed)
    }

    // ── OhHellRound.forbiddenContractForLastBidder ────────

    @Test
    fun forbiddenContract_basic() {
        val ids = listOf(0L, 1L, 2L, 3L)
        val round = makeRound(
            maxCards         = 5,
            startPlayerIndex = 0,
            playerIds        = ids,
            contractsSoFar   = mapOf(0L to 1, 1L to 1, 2L to 1)
        )
        assertEquals(2, round.forbiddenContractForLastBidder(ids))
    }

    @Test
    fun forbiddenContract_zeroSumSoFar() {
        val ids = listOf(0L, 1L, 2L, 3L)
        val round = makeRound(
            maxCards         = 5,
            startPlayerIndex = 0,
            playerIds        = ids,
            contractsSoFar   = mapOf(0L to 0, 1L to 0, 2L to 0)
        )
        assertEquals(5, round.forbiddenContractForLastBidder(ids))
    }

    @Test
    fun forbiddenContract_noForbiddenWhenSumExceedsMax() {
        val ids = listOf(0L, 1L, 2L, 3L)
        val round = makeRound(
            maxCards         = 3,
            startPlayerIndex = 0,
            playerIds        = ids,
            contractsSoFar   = mapOf(0L to 2, 1L to 2, 2L to 2)
        )
        assertNull(round.forbiddenContractForLastBidder(ids))
    }

    // ── OhHellRound.getScore ──────────────────────────────

    private fun roundWithResult(
        contract: Int,
        crosses: Int,
        playerId: Long = 1L
    ): OhHellRound {
        val round = OhHellRound(roundNumber = 1, maxCards = 10, startPlayerIndex = 0)
        round.contracts[playerId] = contract
        round.results[playerId]   = crosses
        return round
    }

    @Test
    fun score_exactBid_zero() = assertEquals(5,  roundWithResult(0, 0).getScore(1L))
    @Test
    fun score_exactBid_three() = assertEquals(11, roundWithResult(3, 0).getScore(1L))
    @Test
    fun score_oneMiss() = assertEquals(-2, roundWithResult(2, 1).getScore(1L))
    @Test
    fun score_threeMiss() = assertEquals(-6, roundWithResult(5, 3).getScore(1L))
    @Test
    fun score_missByOne_bidZero() = assertEquals(-2, roundWithResult(0, 1).getScore(1L))

    @Test
    fun score_returnsNull_whenContractMissing() {
        val round = OhHellRound(roundNumber = 1, maxCards = 5, startPlayerIndex = 0)
        round.results[1L] = 0
        assertNull(round.getScore(1L))
    }

    @Test
    fun score_returnsNull_whenResultMissing() {
        val round = OhHellRound(roundNumber = 1, maxCards = 5, startPlayerIndex = 0)
        round.contracts[1L] = 2
        assertNull(round.getScore(1L))
    }

    // ── OhHellRound.getResultLabel ────────────────────────

    @Test
    fun resultLabel_success() = assertEquals("✅", roundWithResult(3, 0).getResultLabel(1L))
    @Test
    fun resultLabel_oneCross() = assertEquals("❌", roundWithResult(2, 1).getResultLabel(1L))
    @Test
    fun resultLabel_threeCrosses() = assertEquals("❌❌❌", roundWithResult(0, 3).getResultLabel(1L))

    @Test
    fun resultLabel_neverCompactNotation() {
        val label = roundWithResult(0, 6).getResultLabel(1L)
        assertFalse("Must not contain ×", label.contains("×"))
        assertEquals("❌❌❌❌❌❌", label)
    }

    @Test
    fun resultLabel_emptyWhenNotEntered() {
        val round = OhHellRound(roundNumber = 1, maxCards = 5, startPlayerIndex = 0)
        assertEquals("", round.getResultLabel(1L))
    }

    // ── OhHellRound.isComplete / isContractPhaseComplete ─

    @Test
    fun isComplete_falseWhenNoResults() {
        val ids = listOf(1L, 2L)
        val round = OhHellRound(roundNumber = 1, maxCards = 3, startPlayerIndex = 0)
        assertFalse(round.isComplete(ids))
    }

    @Test
    fun isComplete_trueWhenAllResultsEntered() {
        val ids = listOf(1L, 2L)
        val round = OhHellRound(roundNumber = 1, maxCards = 3, startPlayerIndex = 0)
        round.results[1L] = 0; round.results[2L] = 1
        assertTrue(round.isComplete(ids))
    }

    @Test
    fun contractPhaseComplete_trueWhenAllContractsEntered() {
        val ids = listOf(1L, 2L, 3L)
        val round = OhHellRound(roundNumber = 1, maxCards = 5, startPlayerIndex = 0)
        round.contracts[1L] = 1; round.contracts[2L] = 2; round.contracts[3L] = 0
        assertTrue(round.isContractPhaseComplete(ids))
    }

    // ── OhHellPlayerState.getTotal ────────────────────────

    @Test
    fun getTotal_emptyRounds() {
        val player = OhHellPlayerState(1L, "Alice", 0xFF0000)
        assertEquals(0, player.getTotal(emptyList()))
    }

    @Test
    fun getTotal_singleRoundSuccess() {
        val player = OhHellPlayerState(1L, "Alice", 0xFF0000)
        val round = roundWithResult(contract = 2, crosses = 0, playerId = 1L)
        // 5 + 2*2 = 9
        assertEquals(9, player.getTotal(listOf(round)))
    }

    @Test
    fun getTotal_twoRounds_mixedResults() {
        val player = OhHellPlayerState(1L, "Alice", 0xFF0000)
        val r1 = roundWithResult(contract = 1, crosses = 0, playerId = 1L) // +7
        val r2 = roundWithResult(contract = 2, crosses = 2, playerId = 1L) // -4
        assertEquals(3, player.getTotal(listOf(r1, r2)))
    }

    @Test
    fun getTotal_ignoresRoundsWithMissingData() {
        val player = OhHellPlayerState(1L, "Alice", 0xFF0000)
        val r1 = roundWithResult(contract = 3, crosses = 0, playerId = 1L) // +11
        val r2 = OhHellRound(roundNumber = 2, maxCards = 5, startPlayerIndex = 0) // no data → 0
        assertEquals(11, player.getTotal(listOf(r1, r2)))
    }
}
