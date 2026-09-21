package dev.gaphunter.mermaidcompanion.coexistence

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * When JetBrains's Mermaid plugin is also present, asks once (per IDE, not
 * per project) which editor should own `.mmd`/`.mermaid` files. Nothing
 * changes unless the user clicks; both choices stay reversible from the
 * Tools menu.
 */
class CoexistenceStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        val offer = EditorOwnership.shouldOfferChoice(
            nativeEnabled = NativeMermaid.isEnabled(),
            deferred = EditorOwnership.deferEditingToNative,
            noticeShown = EditorOwnership.noticeShown,
        )
        if (!offer) return
        EditorOwnership.noticeShown = true
        NotificationGroupManager.getInstance().getNotificationGroup("Mermaid Companion")
            .createNotification(
                "JetBrains Mermaid support is also installed",
                "Mermaid Companion is editing .mmd/.mermaid files right now. JetBrains's own Mermaid editor " +
                    "adds completion, formatting and rename. The Mermaid Companion Preview tab (zoom, themes, " +
                    "SVG/PNG export) stays available with either editor.",
                NotificationType.INFORMATION,
            )
            .addAction(NotificationAction.createSimpleExpiring("Use JetBrains editor, keep Companion preview") {
                NativeMermaid.fileType()?.let { EditorOwnership.deferTo(it) }
            })
            .addAction(NotificationAction.createSimpleExpiring("Keep Mermaid Companion editor") {})
            .notify(project)
    }
}
