package tv.hdao.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutTest {
    @Test
    fun `TCL 1080p at 240dpi keeps standard cards and two lines of synopsis`() {
        val layout = homeLayout(1080f / 1.5f)

        assertEquals(720f, layout.firstScreenHeightDp, 0.01f)
        assertEquals(132f, layout.posterWidthDp, 0.01f)
        assertEquals(186f, layout.posterHeightDp, 0.01f)
        assertEquals(2, layout.descriptionMaxLines)
        assertTrue(layout.topInsetDp >= 60f)
        assertBudgetFits(layout)
    }

    @Test
    fun `viewport reduced by insets still keeps full size cards`() {
        val layout = homeLayout(678f)

        assertEquals(678f, layout.firstScreenHeightDp, 0.01f)
        assertEquals(186f, layout.posterHeightDp, 0.01f)
        assertEquals(2, layout.descriptionMaxLines)
        assertBudgetFits(layout)
    }

    @Test
    fun `short viewport reduces synopsis and spacing before scaling cards`() {
        val layout = homeLayout(540f)

        assertEquals(540f, layout.firstScreenHeightDp, 0.01f)
        assertEquals(1, layout.descriptionMaxLines)
        assertEquals(16f, layout.copyGapDp, 0.01f)
        assertTrue(layout.posterHeightDp in 132f..186f)
        assertTrue(layout.posterHeightDp < 186f)
        assertBudgetFits(layout)
    }

    @Test
    fun `larger system text is included in the copy budget`() {
        val normal = homeLayout(720f)
        val larger = homeLayout(720f, 1.3f)

        assertTrue(larger.minimumCopyHeightDp > normal.minimumCopyHeightDp)
        assertEquals(186f, larger.posterHeightDp, 0.01f)
        assertEquals(720f, larger.firstScreenHeightDp, 0.01f)
        assertBudgetFits(larger)
    }

    @Test
    fun `extreme font scale omits synopsis before crushing posters`() {
        val layout = homeLayout(720f, 2f)

        assertEquals(0, layout.descriptionMaxLines)
        assertEquals(720f, layout.firstScreenHeightDp, 0.01f)
        assertTrue(layout.posterHeightDp >= 132f)
        assertBudgetFits(layout)
    }

    @Test
    fun `impossibly short viewport scrolls rather than clipping actions`() {
        val layout = homeLayout(360f)

        assertEquals(0, layout.descriptionMaxLines)
        assertEquals(132f, layout.posterHeightDp, 0.01f)
        assertTrue(layout.firstScreenHeightDp > 360f)
        assertBudgetFits(layout)
    }

    @Test
    fun `all supported budgets preserve poster ratio and navigation clearance`() {
        for (height in listOf(480f, 540f, 600f, 678f, 720f, 900f)) {
            for (scale in listOf(0.85f, 1f, 1.3f, 1.6f, 2f)) {
                val layout = homeLayout(height, scale)
                assertEquals(132f / 186f, layout.posterWidthDp / layout.posterHeightDp, 0.0001f)
                assertTrue(layout.posterHeightDp in 132f..186f)
                assertTrue(layout.descriptionMaxLines in 0..2)
                assertTrue(layout.topInsetDp >= 60f)
                assertBudgetFits(layout)
            }
        }
    }

    @Test
    fun `invalid measurements use finite safe defaults`() {
        val layout = homeLayout(Float.NaN, Float.POSITIVE_INFINITY)
        val fallback = homeLayout(720f)

        assertEquals(fallback, layout)
        assertEquals(fallback, homeLayout(-1f, 0f))
    }

    private fun assertBudgetFits(layout: HomeLayout) {
        val required = layout.topInsetDp + layout.minimumCopyHeightDp +
            layout.copyGapDp + layout.shelfOverheadDp + layout.posterHeightDp
        assertTrue("Copy, actions and focus clearance must fit: $layout", required <= layout.firstScreenHeightDp + 0.01f)
    }
}
