package dev.gaphunter.mermaidcompanion.preview

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.util.Alarm
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

private const val DEBOUNCE_MS = 300

/**
 * A "Preview" tab next to the text editor (FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR,
 * same non-intrusive pattern as Spreadsheet Companion's XlsxViewerEditor
 * -- the text editor is never replaced). Renders via the real
 * mermaid.js (bundled, see MermaidBundle) inside JBCefBrowser; the
 * plugin's own highlighting/MermaidSyntaxChecker are completely
 * unaffected either way.
 *
 * Gracefully degrades if JCEF isn't supported in this IDE build/
 * environment -- `JBCefApp.isSupported()` is checked once at
 * construction and logged, since this is genuinely environment-
 * dependent (see INTELLIJ_PLATFORM_KNOWLEDGE.md section G).
 */
class MermaidPreviewFileEditor(private val file: VirtualFile) : UserDataHolderBase(), FileEditor {

    private val panel = JPanel(BorderLayout())
    private val document: Document? = FileDocumentManager.getInstance().getDocument(file)
    private var browser: JBCefBrowser? = null

    init {
        val supported = JBCefApp.isSupported()
        thisLogger().warn("Mermaid Companion preview: JBCefApp.isSupported()=$supported")
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
        panel.add(newBrowser.component, BorderLayout.CENTER)
        newBrowser.loadHTML(shellHtml())

        // Debounced re-render, never on every keystroke -- same "heavy
        // work never blocks typing" spirit as CONSTITUTION.md S6's first
        // rule, applied to a JS render call instead of a pooled thread.
        val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, newBrowser)
        document?.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                alarm.cancelAllRequests()
                alarm.addRequest({ renderCurrentDocument() }, DEBOUNCE_MS)
            }
        }, newBrowser)

        renderCurrentDocument()
    }

    private fun renderCurrentDocument() {
        val activeBrowser = browser ?: return
        val text = document?.text ?: return
        val escaped = MermaidJsEscaper.escapeForTemplateLiteral(text)
        val cefBrowser = activeBrowser.cefBrowser
        cefBrowser.executeJavaScript("window.gapHunterRenderMermaid(`$escaped`);", cefBrowser.url, 0)
    }

    private fun shellHtml(): String {
        val script = MermaidBundle.scriptContent
        return """
            <html>
              <body style="margin:0;padding:8px;background:#ffffff;font-family:sans-serif;">
                <div id="mermaid-output"></div>
                <script>$script</script>
                <script>
                  window.mermaid.initialize({ startOnLoad: false });
                  window.gapHunterRenderMermaid = function(text) {
                    var container = document.getElementById('mermaid-output');
                    window.mermaid.render('gap-hunter-mermaid-svg', text)
                      .then(function(result) { container.innerHTML = result.svg; })
                      .catch(function(err) { container.innerText = 'Diagram error: ' + err; });
                  };
                </script>
              </body>
            </html>
        """.trimIndent()
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
    // closes, not just a cosmetic issue.
    override fun dispose() {
        browser?.dispose()
    }
}
