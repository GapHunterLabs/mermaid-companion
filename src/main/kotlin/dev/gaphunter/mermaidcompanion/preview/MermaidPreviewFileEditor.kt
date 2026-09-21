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
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import com.intellij.util.Alarm
import dev.gaphunter.mermaidcompanion.review.ReviewPrompt
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import java.io.IOException
import java.nio.file.Files
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

private const val DEBOUNCE_MS = 300
private const val ZOOM_STEP = 1.25

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
 * environment -- `JBCefApp.isSupported()` is checked once at
 * construction and logged, since this is genuinely environment-
 * dependent.
 */
class MermaidPreviewFileEditor(private val project: Project, private val file: VirtualFile) : UserDataHolderBase(), FileEditor {

    private val panel = JPanel(BorderLayout())
    private val document: Document? = FileDocumentManager.getInstance().getDocument(file)
    private val zoomLabel = JLabel("100%")
    private var browser: JBCefBrowser? = null

    // loadHTML() is fire-and-forget -- the shell page (and the
    // window.gapHunterRender function it defines) isn't actually
    // loaded yet the instant loadHTML() returns. Calling
    // executeJavaScript() before onLoadEnd fires hits a ReferenceError
    // in the page (function not defined yet) with nothing visible on
    // either side -- the preview just stays blank. Confirmed by hitting
    // this exact blank-preview symptom in a real runIde sandbox.
    @Volatile
    private var pageLoaded = false

    init {
        val supported = JBCefApp.isSupported()
        thisLogger().info("Mermaid Companion preview: JBCefApp.isSupported()=$supported")
        if (supported) {
            setUpBrowser()
        } else {
            panel.add(
                JLabel("Diagram preview isn't available: this IDE build doesn't support JCEF.", SwingConstants.CENTER),
                BorderLayout.CENTER,
            )
        }
    }

    private fun setUpBrowser() {
        val newBrowser = JBCefBrowser()
        browser = newBrowser
        panel.add(createToolbar(), BorderLayout.NORTH)
        panel.add(newBrowser.component, BorderLayout.CENTER)

        // Messages from the page (exports, zoom level). Created before the
        // page loads, as JBCefJSQuery requires.
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

        newBrowser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(cefBrowser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
                if (!frame.isMain) return
                pageLoaded = true
                js("window.gapHunterBridge = function(p) { ${bridge.inject("p")} };")
                js("window.gapHunterSetTheme('${PreviewTheme.current.id}');")
                renderCurrentDocument()
            }
        }, newBrowser.cefBrowser)

        newBrowser.loadHTML(PreviewPage.html(MermaidBundle.scriptContent, PreviewPage.companionScript))
    }

    private fun js(code: String) {
        if (!pageLoaded) return
        val cefBrowser = browser?.cefBrowser ?: return
        cefBrowser.executeJavaScript(code, cefBrowser.url, 0)
    }

    private fun renderCurrentDocument() {
        if (!pageLoaded) return
        val text = document?.text ?: return
        js("window.gapHunterRender(`${MermaidJsEscaper.escapeForTemplateLiteral(text)}`);")
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
        }
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
        }
        val toolbar = ActionManager.getInstance().createActionToolbar("MermaidCompanionPreview", group, true)
        toolbar.targetComponent = panel
        return JPanel(BorderLayout()).apply {
            add(toolbar.component, BorderLayout.CENTER)
            add(zoomLabel.apply { border = javax.swing.BorderFactory.createEmptyBorder(0, 8, 0, 8) }, BorderLayout.EAST)
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
    }
}
