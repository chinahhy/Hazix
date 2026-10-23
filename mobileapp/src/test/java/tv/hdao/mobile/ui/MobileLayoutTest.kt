package tv.hdao.mobile.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MobileLayoutTest {
    @Test
    fun allSixCategoriesRemainDirectlyReachable() {
        assertEquals(
            listOf("movie", "tv", "variety", "documentary", "anime", "short-drama"),
            mobileCategories.map { it.first },
        )
        assertEquals(6, mobileCategories.map { it.first }.distinct().size)
    }

    @Test
    fun narrowPhonesUseTwoGridColumns() {
        assertEquals(2, mobilePosterColumns(320f))
        assertEquals(2, mobilePosterColumns(359f))
        assertEquals(3, mobilePosterColumns(360f))
        assertEquals(3, mobilePosterColumns(390f))
        assertEquals(5, mobilePosterColumns(600f))
    }

    @Test
    fun regularPhoneKeepsHeroAndPostersInAvailableFirstScreen() {
        val layout = mobileHomeLayout(740f, 1f, 24f)

        assertEquals(740f, layout.firstScreenHeightDp, 0.01f)
        assertEquals(103.6f, layout.posterWidthDp, 0.01f)
        assertFalse(layout.compact)
        assertHeaderAndCopyDoNotOverlap(layout)
    }

    @Test
    fun shortPhoneUsesCompactCopyAndSmallerPosters() {
        val layout = mobileHomeLayout(464f, 1f, 24f)

        assertEquals(465f, layout.firstScreenHeightDp, 0.01f)
        assertEquals(80f, layout.posterWidthDp, 0.01f)
        assertTrue(layout.compact)
        assertHeaderAndCopyDoNotOverlap(layout)
    }

    @Test
    fun veryShortWindowsCanScrollInsteadOfClippingControls() {
        val layout = mobileHomeLayout(280f, 1f, 24f)

        assertEquals(465f, layout.firstScreenHeightDp, 0.01f)
        assertEquals(72f, layout.headerHeightDp, 0.01f)
        assertEquals(168f, layout.heroCopyHeightDp, 0.01f)
        assertEquals(209f, layout.shelfHeightDp, 0.01f)
        assertTrue(layout.compact)
        assertHeaderAndCopyDoNotOverlap(layout)
    }

    @Test
    fun largeTextAndNotchGetScrollableSpaceAndCompactCopy() {
        val layout = mobileHomeLayout(464f, 2f, 52f)

        assertEquals(603f, layout.firstScreenHeightDp, 0.01f)
        assertTrue(layout.compact)
        assertHeaderAndCopyDoNotOverlap(layout)
    }

    @Test
    fun tallerSafeInsetIncreasesBudgetWithoutShrinkingPlayOrShelf() {
        val standard = mobileHomeLayout(280f, 1f, 24f)
        val notched = mobileHomeLayout(280f, 1f, 52f)

        assertEquals(28f, notched.firstScreenHeightDp - standard.firstScreenHeightDp, 0.01f)
        assertEquals(standard.heroCopyHeightDp, notched.heroCopyHeightDp, 0.01f)
        assertEquals(standard.shelfHeightDp, notched.shelfHeightDp, 0.01f)
        assertHeaderAndCopyDoNotOverlap(notched)
    }

    @Test
    fun allSupportedHeightFontAndSafeAreaBudgetsKeepHeaderClearance() {
        for (height in listOf(280f, 440f, 464f, 620f, 740f, 1200f)) {
            for (fontScale in listOf(1f, 1.3f, 2f, 3f)) {
                for (topInset in listOf(0f, 24f, 52f)) {
                    val layout = mobileHomeLayout(height, fontScale, topInset)
                    assertTrue(layout.firstScreenHeightDp >= height)
                    assertHeaderAndCopyDoNotOverlap(layout)
                }
            }
        }
    }

    @Test
    fun tallWindowsDoNotEnlargePostersWithoutLimit() {
        assertEquals(116f, mobileHomeLayout(1200f, 1f, 24f).posterWidthDp, 0.01f)
    }

    private fun assertHeaderAndCopyDoNotOverlap(layout: MobileHomeLayout) {
        val heroHeight = layout.firstScreenHeightDp - layout.shelfHeightDp
        assertTrue(
            "Hero must fit its top safe area, category header, complete copy/actions and clearance",
            heroHeight + 0.01f >= layout.headerHeightDp + layout.heroCopyHeightDp + layout.copyTopGapDp,
        )
        assertTrue(layout.copyTopGapDp >= 16f)
    }
}
