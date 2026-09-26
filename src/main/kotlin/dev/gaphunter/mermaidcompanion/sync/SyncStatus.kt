package dev.gaphunter.mermaidcompanion.sync

import java.nio.file.Path

/** What the toolbar shows about the last "Export on Save" attempt. */
sealed interface SyncStatus {
    data object Idle : SyncStatus
    data object Syncing : SyncStatus

    /** [written] were rewritten; [unchanged] already had the right content. */
    data class Synced(val written: List<Path>, val unchanged: Int) : SyncStatus

    /** The diagram doesn't render right now: existing exports were left as they are. */
    data class RenderFailed(val message: String) : SyncStatus
    data class Failed(val message: String) : SyncStatus

    /** A Pro action was used without a license. */
    data object NeedsLicense : SyncStatus

    /** "Sync Now" with neither SVG nor PNG turned on. */
    data object NothingEnabled : SyncStatus
}
