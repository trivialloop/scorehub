class ScoreProgressHelperTest {
    @Test fun `ratio is clamped between 0 and 1`() {
        assertEquals(0f, ScoreProgressHelper.ratio(-50, 1000), 0.001f)
        assertEquals(0.5f, ScoreProgressHelper.ratio(500, 1000), 0.001f)
        assertEquals(1f, ScoreProgressHelper.ratio(1200, 1000), 0.001f)
    }
}
