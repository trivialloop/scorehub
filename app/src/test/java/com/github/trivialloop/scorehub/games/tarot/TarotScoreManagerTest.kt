package com.github.trivialloop.scorehub.games.tarot

import org.junit.Test
import org.junit.Assert.*

class TarotScoreManagerTest {

    // ─── Threshold ────────────────────────────────────────────────────────────

    @Test fun `threshold 0 bouts = 56`() = assertEquals(56, TarotRound.threshold(0))
    @Test fun `threshold 1 bout = 51`()  = assertEquals(51, TarotRound.threshold(1))
    @Test fun `threshold 2 bouts = 41`() = assertEquals(41, TarotRound.threshold(2))
    @Test fun `threshold 3 bouts = 36`() = assertEquals(36, TarotRound.threshold(3))

    // ─── isDeclarerWin ────────────────────────────────────────────────────────

    @Test fun `declarer wins at threshold`() = assertTrue(round(2, 41).isDeclarerWin)
    @Test fun `declarer wins above threshold`() = assertTrue(round(1, 60).isDeclarerWin)
    @Test fun `declarer loses below threshold`() = assertFalse(round(0, 40).isDeclarerWin)

    // ─── Zero-sum property (the critical invariant) ───────────────────────────

    @Test fun `zero-sum 4P prise won`() = assertZeroSum(
        players4, round(2, 41, contract = TarotContract.PRISE))

    @Test fun `zero-sum 4P garde won`() = assertZeroSum(
        players4, round(2, 50, contract = TarotContract.GARDE))

    @Test fun `zero-sum 4P garde lost`() = assertZeroSum(
        players4, round(2, 30, contract = TarotContract.GARDE))

    @Test fun `zero-sum 4P garde sans`() = assertZeroSum(
        players4, round(3, 36, contract = TarotContract.GARDE_SANS))

    @Test fun `zero-sum 4P garde contre`() = assertZeroSum(
        players4, round(0, 56, contract = TarotContract.GARDE_CONTRE))

    @Test fun `zero-sum 3P`() = assertZeroSum(
        players3, TarotRound(1, 1L, TarotContract.PRISE, 2, 41))

    @Test fun `zero-sum 5P with partner`() = assertZeroSum(
        players5,
        TarotRound(1, 1L, TarotContract.PRISE, 2, 41, associatedPlayerId = 3L))

    @Test fun `zero-sum 5P solo`() = assertZeroSum(
        players5,
        TarotRound(1, 1L, TarotContract.PRISE, 2, 41, associatedPlayerId = 1L))

    @Test fun `zero-sum with poignee simple declarer`() = assertZeroSum(
        players4, TarotRound(1, 1L, TarotContract.GARDE, 2, 45,
            poignees = TarotPoigneeOptions(TarotPoigneeLevel.SIMPLE, TarotPoigneeLevel.NONE)))

    @Test fun `zero-sum with poignee simple both sides`() = assertZeroSum(
        players4, TarotRound(1, 1L, TarotContract.PRISE, 2, 41,
            poignees = TarotPoigneeOptions(TarotPoigneeLevel.SIMPLE, TarotPoigneeLevel.SIMPLE)))

    @Test fun `zero-sum with petit au bout declarer`() = assertZeroSum(
        players4, TarotRound(1, 1L, TarotContract.GARDE, 2, 45,
            petitAuBout = TarotPetitAuBout.DECLARER))

    @Test fun `zero-sum with petit au bout defense`() = assertZeroSum(
        players4, TarotRound(1, 1L, TarotContract.GARDE, 2, 45,
            petitAuBout = TarotPetitAuBout.DEFENSE))

    @Test fun `zero-sum with petit au bout defense and declarer loses`() = assertZeroSum(
        players4, TarotRound(1, 1L, TarotContract.GARDE_SANS, 2, 30,
            petitAuBout = TarotPetitAuBout.DEFENSE))

    @Test fun `zero-sum with chelem announced success`() = assertZeroSum(
        players4, TarotRound(1, 1L, TarotContract.GARDE, 3, 91,
            chelem = TarotChelem.ANNOUNCED_SUCCESS))

    @Test fun `zero-sum with chelem unannounced`() = assertZeroSum(
        players4, TarotRound(1, 1L, TarotContract.GARDE, 3, 91,
            chelem = TarotChelem.UNANNOUNCED_SUCCESS))

    @Test fun `zero-sum with chelem announced failure`() = assertZeroSum(
        players4, TarotRound(1, 1L, TarotContract.GARDE, 2, 41,
            chelem = TarotChelem.ANNOUNCED_FAILURE))

    @Test fun `zero-sum all options combined`() = assertZeroSum(
        players4, TarotRound(1, 1L, TarotContract.GARDE, 2, 49,
            poignees = TarotPoigneeOptions(TarotPoigneeLevel.SIMPLE, TarotPoigneeLevel.NONE),
            petitAuBout = TarotPetitAuBout.DECLARER,
            chelem = TarotChelem.NONE))

    // ─── Exact score values (4 players) ──────────────────────────────────────

    @Test fun `prise won by 0 - base 25x1`() {
        // base=(0+25)*1=25 per defender; declarer=+75, each defender=-25
        val s = round(2, 41, TarotContract.PRISE).computeScores(players4)
        assertEquals(75, s[1L])
        assertEquals(-25, s[2L])
        assertEquals(-25, s[3L])
        assertEquals(-25, s[4L])
    }

    @Test fun `garde won by 8 - base (8+25)x2=66`() {
        val s = round(2, 49, TarotContract.GARDE).computeScores(players4)
        assertEquals(198, s[1L])   // 66*3
        assertEquals(-66, s[2L])
        assertEquals(-66, s[3L])
        assertEquals(-66, s[4L])
    }

    @Test fun `garde lost by 7 - base (7+25)x2=64`() {
        val s = TarotRound(1, 2L, TarotContract.GARDE, 0, 49)
            .computeScores(players4)  // threshold=56, diff=-7
        assertEquals(-192, s[2L])   // 64*3 negative
        assertEquals(64, s[1L])
        assertEquals(64, s[3L])
        assertEquals(64, s[4L])
    }

    @Test fun `official example - garde poignee10 petit declarer 2bouts 49pts`() {
        // From FFT rules: (8+25)*2=66 + poignee20 + petit10*2=20 = 106
        // Declarer: +318, each defender: -106
        val s = TarotRound(1, 1L, TarotContract.GARDE, 2, 49,
            poignees = TarotPoigneeOptions(TarotPoigneeLevel.SIMPLE, TarotPoigneeLevel.NONE),
            petitAuBout = TarotPetitAuBout.DECLARER
        ).computeScores(players4)
        assertEquals(318, s[1L])
        assertEquals(-106, s[2L])
        assertEquals(-106, s[3L])
        assertEquals(-106, s[4L])
        assertEquals(0, s.values.sum())
    }

    @Test fun `chelem announced success adds 400 per defender`() {
        // base=(0+25)*2=50 + chelem400 = 450 per defender
        // Declarer wins: +1350, each defender -450
        val s = TarotRound(1, 1L, TarotContract.GARDE, 2, 41,
            chelem = TarotChelem.ANNOUNCED_SUCCESS).computeScores(players4)
        assertEquals(1350, s[1L])
        assertEquals(-450, s[2L])
        assertEquals(0, s.values.sum())
    }

    @Test fun `chelem unannounced success adds 200 per defender`() {
        val s = TarotRound(1, 1L, TarotContract.GARDE, 2, 41,
            chelem = TarotChelem.UNANNOUNCED_SUCCESS).computeScores(players4)
        // base=50 + chelem200 = 250; declarer +750, defenders -250
        assertEquals(750, s[1L])
        assertEquals(-250, s[2L])
        assertEquals(0, s.values.sum())
    }

    @Test fun `chelem announced failure - declarer pays 200 per defender`() {
        // Declarer still wins contract (41pts, 2 bouts) but failed chelem
        // base=50, chelem_penalty=200 → per defender: -50 + 200 = +150
        // declarer: -(3*150) = -450
        val s = TarotRound(1, 1L, TarotContract.GARDE, 2, 41,
            chelem = TarotChelem.ANNOUNCED_FAILURE).computeScores(players4)
        assertEquals(-450, s[1L])
        assertEquals(150, s[2L])
        assertEquals(0, s.values.sum())
    }

    @Test fun `petit au bout defense adds per-defender unit`() {
        // Garde (×2), 2 bouts, 45 pts won (+4), petit defense
        // base=(4+25)*2=58, petit=10*2=20
        // defenderScore = -58 (contract won) +20 (defense wins petit) = -38
        // declarer = -(-38*3) = +114
        val s = TarotRound(1, 1L, TarotContract.GARDE, 2, 45,
            petitAuBout = TarotPetitAuBout.DEFENSE).computeScores(players4)
        assertEquals(114, s[1L])
        assertEquals(-38, s[2L])
        assertEquals(0, s.values.sum())
    }

    @Test fun `petit au bout declarer adds to declarer`() {
        // Same scenario but petit for declarer
        // defenderScore = -58 -20 = -78; declarer = +234
        val s = TarotRound(1, 1L, TarotContract.GARDE, 2, 45,
            petitAuBout = TarotPetitAuBout.DECLARER).computeScores(players4)
        assertEquals(234, s[1L])
        assertEquals(-78, s[2L])
        assertEquals(0, s.values.sum())
    }

    // ─── 3 players ────────────────────────────────────────────────────────────

    @Test fun `3P prise won by 0 - declarer 2x defenders`() {
        val s = TarotRound(1, 1L, TarotContract.PRISE, 2, 41)
            .computeScores(players3)
        // base=25; defender=-25; declarer=+50
        assertEquals(50, s[1L])
        assertEquals(-25, s[2L])
        assertEquals(-25, s[3L])
        assertEquals(0, s.values.sum())
    }

    // ─── 5 players ────────────────────────────────────────────────────────────

    @Test fun `5P with partner - declarer 2x partner 1x defenders 1x`() {
        val s = TarotRound(1, 1L, TarotContract.PRISE, 2, 41,
            associatedPlayerId = 3L).computeScores(players5)
        // base=25; defender=-25; partner=+25; declarer=-(3*-25+25)=-(−75+25)=+50
        assertEquals(50, s[1L])   // declarer
        assertEquals(25, s[3L])   // partner
        assertEquals(-25, s[2L]) // defender
        assertEquals(-25, s[4L])
        assertEquals(-25, s[5L])
        assertEquals(0, s.values.sum())
    }

    @Test fun `5P solo - declarer 4x`() {
        val s = TarotRound(1, 1L, TarotContract.PRISE, 2, 41,
            associatedPlayerId = 1L).computeScores(players5)
        // base=25; defender=-25; declarer=+100
        assertEquals(100, s[1L])
        assertEquals(-25, s[2L])
        assertEquals(-25, s[3L])
        assertEquals(-25, s[4L])
        assertEquals(-25, s[5L])
        assertEquals(0, s.values.sum())
    }

    // ─── TarotPlayerState.getTotal ────────────────────────────────────────────

    @Test fun `getTotal empty returns 0`() {
        assertEquals(0, TarotPlayerState(1L, "A", 0).getTotal(emptyList(), players4))
    }

    @Test fun `getTotal accumulates across rounds`() {
        val alice = TarotPlayerState(1L, "Alice", 0)
        val r1 = TarotRound(1, 1L, TarotContract.PRISE, 2, 41) // alice wins +75
        val r2 = TarotRound(2, 2L, TarotContract.PRISE, 2, 41) // alice defends -25
        assertEquals(75 - 25, alice.getTotal(listOf(r1, r2), players4))
    }

    // ─── getCellRole ──────────────────────────────────────────────────────────

    @Test fun `getCellRole declarer win`() = assertEquals(
        TarotCellRole.DECLARER_WIN, round(2, 50).getCellRole(1L, players4))

    @Test fun `getCellRole declarer loss`() = assertEquals(
        TarotCellRole.DECLARER_LOSS, round(2, 30).getCellRole(1L, players4))

    @Test fun `getCellRole defender when declarer wins`() = assertEquals(
        TarotCellRole.DEFENDER_LOSS, round(2, 50).getCellRole(2L, players4))

    @Test fun `getCellRole defender when declarer loses`() = assertEquals(
        TarotCellRole.DEFENDER_WIN, round(2, 30).getCellRole(2L, players4))

    @Test fun `getCellRole 5P partner win`() = assertEquals(
        TarotCellRole.PARTNER_WIN,
        TarotRound(1, 1L, TarotContract.PRISE, 2, 50, associatedPlayerId = 3L)
            .getCellRole(3L, players5))

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private val players3 = listOf(1L, 2L, 3L)
    private val players4 = listOf(1L, 2L, 3L, 4L)
    private val players5 = listOf(1L, 2L, 3L, 4L, 5L)

    private fun round(
        bouts: Int, pts: Int,
        contract: TarotContract = TarotContract.PRISE,
        declarerId: Long = 1L
    ) = TarotRound(1, declarerId, contract, bouts, pts)

    private fun assertZeroSum(playerIds: List<Long>, round: TarotRound) {
        val scores = round.computeScores(playerIds)
        assertEquals(
            "Zero-sum failed for round: $round\nScores: $scores",
            0, scores.values.sum()
        )
    }
}
