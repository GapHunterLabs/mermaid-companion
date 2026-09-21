package dev.gaphunter.mermaidcompanion.coexistence

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

/** Tools menu: hand `.mmd`/`.mermaid` editing to JetBrains's Mermaid editor. */
class UseJetBrainsMermaidEditorAction : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) {
        NativeMermaid.fileType()?.let { EditorOwnership.deferTo(it) }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = NativeMermaid.isEnabled() && !EditorOwnership.deferEditingToNative
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

/** Tools menu: take `.mmd`/`.mermaid` editing back into Mermaid Companion. */
class UseCompanionMermaidEditorAction : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) {
        EditorOwnership.reclaimFrom(NativeMermaid.fileType())
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = EditorOwnership.deferEditingToNative
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
