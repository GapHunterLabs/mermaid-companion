package dev.gaphunter.mermaidcompanion.licensing

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class ProGateTest {

    private lateinit var original: () -> Boolean?
    private lateinit var originalLegacyThemes: () -> Boolean
    private lateinit var originalLegacySvg: () -> Boolean

    @Before
    fun remember() {
        original = ProGate.licenseCheck
        originalLegacyThemes = ProGate.legacyThemesCheck
        originalLegacySvg = ProGate.legacySvgCheck
    }

    @After
    fun restore() {
        ProGate.licenseCheck = original
        ProGate.legacyThemesCheck = originalLegacyThemes
        ProGate.legacySvgCheck = originalLegacySvg
    }

    @Test
    fun `diagram themes need a license on a new installation`() {
        ProGate.licenseCheck = { false }
        ProGate.legacyThemesCheck = { false }
        assertFalse(ProGate.allowsThemes())
        ProGate.licenseCheck = { null }
        assertFalse(ProGate.allowsThemes())
    }

    @Test
    fun `diagram themes stay available with a license or on an installation that had them`() {
        ProGate.licenseCheck = { true }
        ProGate.legacyThemesCheck = { false }
        assertTrue(ProGate.allowsThemes())
        ProGate.licenseCheck = { false }
        ProGate.legacyThemesCheck = { true }
        assertTrue(ProGate.allowsThemes())
    }

    @Test
    fun `SVG export needs a license on a new installation`() {
        ProGate.licenseCheck = { false }
        ProGate.legacySvgCheck = { false }
        assertFalse(ProGate.allowsSvgExport())
        ProGate.licenseCheck = { null }
        assertFalse(ProGate.allowsSvgExport())
    }

    @Test
    fun `SVG export stays available with a license or on an installation that had it`() {
        ProGate.licenseCheck = { true }
        ProGate.legacySvgCheck = { false }
        assertTrue(ProGate.allowsSvgExport())
        ProGate.licenseCheck = { false }
        ProGate.legacySvgCheck = { true }
        assertTrue(ProGate.allowsSvgExport())
    }

    @Test
    fun `each legacy stamp only counts before its own cutoff`() {
        assertTrue(LegacyFreeFeatures.keeps(LegacyFreeFeatures.CUTOFF_THEMES.minusDays(1).toString(), LegacyFreeFeatures.CUTOFF_THEMES))
        assertFalse(LegacyFreeFeatures.keeps(LegacyFreeFeatures.CUTOFF_THEMES.toString(), LegacyFreeFeatures.CUTOFF_THEMES))
        assertTrue(LegacyFreeFeatures.keeps(LegacyFreeFeatures.CUTOFF_SVG.minusDays(1).toString(), LegacyFreeFeatures.CUTOFF_SVG))
        assertFalse(LegacyFreeFeatures.keeps(LegacyFreeFeatures.CUTOFF_SVG.toString(), LegacyFreeFeatures.CUTOFF_SVG))
        assertFalse(LegacyFreeFeatures.keeps(null, LegacyFreeFeatures.CUTOFF_SVG))
        assertFalse(LegacyFreeFeatures.keeps("not a date", LegacyFreeFeatures.CUTOFF_SVG))
    }

    @Test
    fun `Export on Save stays Pro only -- an installation that had the moved features doesn't get it`() {
        ProGate.licenseCheck = { false }
        ProGate.legacyThemesCheck = { true }
        ProGate.legacySvgCheck = { true }
        assertFalse(ProGate.isOpen())
    }

    @Test
    fun `only an explicit yes opens Pro`() {
        ProGate.licenseCheck = { true }
        assertTrue(ProGate.isOpen())
    }

    @Test
    fun `a license that is not there keeps Pro closed`() {
        ProGate.licenseCheck = { false }
        assertFalse(ProGate.isOpen())
    }

    @Test
    fun `an inconclusive check keeps Pro closed too -- the license facade is not ready yet`() {
        ProGate.licenseCheck = { null }
        assertFalse(ProGate.isOpen())
    }

    @Test
    fun `the real check is closed when this build has no product code yet`() {
        // Not injected: goes through CheckLicense with the placeholder code. Outside an
        // IDE there is no LicensingFacade at all -- either way the answer is "not licensed".
        ProGate.licenseCheck = original
        val answer = try {
            CheckLicense.isLicensed()
        } catch (e: Throwable) {
            null
        }
        assertFalse(answer == true)
    }

    /**
     * The code in CheckLicense and the <product-descriptor> in plugin.xml are one fact written
     * twice. They must agree whenever the descriptor is there; and a build that carries a
     * real code in one place but not the other is caught here, before it can ship.
     */
    @Test
    fun `the product code in the license check matches the plugin descriptor`() {
        val xml = File("src/main/resources/META-INF/plugin.xml").readText()
        val descriptorCode = Regex("""<product-descriptor[^>]*\bcode="([^"]+)"""").find(xml)?.groupValues?.get(1)

        if (descriptorCode == null) {
            assertEquals(
                "no <product-descriptor> yet, so no real product code may be in use either",
                CheckLicense.PENDING_PRODUCT_CODE,
                CheckLicense.PRODUCT_CODE,
            )
        } else {
            assertEquals(descriptorCode, CheckLicense.PRODUCT_CODE)
        }
    }
}
