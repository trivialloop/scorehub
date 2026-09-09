package com.github.trivialloop.scorehub.games.belote

import org.junit.Test
import org.junit.Assert.*

class BeloteScoreManagerTest {

    // ─── Contract succeeded ────────────────────────────────────────────────────

    @Test
    fun `attacking team succeeds - scores points made, defense scores complement`() {
        val round = BeloteRound(1, attackingTeam = 0, pointsMade = 100)
        val (scores, carry) = round.computeScores(litigeCarry = 0)
        assertEquals(100, scores[0])
        assertEquals(62, scores[1])
        assertEquals(0, carry)
    }

    @Test
    fun `scores sum to 162 when contract succeeds without belote`() {
        val round = BeloteRound(1, attackingTeam = 1, pointsMade = 90)
        val (scores, _) = round.computeScores(0)
        assertEquals(162, (scores[0] ?: 0) + (scores[1] ?: 0))
    }

    @Test
    fun `pending litige carry is added to a successful attacker`() {
        val round = BeloteRound(2, attackingTeam = 0, pointsMade = 90)
        val (scores, carry) = round.computeScores(litigeCarry = 81)
        assertEquals(171, scores[0])   // 90 + 81 carried
        assertEquals(72, scores[1])
        assertEquals(0, carry)
    }

    // ─── Contract failed (chute) ───────────────────────────────────────────────

    @Test
    fun `attacking team fails - scores 0, defense scores 162`() {
        val round = BeloteRound(1, attackingTeam = 0, pointsMade = 60)
        val (scores, carry) = round.computeScores(0)
        assertEquals(0, scores[0])
        assertEquals(162, scores[1])
        assertEquals(0, carry)
    }

    @Test
    fun `litige carry persists through a failed hand`() {
        val round = BeloteRound(3, attackingTeam = 1, pointsMade = 50)
        val (_, carry) = round.computeScores(litigeCarry = 81)
        assertEquals(81, carry)
    }

    // ─── Litige (81-81) ─────────────────────────────────────────────────────────

    @Test
    fun `exactly 81 points triggers litige - defense scores 81, attacker 0, carry set`() {
        val round = BeloteRound(1, attackingTeam = 0, pointsMade = 81)
        val (scores, carry) = round.computeScores(0)
        assertEquals(0, scores[0])
        assertEquals(81, scores[1])
        assertEquals(81, carry)
    }

    @Test
    fun `second consecutive litige accumulates the carry`() {
        val round = BeloteRound(2, attackingTeam = 1, pointsMade = 81)
        val (scores, carry) = round.computeScores(litigeCarry = 81)
        assertEquals(81, scores[0])   // defending team (team 0) this round
        assertEquals(0, scores[1])
        assertEquals(162, carry)
    }

    // ─── Capot ────────────────────────────────────────────────────────────────

    @Test
    fun `capot by attacking team scores 252-0`() {
        val round = BeloteRound(1, attackingTeam = 0, isCapot = true, capotTeam = 0)
        val (scores, carry) = round.computeScores(0)
        assertEquals(252, scores[0])
        assertEquals(0, scores[1])
        assertEquals(0, carry)
    }

    @Test
    fun `capot by defending team scores 0-252 and attacker gets nothing`() {
        val round = BeloteRound(1, attackingTeam = 0, isCapot = true, capotTeam = 1)
        val (scores, _) = round.computeScores(0)
        assertEquals(0, scores[0])
        assertEquals(252, scores[1])
    }

    @Test
    fun `capot by attacker resolves pending litige carry`() {
        val round = BeloteRound(2, attackingTeam = 1, isCapot = true, capotTeam = 1)
        val (scores, carry) = round.computeScores(litigeCarry = 81)
        assertEquals(333, scores[1])  // 252 + 81 carried
        assertEquals(0, carry)
    }

    @Test
    fun `capot by defender does not resolve pending litige carry`() {
        val round = BeloteRound(2, attackingTeam = 1, isCapot = true, capotTeam = 0)
        val (_, carry) = round.computeScores(litigeCarry = 81)
        assertEquals(81, carry)
    }

    // ─── Belote-Rebelote ────────────────────────────────────────────────────────

    @Test
    fun `belote bonus adds 20 to the announcing team on a successful contract`() {
        val round = BeloteRound(1, attackingTeam = 0, pointsMade = 90, beloteTeam = 0)
        val (scores, _) = round.computeScores(0)
        assertEquals(110, scores[0])
        assertEquals(72, scores[1])
    }

    @Test
    fun `belote bonus is kept even when the announcing team falls (chute)`() {
        val round = BeloteRound(1, attackingTeam = 0, pointsMade = 60, beloteTeam = 0)
        val (scores, _) = round.computeScores(0)
        assertEquals(20, scores[0])   // 0 + belote bonus, still marked despite chute
        assertEquals(162, scores[1])
    }

    @Test
    fun `belote bonus for the defending team is kept even on a capot by the attacker`() {
        val round = BeloteRound(1, attackingTeam = 0, isCapot = true, capotTeam = 0, beloteTeam = 1)
        val (scores, _) = round.computeScores(0)
        assertEquals(252, scores[0])
        assertEquals(20, scores[1])
    }

    // ─── isComplete ───────────────────────────────────────────────────────────

    @Test
    fun `isComplete false when neither capot nor points entered`() {
        assertFalse(BeloteRound(1, attackingTeam = 0).isComplete())
    }

    @Test
    fun `isComplete true when points made are entered`() {
        assertTrue(BeloteRound(1, attackingTeam = 0, pointsMade = 90).isComplete())
    }

    @Test
    fun `isComplete true when capot is flagged even without points`() {
        assertTrue(BeloteRound(1, attackingTeam = 0, isCapot = true, capotTeam = 0).isComplete())
    }

    // ─── BeloteScoring.computeRoundScores ──────────────────────────────────────

    @Test
    fun `computeRoundScores threads carry across multiple hands`() {
        val rounds = listOf(
            BeloteRound(1, attackingTeam = 0, pointsMade = 81),          // litige, carry=81
            BeloteRound(2, attackingTeam = 0, pointsMade = 90)           // 90+81=171 vs 72
        )
        val results = BeloteScoring.computeRoundScores(rounds)
        assertEquals(mapOf(0 to 0, 1 to 81), results[0])
        assertEquals(mapOf(0 to 171, 1 to 72), results[1])
    }

    @Test
    fun `incomplete round in the list scores 0-0 and does not break the carry`() {
        val rounds = listOf(
            BeloteRound(1, attackingTeam = 0, pointsMade = 81),   // carry=81
            BeloteRound(2, attackingTeam = 1)                      // incomplete
        )
        val results = BeloteScoring.computeRoundScores(rounds)
        assertEquals(mapOf(0 to 0, 1 to 0), results[1])
    }

    // ─── BeloteTeamState.getTotal ───────────────────────────────────────────────

    @Test
    fun `getTotal returns 0 with no rounds`() {
        val team = BeloteTeamState(0, "Alice", "Bob", 0xFF0000)
        assertEquals(0, team.getTotal(emptyList()))
    }

    @Test
    fun `getTotal sums a team's scores across hands including litige resolution`() {
        val team0 = BeloteTeamState(0, "Alice", "Bob", 0xFF0000)
        val team1 = BeloteTeamState(1, "Carol", "Dave", 0x00FF00)
        val rounds = listOf(
            BeloteRound(1, attackingTeam = 0, pointsMade = 81),   // 0 / 81, carry=81
            BeloteRound(2, attackingTeam = 1, pointsMade = 100)   // 62 / 100
        )
        assertEquals(62, team0.getTotal(rounds))
        assertEquals(181, team1.getTotal(rounds))   // 81 + 100
    }

    @Test
    fun `displayName combines both partner names`() {
        val team = BeloteTeamState(0, "Alice", "Bob", 0xFF0000)
        assertEquals("Alice & Bob", team.displayName)
    }
}
