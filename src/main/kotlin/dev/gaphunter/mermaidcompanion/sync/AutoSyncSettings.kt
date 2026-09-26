package dev.gaphunter.mermaidcompanion.sync

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros

/**
 * Which diagrams keep which exports in sync, remembered per project and per
 * user (workspace file -- not shared through version control, so turning it
 * on never changes what a teammate's IDE does).
 */
@Service(Service.Level.PROJECT)
@State(name = "MermaidCompanionExportOnSave", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class AutoSyncSettings : PersistentStateComponent<AutoSyncSettings.State> {

    class State {
        var svgFiles: MutableList<String> = mutableListOf()
        var pngFiles: MutableList<String> = mutableListOf()
    }

    private var state = State()

    override fun getState(): State = state
    override fun loadState(loaded: State) {
        state = loaded
    }

    private fun listFor(format: ExportFormat): MutableList<String> = when (format) {
        ExportFormat.SVG -> state.svgFiles
        ExportFormat.PNG -> state.pngFiles
    }

    fun isEnabled(fileUrl: String, format: ExportFormat): Boolean = fileUrl in listFor(format)

    fun enabledFormats(fileUrl: String): Set<ExportFormat> =
        ExportFormat.entries.filterTo(linkedSetOf()) { isEnabled(fileUrl, it) }

    fun setEnabled(fileUrl: String, format: ExportFormat, enabled: Boolean) {
        val list = listFor(format)
        if (enabled) {
            if (fileUrl !in list) list += fileUrl
        } else {
            list.remove(fileUrl)
        }
    }
}
