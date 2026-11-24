package dev.gaphunter.mermaidcompanion.preview

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationGuardTest {

    @Test
    fun `the preview page itself loads, and frames the page creates are left alone`() {
        assertFalse("first load of the preview page", NavigationGuard.cancelNavigation(isMainFrame = true, pageLoaded = false))
        assertFalse("an export frame before the page loaded", NavigationGuard.cancelNavigation(isMainFrame = false, pageLoaded = false))
        assertFalse("an export frame after the page loaded", NavigationGuard.cancelNavigation(isMainFrame = false, pageLoaded = true))
    }

    @Test
    fun `once the page is up, the main frame never navigates away`() {
        assertTrue(NavigationGuard.cancelNavigation(isMainFrame = true, pageLoaded = true))
    }

    @Test
    fun `no link opens a window, whatever its address`() {
        for (url in listOf("https://example.com", "javascript:alert(1)", "file:///etc/passwd", "", null)) {
            assertTrue("popup for $url", NavigationGuard.cancelPopup(url))
        }
    }
}
