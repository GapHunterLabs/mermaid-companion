package dev.gaphunter.mermaidcompanion.licensing

import com.intellij.ide.util.PropertiesComponent
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Two Pro moves, one stamp, two cutoffs. [recordFirstSeen] (called from
 * [FirstSeenStartupActivity]) stamps the first day this installation ran a version that
 * tracks it; [keepsThemes] and [keepsSvgExport] each compare that same stamp against their
 * own cutoff.
 *
 * - Diagram themes moved from the free tier to Pro in 2026.2.0 (cutoff [CUTOFF_THEMES]). PNG
 *   export moved with them that same version, then back to the free tier in 2026.3.0 -- it
 *   needs no stamp any more, it is simply free again.
 * - SVG export moved from the free tier to Pro in 2026.3.0 (cutoff [CUTOFF_SVG]).
 */
object LegacyFreeFeatures {
    /** First day diagram themes need a license on an installation stamped from then on. */
    val CUTOFF_THEMES: LocalDate = LocalDate.of(2026, 11, 2)

    /** First day SVG export needs a license on an installation stamped from then on. */
    val CUTOFF_SVG: LocalDate = LocalDate.of(2026, 11, 9)

    private const val FIRST_SEEN_KEY = "dev.gaphunter.mermaidcompanion.firstSeen"

    fun recordFirstSeen(today: LocalDate = LocalDate.now()) {
        val properties = PropertiesComponent.getInstance()
        if (properties.getValue(FIRST_SEEN_KEY) == null) properties.setValue(FIRST_SEEN_KEY, today.toString())
    }

    private fun firstSeen(): String? = PropertiesComponent.getInstance().getValue(FIRST_SEEN_KEY)

    fun keepsThemes(): Boolean = keeps(firstSeen(), CUTOFF_THEMES)
    fun keepsSvgExport(): Boolean = keeps(firstSeen(), CUTOFF_SVG)

    /** The decision itself, testable without the platform. An unreadable stamp counts as none. */
    fun keeps(firstSeen: String?, cutoff: LocalDate): Boolean {
        val date = try {
            firstSeen?.let(LocalDate::parse)
        } catch (e: DateTimeParseException) {
            null
        } ?: return false
        return date.isBefore(cutoff)
    }
}
