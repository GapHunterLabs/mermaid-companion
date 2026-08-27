package dev.gaphunter.mermaidcompanion.review

import com.intellij.ide.BrowserUtil
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader

/**
 * Asks the user to rate the plugin on Marketplace, once, after the
 * plugin's own detector has flagged a real number of *distinct*
 * problems -- never on install, never on a timer, and never inflated
 * by the platform re-running the same inspection/line-marker pass over
 * an unchanged file (both `LocalInspectionTool.checkFile` and
 * `LineMarkerProviderDescriptor.collectSlowLineMarkers` fire on every
 * background highlight pass, not just on a real edit). [recordHit]
 * takes a small stable key per finding (e.g. `"$filePath:$lineNumber"`)
 * and only counts it the first time that exact key is ever seen, so
 * re-highlighting the same file repeatedly doesn't move the counter.
 *
 * This plugin uniquely has TWO real signals of value sharing this one
 * counter/threshold: [MermaidSyntaxAnnotator]'s real syntax findings
 * (dedupe-keyed, the usual mechanical pattern) and
 * [MermaidPreviewFileEditor]'s successful diagram renders (no natural
 * dedupe key -- each debounced render is its own distinct "use", so
 * the no-arg overload generates an internal counter-based key). Either
 * signal moves the same counter toward the same threshold; the user
 * is never asked twice for the same plugin.
 *
 * Same "earn the ask" principle as other well-regarded plugins; never
 * re-asks once the user has either rated or dismissed it.
 *
 * Persisted via [PropertiesComponent] at the application level (not
 * per-project) -- how many distinct problems this plugin has flagged
 * isn't tied to any one project, and neither is whether the user
 * already answered.
 */
object ReviewPrompt {

    /** How many distinct real findings before the prompt shows once. */
    private const val HITS_BEFORE_PROMPT = 10

    /** Caps how many dedupe keys are retained -- well past HITS_BEFORE_PROMPT, just a sane upper bound. */
    private const val MAX_TRACKED_KEYS = 500

    private const val KEY_SEEN_FINDINGS = "dev.gaphunter.mermaidcompanion.review.seenFindings"
    private const val KEY_ANSWERED = "dev.gaphunter.mermaidcompanion.review.answered"

    private const val NOTIFICATION_GROUP_ID = "Mermaid Companion"

    /** Counter-based key generator for callers with no natural dedupe key (the preview's own renders). */
    private const val KEY_RENDER_COUNTER = "dev.gaphunter.mermaidcompanion.review.renderCounter"

    // TODO(post-first-publish): Marketplace only assigns a numeric plugin
    // ID on the first manual submit (queued, see demo/README.md) -- until
    // then this points at the vendor page so "Rate on Marketplace" still
    // goes somewhere real instead of a 404. Update to
    // https://plugins.jetbrains.com/plugin/<id>-__SLUG__/reviews once the
    // real ID is known (recorded in the same place as the other
    // post-publish follow-ups).
    private const val MARKETPLACE_URL = "https://plugins.jetbrains.com/vendor/gap-hunter-labs"

    /**
     * Call this from the real detection code path once per distinct
     * real finding (e.g. once per problem/line-marker actually
     * produced), passing a key that's stable for that same finding
     * across re-highlighting the same unchanged file (a file path +
     * line number is enough -- doesn't need to be globally unique or
     * survive the finding moving to a different line). Safe to call on
     * any thread.
     */
    fun recordHit(project: Project?, dedupeKey: String) {
        if (ApplicationManager.getApplication() == null) return
        val properties = PropertiesComponent.getInstance()
        if (properties.getBoolean(KEY_ANSWERED)) return

        val seen = properties.getList(KEY_SEEN_FINDINGS)?.toMutableSet() ?: mutableSetOf()
        if (!seen.add(dedupeKey)) return // already counted this exact finding

        if (seen.size > MAX_TRACKED_KEYS) {
            // Drop to just the count once the tracked set gets large --
            // avoids unbounded growth in a huge project; the milestone
            // has long since passed by MAX_TRACKED_KEYS anyway.
            properties.unsetValue(KEY_SEEN_FINDINGS)
        } else {
            properties.setList(KEY_SEEN_FINDINGS, seen)
        }

        if (seen.size == HITS_BEFORE_PROMPT) {
            showPrompt(project)
        }
    }

    /**
     * Overload for callers with no natural dedupe key -- [MermaidPreviewFileEditor]'s
     * successful diagram renders, where each debounced render is
     * already its own distinct event (never called per keystroke, the
     * caller's own Alarm-based debounce already collapses that).
     * Generates an internal counter-based key so this still shares the
     * same [seen] set and threshold as the annotator's real findings.
     */
    fun recordHit(project: Project?) {
        if (ApplicationManager.getApplication() == null) return
        val properties = PropertiesComponent.getInstance()
        val nextIndex = properties.getInt(KEY_RENDER_COUNTER, 0) + 1
        properties.setValue(KEY_RENDER_COUNTER, nextIndex, 0)
        recordHit(project, "render:$nextIndex")
    }

    private fun showPrompt(project: Project?) {
        val properties = PropertiesComponent.getInstance()

        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(
                "Mermaid Companion",
                "If this plugin has been useful, a rating on the Marketplace helps other developers find it.",
                NotificationType.INFORMATION,
            )
        notification.setIcon(IconLoader.getIcon("/META-INF/pluginIcon.svg", ReviewPrompt::class.java))

        notification.addAction(NotificationAction.createSimpleExpiring("Rate on Marketplace") {
            properties.setValue(KEY_ANSWERED, true)
            BrowserUtil.browse(MARKETPLACE_URL)
        })
        notification.addAction(NotificationAction.createSimpleExpiring("Don't ask again") {
            properties.setValue(KEY_ANSWERED, true)
        })

        notification.notify(project)
    }
}
