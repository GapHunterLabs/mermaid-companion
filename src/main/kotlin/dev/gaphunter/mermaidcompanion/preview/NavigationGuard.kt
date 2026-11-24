package dev.gaphunter.mermaidcompanion.preview

/**
 * The preview never leaves its own page. A diagram can carry links (`click A "https://..."`), and
 * mermaid.js turns them into real `<a href>` elements even in its strict security level; a click
 * on one would load a website inside the Preview tab, make a network request the plugin otherwise
 * never makes, and leave the preview broken until it is reopened. The page itself cancels a click
 * on a link (companion-preview.js); this is the second line, at the browser.
 *
 * Allowed: the main frame's first load (the preview page) and anything inside frames the page
 * creates for exports (no src, nothing to fetch). Refused: any later navigation of the main
 * frame, and every new window.
 */
object NavigationGuard {
    fun cancelNavigation(isMainFrame: Boolean, pageLoaded: Boolean): Boolean = isMainFrame && pageLoaded

    @Suppress("UNUSED_PARAMETER")
    fun cancelPopup(targetUrl: String?): Boolean = true
}
