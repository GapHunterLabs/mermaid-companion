package dev.gaphunter.mermaidcompanion.licensing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The legacy-access decision on the exact days around each cutoff, and on stamps that are
 * missing, corrupted or written by a wrong clock. Only the pure decision is tested here;
 * writing the stamp needs the IDE's settings store.
 */
class LegacyFreeFeaturesBoundaryTest {

    private val themes = LegacyFreeFeatures.CUTOFF_THEMES
    private val svg = LegacyFreeFeatures.CUTOFF_SVG

    @Test
    fun `the cutoffs are the dates the README and plugin description promise`() {
        assertEquals(LocalDate.of(2026, 11, 2), themes)
        assertEquals(LocalDate.of(2026, 11, 9), svg)
    }

    @Test
    fun `themes - the day before the cutoff keeps them, the cutoff day and after do not`() {
        assertTrue(LegacyFreeFeatures.keeps("2026-11-01", themes))
        assertFalse(LegacyFreeFeatures.keeps("2026-11-02", themes))
        assertFalse(LegacyFreeFeatures.keeps("2026-11-03", themes))
    }

    @Test
    fun `SVG export - the day before the cutoff keeps it, the cutoff day and after do not`() {
        assertTrue(LegacyFreeFeatures.keeps("2026-11-08", svg))
        assertFalse(LegacyFreeFeatures.keeps("2026-11-09", svg))
        assertFalse(LegacyFreeFeatures.keeps("2026-11-10", svg))
    }

    @Test
    fun `one stamp between the two cutoffs keeps SVG export but not themes`() {
        for (day in listOf("2026-11-02", "2026-11-05", "2026-11-08")) {
            assertFalse(day, LegacyFreeFeatures.keeps(day, themes))
            assertTrue(day, LegacyFreeFeatures.keeps(day, svg))
        }
    }

    @Test
    fun `a missing, empty or corrupted stamp never grants anything`() {
        val corrupted = listOf(
            null, "", " ", "null", "2026-11-1", "2026-13-01", "2026-02-30", "20261101",
            "2026/11/01", "2026-11-01T00:00", " 2026-11-01", "2026-11-01 ", "01-11-2026",
            "2026-11-01\n", "２０２６-１１-０１", "9999999999-01-01",
        )
        for (stamp in corrupted) {
            assertFalse("themes for '$stamp'", LegacyFreeFeatures.keeps(stamp, themes))
            assertFalse("svg for '$stamp'", LegacyFreeFeatures.keeps(stamp, svg))
        }
    }

    @Test
    fun `a stamp written by a clock set in the future fails closed`() {
        assertFalse(LegacyFreeFeatures.keeps("2030-01-01", themes))
        assertFalse(LegacyFreeFeatures.keeps("+12026-01-01", svg))
    }

    /**
     * Pinned current behaviour: any parseable date before the cutoff counts. The stamp is not
     * compared with the release date of the version that introduced it (2026.2.0, published
     * 2026-10-19), so a date no real installation could have written is accepted too.
     */
    @Test
    fun `a stamp dated before the feature existed is accepted (pinned)`() {
        assertTrue(LegacyFreeFeatures.keeps("1970-01-01", themes))
        assertTrue(LegacyFreeFeatures.keeps("2026-01-01", svg))
        assertTrue(LegacyFreeFeatures.keeps("-2026-11-01", themes))
        assertTrue(LegacyFreeFeatures.keeps("-999999999-01-01", svg))
    }
}
