package dev.gaphunter.mermaidcompanion.sync

import java.nio.file.Path

/**
 * The "Export on Save" flow, with every side effect injected so it can be
 * tested without an IDE, a browser or a disk:
 *
 *   save (or Sync Now) -> render the current text -> when that render
 *   succeeds, ask the page for each enabled format -> write each one.
 *
 * Exports only ever come from a render that succeeded for the text being
 * saved. If the diagram doesn't parse, nothing is exported and the existing
 * files stay as they are -- a half-typed diagram never overwrites a good
 * export. Pro is fail-closed: nothing is rendered, requested or written
 * unless [licensed] says so, checked again right before every write.
 *
 * Every method is called on one thread (the UI thread). The disk write is
 * asynchronous -- [requestWrite] starts it and its outcome comes back through
 * [onWritten] -- so a slow disk never blocks the UI.
 */
class SyncCoordinator(
    private val enabledFormats: () -> Set<ExportFormat>,
    private val licensed: () -> Boolean,
    private val requestRender: (Int) -> Unit,
    private val requestExport: (ExportFormat) -> Unit,
    private val requestWrite: (ExportFormat, ByteArray) -> Unit,
    private val report: (SyncStatus) -> Unit,
) {
    private var active = false
    private var rendered = false

    /** Identifies the render this flow asked for; only its outcome counts. Never reused. */
    private var token = 0
    private var retries = 0

    /** Formats the page hasn't answered for yet, and formats being written. */
    private val waiting = linkedSetOf<ExportFormat>()
    private val writing = linkedSetOf<ExportFormat>()
    private val written = mutableListOf<Path>()
    private var unchanged = 0
    private var failure: String? = null

    /** A save of the diagram's file. Silent when Pro is closed or nothing is enabled. */
    fun onSave() {
        if (!licensed()) return
        start(enabledFormats(), announceProblems = false)
    }

    /** The "Sync Now" button: says why when it can't run. */
    fun onSyncNow() {
        if (!licensed()) {
            report(SyncStatus.NeedsLicense)
            return
        }
        start(enabledFormats(), announceProblems = true)
    }

    private fun start(formats: Set<ExportFormat>, announceProblems: Boolean) {
        if (formats.isEmpty()) {
            if (announceProblems) report(SyncStatus.NothingEnabled)
            return
        }
        // A newer save supersedes one still in flight.
        reset()
        waiting.addAll(formats)
        active = true
        retries = 0
        report(SyncStatus.Syncing)
        startRender()
    }

    private fun startRender() {
        token++
        requestRender(token)
    }

    /** The page finished rendering successfully. */
    fun onRendered(forToken: Int) {
        if (!active || rendered || forToken != token) return
        rendered = true
        waiting.toList().forEach(requestExport)
    }

    /** The page could not render the current text. */
    fun onRenderFailed(forToken: Int, message: String) {
        if (!active || rendered || forToken != token) return
        reset()
        report(SyncStatus.RenderFailed(message))
    }

    /**
     * A newer render (the user kept typing) replaced ours before it finished.
     * Ask again, a couple of times at most.
     */
    fun onRenderSuperseded(forToken: Int) {
        if (!active || rendered || forToken != token) return
        if (retries < MAX_RETRIES) {
            retries++
            startRender()
        } else {
            reset()
            report(SyncStatus.Failed("The diagram kept changing while syncing; exports were left as they are."))
        }
    }

    /** The page delivered one export. */
    fun onExport(format: ExportFormat, bytes: ByteArray) {
        if (!active || !rendered || !waiting.remove(format)) return
        if (!licensed()) {
            reset()
            return
        }
        writing += format
        requestWrite(format, bytes)
    }

    /** The page could not produce an export. */
    fun onExportFailed(message: String) {
        if (!active || !rendered) return
        waiting.clear()
        failure = failure ?: message
        finishIfDone()
    }

    /** The outcome of a write started by [requestWrite]. */
    fun onWritten(format: ExportFormat, result: WriteResult) {
        if (!active || !writing.remove(format)) return
        when (result) {
            // add(), not +=: a Path is itself an Iterable<Path>, which makes += ambiguous.
            is WriteResult.Written -> written.add(result.path)
            is WriteResult.Unchanged -> unchanged++
            is WriteResult.Failed -> failure = failure ?: result.message
        }
        finishIfDone()
    }

    /** No answer from the page in time. */
    fun onTimeout() {
        if (!active) return
        reset()
        report(SyncStatus.Failed("The preview didn't answer in time; exports were left as they are."))
    }

    val isBusy: Boolean get() = active

    private companion object {
        const val MAX_RETRIES = 2
    }

    private fun finishIfDone() {
        if (waiting.isNotEmpty() || writing.isNotEmpty()) return
        val message = failure
        val status = if (message != null) SyncStatus.Failed(message) else SyncStatus.Synced(written.toList(), unchanged)
        reset()
        report(status)
    }

    private fun reset() {
        active = false
        rendered = false
        waiting.clear()
        writing.clear()
        written.clear()
        unchanged = 0
        failure = null
    }
}
