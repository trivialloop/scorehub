package com.github.trivialloop.scorehub.utils

import org.junit.Assert.*
import org.junit.Test

class ScoreSheetPrefsTest {

    @Test fun `unlimited never deletes anything`() =
        assertEquals(0, ScoreSheetPrefs.excess(500, ScoreSheetPrefs.UNLIMITED))

    @Test fun `below the limit nothing is deleted`() =
        assertEquals(0, ScoreSheetPrefs.excess(4, 5))

    @Test fun `exactly at the limit nothing is deleted`() =
        assertEquals(0, ScoreSheetPrefs.excess(5, 5))

    @Test fun `above the limit the excess is deleted`() =
        assertEquals(3, ScoreSheetPrefs.excess(8, 5))

    @Test fun `limit of one keeps only the newest`() =
        assertEquals(9, ScoreSheetPrefs.excess(10, 1))

    @Test fun `steps are the expected ones`() =
        assertEquals(listOf(0, 1, 2, 3, 5, 10, 20, 30, 50, 100, ScoreSheetPrefs.UNLIMITED), ScoreSheetPrefs.LIMITS)

    @Test fun `unknown stored value falls back to the default index`() =
        assertEquals(ScoreSheetPrefs.LIMITS.indexOf(ScoreSheetPrefs.DEFAULT_LIMIT), ScoreSheetPrefs.indexOf(7))

    @Test fun `feature is disabled by default`() = 
        assertEquals(0, ScoreSheetPrefs.DEFAULT_LIMIT)
}
