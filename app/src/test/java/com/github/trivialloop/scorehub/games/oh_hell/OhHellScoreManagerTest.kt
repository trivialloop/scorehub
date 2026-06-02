package com.github.trivialloop.scorehub.games.oh_hell

import org.junit.Assert.*
import org.junit.Test

class OhHellScoreManagerTest {

    // ── totalRoundsForPlayers ─────────────────────────────

    @Test
    fun totalRounds_3players() = assertEquals(34, OhHellScoreManager.totalRoundsForPlayers(3))

    @Test
    fun totalRounds_4players() = assertEquals(24, OhHellScoreManager.totalRoundsForPlayers(4))

    @Test
    fun totalRounds_5players() = assertEquals(20, OhHellScoreManager.totalRoundsForPlayers(5))

    @Test
    fun totalRounds_6players() = assertEquals(16, OhHellScoreManager.totalRoundsForPlayers(6))

    @Test
    fun totalRounds_7players() = assertEquals(14, OhHellScoreManager.totalRoundsForPlayers(7))

    @Test
    fun totalRounds_8players() = assertEquals(12, OhHellScoreManager.totalRoundsForPlayers(8))

    // ── maxCardsForRound ──────────────────────────────────

    @Test
    fun maxCards_4players_ascending() {
        // 24 rounds, half=12 → rounds 1..12 ascend, 13..24 descend
        val total = OhHellScoreManager.totalRoundsForPlayers(4)
        assertEquals(1,  OhHellScoreManager.maxCardsForRound(1,  total))
        assertEquals(6,  OhHellScoreManager.maxCardsForRound(6,  total))
        assertEquals(12, OhHellScoreManager.maxCardsForRound(12, total))
    }

    @Test
    fun maxCards_4players_descending() {
        val total = OhHellScoreManager.totalRoundsForPlayers(4)
        assertEquals(12, OhHellScoreManager.maxCardsForRound(13, total))
        assertEquals(6,  OhHellScoreManager.maxCardsForRound(19, total))
        assertEquals(1,  OhHellScoreManager.maxCardsForRound(24, total))
    }

    @Test
    fun maxCards_symmetry_4players() {
        val total = OhHellScoreManager.totalRoundsForPlayers(4)
        for (r in 1..total) {
            val mirror = total - r + 1
            assertEquals(
                OhHellScoreManager.maxCardsForRound(r, total),
                OhHellScoreManager.maxCardsForRound(mirror, total)
            )
        }
    }

    @Test
    fun maxCards_6players_peak() {
        // 16 rounds, half=8 → peak is 8
        val total = OhHellScoreManager.totalRoundsForPlayers(6)
        assertEquals(8, OhHellScoreManager.maxCardsForRound(8, total))
        assertEquals(8, OhHellScoreManager.maxCardsForRound(9, total))
    }

    // ── allowedContracts / forbiddenContract ─────────────

    @Test
    fun allowedContracts_notLastBidder() {
        // Not last bidder → any value 0..maxCards is allowed
        val allowed = OhHellScoreManager.allowedContracts(
            maxCards = 5,
            sumBidsSoFar = 2,
            isLastBidder = false
        )
        assertEquals((0..5).toList(), allowed)
    }

    @Test
    fun allowedContracts_lastBidder_excludesForbidden() {
        // maxCards=5, sumSoFar=3 → forbidden=2 (5-3)
        val allowed = OhHellScoreManager.allowedContracts(
            maxCards = 5,
            sumBidsSoFar = 3,
            isLastBidder = true
        )
        assertFalse(allowed.contains(2))
        assertTrue(allowed.contains(0))
        assertTrue(allowed.contains(1))
        assertTrue(allowed.contains(3))
        assertTrue(allowed.contains(5))
    }

    @Test
    fun allowedContracts_lastBidder_forbiddenNegative_allAllowed() {
        // maxCards=3, sumSoFar=5 → forbidden would be -2 (impossible) → all allowed
        val allowed = OhHellScoreManager.allowedContracts(
            maxCards = 3,
            sumBidsSoFar = 5,
            isLastBidder = true
        )
        assertEquals((0..3).toList(), allowed)
    }

    @Test
    fun forbiddenContract_basic() {
        assertEquals(2, OhHellScoreManager.forbiddenContract(maxCards = 5, sumBidsSoFar = 3))
    }

    @Test
    fun forbiddenContract_zeroSoFar() {
        assertEquals(5, OhHellScoreManager.forbiddenContract(maxCards = 5, sumBidsSoFar = 0))
    }

    // ── getScore ──────────────────────────────────────────

    @Test
    fun score_exactBid_zero() {
        // bid=0, tricks=0 → success → 5 + 2*0 = 5
        assertEquals(5, OhHellScoreManager.getScore(bid = 0, tricksTaken = 0))
    }

    @Test
    fun score_exactBid_three() {
        // bid=3, tricks=3 → 5 + 2*3 = 11
        assertEquals(11, OhHellScoreManager.getScore(bid = 3, tricksTaken = 3))
    }

    @Test
    fun score_oneMiss() {
        // bid=2, tricks=3 → miss by 1 → -2*1 = -2
        assertEquals(-2, OhHellScoreManager.getScore(bid = 2, tricksTaken = 3))
    }

    @Test
    fun score_threeMiss() {
        // bid=5, tricks=2 → miss by 3 → -2*3 = -6
        assertEquals(-6, OhHellScoreManager.getScore(bid = 5, tricksTaken = 2))
    }

    @Test
    fun score_bidHighMiss() {
        // bid=0, tricks=1 → miss by 1 → -2
        assertEquals(-2, OhHellScoreManager.getScore(bid = 0, tricksTaken = 1))
    }

    // ── getResultLabel ────────────────────────────────────

    @Test
    fun resultLabel_success() {
        assertEquals("✅", OhHellScoreManager.getResultLabel(bid = 3, tricksTaken = 3))
    }

    @Test
    fun resultLabel_oneCross() {
        assertEquals("❌", OhHellScoreManager.getResultLabel(bid = 2, tricksTaken = 3))
    }

    @Test
    fun resultLabel_threeCrosses() {
        assertEquals("❌❌❌", OhHellScoreManager.getResultLabel(bid = 0, tricksTaken = 3))
    }

    @Test
    fun resultLabel_neverCompact() {
        // Must never produce formats like "❌×6"
        val label = OhHellScoreManager.getResultLabel(bid = 0, tricksTaken = 6)
        assertFalse(label.contains("×"))
        assertEquals("❌❌❌❌❌❌", label)
    }

    // ── isComplete ────────────────────────────────────────

    @Test
    fun isComplete_allRoundsPlayed() {
        val manager = OhHellScoreManager(playerCount = 4)
        val totalRounds = OhHellScoreManager.totalRoundsForPlayers(4)
        repeat(totalRounds) { round ->
            val maxCards = OhHellScoreManager.maxCardsForRound(round + 1, totalRounds)
            for (p in 0 until 4) {
                manager.setBid(round, p, 1.coerceAtMost(maxCards))
                manager.setTricks(round, p, 1.coerceAtMost(maxCards))
            }
        }
        assertTrue(manager.isComplete())
    }

    @Test
    fun isComplete_notFinished() {
        val manager = OhHellScoreManager(playerCount = 4)
        assertFalse(manager.isComplete())
    }

    // ── getTotal ──────────────────────────────────────────

    @Test
    fun getTotal_singleRoundSuccess() {
        val manager = OhHellScoreManager(playerCount = 3)
        // Round 0, player 0: bid=2, tricks=2 → score=9
        manager.setBid(0, 0, 2)
        manager.setTricks(0, 0, 2)
        assertEquals(9, manager.getTotal(0))
    }

    @Test
    fun getTotal_twoRounds_mixedResults() {
        val manager = OhHellScoreManager(playerCount = 3)
        // Round 0: bid=1, tricks=1 → +7
        manager.setBid(0, 0, 1)
        manager.setTricks(0, 0, 1)
        // Round 1: bid=2, tricks=0 → -4
        manager.setBid(1, 0, 2)
        manager.setTricks(1, 0, 0)
        assertEquals(3, manager.getTotal(0))
    }
}
