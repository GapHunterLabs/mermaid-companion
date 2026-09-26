package dev.gaphunter.mermaidcompanion.preview

import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import com.intellij.util.Alarm
import dev.gaphunter.mermaidcompanion.licensing.CheckLicense
import dev.gaphunter.mermaidcompanion.licensing.ProGate
import dev.gaphunter.mermaidcompanion.review.ReviewPrompt
import dev.gaphunter.mermaidcompanion.sync.AutoSyncSettings
import dev.gaphunter.mermaidcompanion.sync.ExportFormat
import dev.gaphunter.mermaidcompanion.sync.SvgNormalizer
import dev.gaphunter.mermaidcompanion.sync.SyncCoordinator
import dev.gaphunter.mermaidcompanion.sync.SyncStatus
import dev.gaphunter.mermaidcompanion.sync.SyncWriter
import dev.gaphunter.mermaidcompanion.sync.WriteResult
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import java.io.IOException
import java.nio.file.Files
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

private const val DEBOUNCE_MS = 300
private const val ZOOM_STEP = 1.25
private const val SYNC_TIMEOUT_MS = 20_000

/**
 * A "Preview" tab next to the text editor (FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR,
 * same non-intrusive pattern as Spreadsheet Companion's XlsxViewerEditor
 * -- the text editor is never replaced). Renders via the real
 * mermaid.js (bundled, see MermaidBundle) inside JBCefBrowser, with zoom
 * and pan (Ctrl+wheel / drag / toolbar), a diagram theme chosen
 * independently of the editor theme, and SVG/PNG export -- the three
 * things reviewers of JetBrains's own Mermaid plugin keep asking for.
 *
 * Opens for any .mmd/.mermaid file by name, so it is also available when
 * the file is edited with JetBrains's bundled Mermaid editor instead of
 * this plugin's (see EditorOwnership).
 *
 * Gracefully degrades if JCEF isn't supported in this IDE build/
 * environment -- `JBCefApp.isSupported()` is checked once, the first time
 * the tab is shown, and logged, since this is genuinely environment-
 * dependent.
 */
class MermaidPreviewFileEditor(private val project: Project, private val file: VirtualFile) : UserDataHolderBase(), FileEditor {

    private val panel = JPanel(BorderLayout())
    private val document: Document? = FileDocumentManager.getInstance().getDocument(file)
    private val zoomLabel = JLabel("100%")
    private val syncLabel = JLabel("")
    private var browser: JBCefBrowser? = null

    // Pro: keep SVG/PNG exports in step with the diagram on every save.
    private val settings = project.service<AutoSyncSettings>()
    private var syncTimeout: Alarm? = null
    private val coordinator = SyncCoordinator(
        enabledFormats = { settings.enabledFormats(file.url) },
        licensed = { ProGate.isOpen() },
        requestRender = { token -> renderCurrentDocument(token) },
        requestExport = { format ->
            js(if (format == ExportFormat.SVG) "window.gapHunterExportSvg('sync');" else "window.gapHunterExportPng('sync');")
        },
        requestWrite = { format, bytes -> writeSyncExport(format, bytes) },
        report = { status -> showSyncStatus(status) },
    )

    // loadHTML() is fire-and-forget -- the shell page (and the
    // window.gapHunterRender function it defines) isn't actually
    // loaded yet the instant loadHTML() returns. Calling
    // executeJavaScript() before onLoadEnd fires hits a ReferenceError
    // in the page (function not defined yet) with nothing visible on
    // either side -- the preview just stays blank. Confirmed by hitting
    // this exact blank-preview symptom in a real runIde sandbox.
    @Volatile
    private var pageLoaded = false

    private var initialized = false

    init {
        // Nothing heavy here: the platform builds every editor of a file on
        // the UI thread when the file opens, Text tab included. Starting
        // Chromium and reading the ~3 MB mermaid.js at that point froze the
        // UI for 17 s on IntelliJ IDEA 2026.2 (the IDE's freeze report named
        // this plugin). The browser is created the first time the Preview
        // tab is shown; the script is read on a background thread meanwhile.
        ApplicationManager.getApplication().executeOnPooledThread { MermaidBundle.scriptContent }
    }

    override fun selectNotify() {
        if (initialized) return
        initialized = true
        val supported = jcefAvailable()
        thisLogger().info("Mermaid Companion preview: JBCefApp.isSupported()=$supported")
        if (supported) {
            setUpBrowser()
        } else {
            panel.add(
                JLabel("Diagram preview isn't available: this IDE build doesn't support JCEF.", SwingConstants.CENTER),
                BorderLayout.CENTER,
            )
        }
        panel.revalidate()
    }

    /**
     * Also false when JCEF's classes can't be reached from this plugin at
     * all -- a LinkageError, as on IntelliJ IDEA 2026.2 before JCEF was
     * declared as a dependency -- so the tab shows the message below
     * instead of failing to open.
     */
    private fun jcefAvailable(): Boolean = try {
        JBCefApp.isSupported()
    } catch (e: LinkageError) {
        thisLogger().warn("Mermaid Companion preview: JCEF classes aren't available to this plugin", e)
        false
    }

    private fun setUpBrowser() {
        val newBrowser = JBCefBrowser()
        browser = newBrowser

        // Messages from the page (exports, zoom level). JBCefJSQuery has to
        // exist before Chromium starts, and Chromium starts as soon as the
        // browser's component joins a visible panel -- which the Preview tab
        // already is by now -- so the component is added last, below.
        // (Adding it first left window.cefQuery_* undefined in the page.)
        val bridge = JBCefJSQuery.create(newBrowser as JBCefBrowserBase)
        bridge.addHandler { payload ->
            BridgeMessage.parse(payload)?.let { message ->
                ApplicationManager.getApplication().invokeLater { if (!project.isDisposed) handle(message) }
            }
            null
        }

        // Debounced re-render, never on every keystroke -- same "heavy
        // work never blocks typing" principle applied catalog-wide,
        // applied here to a JS render call instead of a pooled thread.
        val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, newBrowser)
        document?.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                alarm.cancelAllRequests()
                alarm.addRequest({ renderCurrentDocument() }, DEBOUNCE_MS)
            }
        }, newBrowser)

        syncTimeout = Alarm(Alarm.ThreadToUse.SWING_THREAD, newBrowser)

        // Pro: a save of this file syncs its exports. Runs after the save has
        // been handed off, never inside it, and does nothing unless the
        // preview page is up (nothing to export from before that).
        ApplicationManager.getApplication().messageBus.connect(newBrowser)
            .subscribe(FileDocumentManagerListener.TOPIC, object : FileDocumentManagerListener {
                override fun beforeDocumentSaving(savedDocument: Document) {
                    if (savedDocument !== document || !pageLoaded) return
                    ApplicationManager.getApplication().invokeLater { if (!project.isDisposed) coordinator.onSave() }
                }
            })

        newBrowser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(cefBrowser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
                if (!frame.isMain) return
                pageLoaded = true
                js("window.gapHunterBridge = function(p) { ${bridge.inject("p")} };")
                js("window.gapHunterSetTheme('${PreviewTheme.current.id}');")
                renderCurrentDocument()
            }
        }, newBrowser.cefBrowser)

        panel.add(createToolbar(), BorderLayout.NORTH)
        panel.add(newBrowser.component, BorderLayout.CENTER)

        // The page inlines mermaid.js; build it off the UI thread (the
        // script may still be loading) and hand it to the browser after.
        ApplicationManager.getApplication().executeOnPooledThread {
            val html = PreviewPage.html(MermaidBundle.scriptContent, PreviewPage.companionScript)
            ApplicationManager.getApplication().invokeLater {
                if (!project.isDisposed && browser === newBrowser) newBrowser.loadHTML(html)
            }
        }
    }

    private fun js(code: String) {
        if (!pageLoaded) return
        val cefBrowser = browser?.cefBrowser ?: return
        cefBrowser.executeJavaScript(code, cefBrowser.url, 0)
    }

    /** [token] marks a render the sync flow asked for; its outcome is reported back under it. */
    private fun renderCurrentDocument(token: Int? = null) {
        if (!pageLoaded) return
        val text = document?.text ?: return
        val tokenArgument = if (token != null) ", $token" else ""
        js("window.gapHunterRender(`${MermaidJsEscaper.escapeForTemplateLiteral(text)}`$tokenArgument);")
        // Real render only -- never fires for a null document or before the
        // preview page has actually finished loading. Already debounced by
        // the Alarm above (documentChanged), so this doesn't fire per keystroke.
        if (text.isNotBlank()) ReviewPrompt.recordHit(project)
    }

    private fun handle(message: BridgeMessage) {
        when (message) {
            is BridgeMessage.Zoom -> zoomLabel.text = "${message.percent}%"
            is BridgeMessage.Svg -> save("svg", message.markup.toByteArray(Charsets.UTF_8))
            is BridgeMessage.Png -> save("png", message.bytes)
            is BridgeMessage.Failure -> notify(message.message, NotificationType.WARNING)
            is BridgeMessage.Rendered -> coordinator.onRendered(message.token)
            is BridgeMessage.RenderFailed -> coordinator.onRenderFailed(message.token, message.message)
            is BridgeMessage.RenderSuperseded -> coordinator.onRenderSuperseded(message.token)
            is BridgeMessage.SyncSvg -> coordinator.onExport(ExportFormat.SVG, SvgNormalizer.normalize(message.markup).toByteArray(Charsets.UTF_8))
            is BridgeMessage.SyncPng -> coordinator.onExport(ExportFormat.PNG, message.bytes)
            is BridgeMessage.SyncFailure -> coordinator.onExportFailed(message.message)
        }
    }

    /**
     * Writes one export next to the source file, off the UI thread, then hands
     * the outcome back to the sync flow on the UI thread.
     */
    private fun writeSyncExport(format: ExportFormat, bytes: ByteArray) {
        val dir = try {
            file.parent?.toNioPath()
        } catch (e: UnsupportedOperationException) {
            null
        }
        val target = dir?.let { SyncWriter.targetFor(it, file.name, format) }
        ApplicationManager.getApplication().executeOnPooledThread {
            val result = if (target == null) {
                WriteResult.Failed("This file isn't on the local disk, so its exports can't be written next to it.")
            } else {
                SyncWriter.write(target, bytes)
            }
            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) return@invokeLater
                if (result is WriteResult.Written) LocalFileSystem.getInstance().refreshAndFindFileByNioFile(result.path)
                coordinator.onWritten(format, result)
            }
        }
    }

    private fun showSyncStatus(status: SyncStatus) {
        // A sync that never hears back from the page must not stay "syncing" forever.
        syncTimeout?.cancelAllRequests()
        if (status is SyncStatus.Syncing) syncTimeout?.addRequest({ coordinator.onTimeout() }, SYNC_TIMEOUT_MS)

        val time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        when (status) {
            SyncStatus.Idle -> setSyncLabel("", null)
            SyncStatus.Syncing -> setSyncLabel("Syncing exports...", null)
            is SyncStatus.Synced -> setSyncLabel(
                "Exports synced $time",
                if (status.written.isEmpty()) "Already up to date." else "Wrote: " + status.written.joinToString(", ") { it.fileName.toString() },
            )
            is SyncStatus.RenderFailed -> setSyncLabel("Exports not updated (diagram doesn't render)", status.message)
            is SyncStatus.Failed -> {
                setSyncLabel("Export failed", status.message)
                notify(status.message, NotificationType.WARNING)
            }
            SyncStatus.NeedsLicense -> setSyncLabel("Pro feature", "Export on Save is part of Mermaid Companion Pro.")
            SyncStatus.NothingEnabled -> setSyncLabel("Turn on SVG or PNG first", "Use Export on Save (Pro) in this toolbar.")
        }
    }

    private fun setSyncLabel(text: String, tooltip: String?) {
        syncLabel.text = text
        syncLabel.toolTipText = tooltip
    }

    private fun save(extension: String, bytes: ByteArray) {
        // Spread on purpose: 2024.3 only has the vararg constructor, and
        // without it Kotlin binds the (String, String, String) overload that
        // 2025.2 added -- a NoSuchMethodError on 2024.3 (caught by verifyPlugin).
        val descriptor = FileSaverDescriptor("Export Diagram", "Save the rendered diagram as .$extension", *arrayOf(extension))
        // toNioPath() throws for a file that isn't on the local disk (remote
        // development, in-memory files) -- the dialog then just opens at its
        // default location.
        val startDir = try {
            file.parent?.toNioPath()
        } catch (e: UnsupportedOperationException) {
            null
        }
        val target = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)
            .save(startDir, ExportFiles.suggestedName(file.name, extension))
            ?.file?.toPath() ?: return
        try {
            Files.write(target, bytes)
        } catch (e: IOException) {
            notify("Couldn't write ${target.fileName}: ${e.message}", NotificationType.ERROR)
            return
        }
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(target)
        notify("Diagram exported to ${target.fileName}", NotificationType.INFORMATION)
    }

    private fun notify(content: String, type: NotificationType) {
        NotificationGroupManager.getInstance().getNotificationGroup("Mermaid Companion")
            .createNotification(content, type)
            .notify(project)
    }

    private fun createToolbar(): JComponent {
        val group = DefaultActionGroup().apply {
            add(jsAction("Zoom Out", AllIcons.General.ZoomOut, "window.gapHunterZoom(${1 / ZOOM_STEP});"))
            add(jsAction("Zoom In", AllIcons.General.ZoomIn, "window.gapHunterZoom($ZOOM_STEP);"))
            add(jsAction("Fit Diagram", AllIcons.General.FitContent, "window.gapHunterFit();"))
            add(jsAction("Actual Size (100%)", AllIcons.General.ActualZoom, "window.gapHunterActualSize();"))
            addSeparator()
            add(DefaultActionGroup("Diagram Theme", true).apply {
                templatePresentation.icon = AllIcons.Actions.Colors
                PreviewTheme.entries.forEach { add(ThemeAction(it)) }
            })
            add(jsAction("Export as SVG", AllIcons.ToolbarDecorator.Export, "window.gapHunterExportSvg();"))
            add(jsAction("Export as PNG", AllIcons.FileTypes.Image, "window.gapHunterExportPng();"))
            addSeparator()
            add(DefaultActionGroup("Export on Save (Pro)", true).apply {
                templatePresentation.icon = AllIcons.Actions.MenuSaveall
                ExportFormat.entries.forEach { add(ExportOnSaveAction(it)) }
                addSeparator()
                add(SyncNowAction())
            })
        }
        val toolbar = ActionManager.getInstance().createActionToolbar("MermaidCompanionPreview", group, true)
        toolbar.targetComponent = panel
        return JPanel(BorderLayout()).apply {
            add(toolbar.component, BorderLayout.CENTER)
            add(
                JPanel().apply {
                    layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.X_AXIS)
                    isOpaque = false
                    add(syncLabel.apply { border = javax.swing.BorderFactory.createEmptyBorder(0, 8, 0, 8) })
                    add(zoomLabel.apply { border = javax.swing.BorderFactory.createEmptyBorder(0, 8, 0, 8) })
                },
                BorderLayout.EAST,
            )
        }
    }

    private fun jsAction(text: String, icon: javax.swing.Icon, code: String): AnAction =
        object : DumbAwareAction(text, null, icon) {
            override fun actionPerformed(e: AnActionEvent) = js(code)
            override fun update(e: AnActionEvent) {
                e.presentation.isEnabled = pageLoaded
            }
            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
        }

    /**
     * One "keep this export up to date on every save" switch. Turning it on
     * needs the Pro license; without one the license dialog opens and the
     * switch stays off. If the export file already exists it asks before it
     * starts overwriting it on every save.
     */
    private inner class ExportOnSaveAction(private val format: ExportFormat) : ToggleAction(format.label) {
        override fun isSelected(e: AnActionEvent): Boolean = settings.isEnabled(file.url, format)

        override fun setSelected(e: AnActionEvent, state: Boolean) {
            if (state) {
                if (!ProGate.isOpen()) {
                    showSyncStatus(SyncStatus.NeedsLicense)
                    CheckLicense.requestLicense("Export on Save keeps the ${format.label} export in step with your diagram every time you save. It is part of Mermaid Companion Pro.")
                    return
                }
                if (!confirmOverwrite()) return
            }
            settings.setEnabled(file.url, format, state)
            // Whatever the toolbar said about an earlier attempt ("Turn on SVG or PNG
            // first", "Pro feature") is out of date now; a sync in progress keeps its own.
            if (!coordinator.isBusy) showSyncStatus(SyncStatus.Idle)
        }

        private fun confirmOverwrite(): Boolean {
            val dir = try {
                file.parent?.toNioPath()
            } catch (e: UnsupportedOperationException) {
                null
            } ?: return true
            val target = SyncWriter.targetFor(dir, file.name, format) ?: return true
            if (!Files.exists(target)) return true
            return Messages.showYesNoDialog(
                project,
                "${target.fileName} already exists next to this diagram. Overwrite it every time you save?",
                "Export on Save",
                Messages.getQuestionIcon(),
            ) == Messages.YES
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    }

    private inner class SyncNowAction : DumbAwareAction("Sync Now", "Update the enabled exports now", AllIcons.Actions.Refresh) {
        override fun actionPerformed(e: AnActionEvent) = coordinator.onSyncNow()
        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = pageLoaded && !coordinator.isBusy
        }
        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    }

    private inner class ThemeAction(private val theme: PreviewTheme) : ToggleAction(theme.displayName) {
        override fun isSelected(e: AnActionEvent): Boolean = PreviewTheme.current == theme
        override fun setSelected(e: AnActionEvent, state: Boolean) {
            if (!state) return
            PreviewTheme.current = theme
            js("window.gapHunterSetTheme('${theme.id}');")
        }
        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    }

    override fun getComponent(): JComponent = panel
    override fun getPreferredFocusedComponent(): JComponent = panel
    override fun getName(): String = "Preview"
    override fun setState(state: FileEditorState) {}
    override fun isModified(): Boolean = false
    override fun isValid(): Boolean = file.isValid
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
    override fun getFile(): VirtualFile = file

    // A JBCefBrowser has a native Chromium process behind it -- not
    // disposing it here leaks that process every time a preview tab
    // closes, not just a cosmetic issue. The JS query is disposed with it
    // (JBCefJSQuery registers itself as a child of the browser).
    override fun dispose() {
        browser?.dispose()
        browser = null
    }
}
