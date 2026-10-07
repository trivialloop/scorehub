package com.github.trivialloop.scorehub.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScoreProgressHelperTest {

    @Test
    fun `ratio is clamped between 0 and 1`() {
        assertEquals(0f, ScoreProgressHelper.ratio(-50, 1000), 0.001f)
        assertEquals(0.5f, ScoreProgressHelper.ratio(500, 1000), 0.001f)
        assertEquals(1f, ScoreProgressHelper.ratio(1200, 1000), 0.001f)
    }

    @Test
    fun `ratio is 0 for a zero score`() {
        assertEquals(0f, ScoreProgressHelper.ratio(0, 100), 0.001f)
    }

    @Test
    fun `ratio is exactly 1 when the limit is reached`() {
        assertEquals(1f, ScoreProgressHelper.ratio(100, 100), 0.001f)
    }

    @Test
    fun `ratio returns 0 when limit is invalid`() {
        assertEquals(0f, ScoreProgressHelper.ratio(50, 0), 0.001f)
        assertEquals(0f, ScoreProgressHelper.ratio(50, -10), 0.001f)
    }

    @Test
    fun `entry weight is optional`() {
        assertNull(ScoreProgressHelper.Entry(10, 0).weight)
        assertEquals(0.6f, ScoreProgressHelper.Entry(10, 0, 0.6f).weight!!, 0.001f)
    }
}