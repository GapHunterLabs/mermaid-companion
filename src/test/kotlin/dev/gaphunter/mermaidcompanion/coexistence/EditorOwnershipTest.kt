package dev.gaphunter.mermaidcompanion.coexistence

import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.gaphunter.mermaidcompanion.lang.MermaidFileType

/**
 * The test platform (2025.2) doesn't include JetBrains's Mermaid plugin,
 * so its presence is simulated with [NativeMermaid.enabledOverride] and its
 * file type is stood in for by plain text -- what's under test is that the
 * associations really move and that this plugin really stops claiming the
 * files, through the platform's own FileTypeManager.
 */
class EditorOwnershipTest : BasePlatformTestCase() {

    private val otherEditor = PlainTextFileType.INSTANCE

    override fun setUp() {
        super.setUp()
        NativeMermaid.enabledOverride = true
    }

    override fun tearDown() {
        try {
            EditorOwnership.reclaimFrom(otherEditor)
            EditorOwnership.noticeShown = false
            NativeMermaid.enabledOverride = null
        } finally {
            super.tearDown()
        }
    }

    private fun typeOf(name: String) = FileTypeManager.getInstance().getFileTypeByFileName(name)

    private fun claimsByContent(name: String) = MermaidFileType.isMyFileType(LightVirtualFile(name))

    fun testByDefaultMermaidCompanionOwnsTheFiles() {
        assertEquals(MermaidFileType, typeOf("flow.mmd"))
        assertEquals(MermaidFileType, typeOf("flow.mermaid"))
        assertTrue(claimsByContent("flow.mmd"))
    }

    fun testDeferringHandsBothExtensionsToTheOtherEditor() {
        EditorOwnership.deferTo(otherEditor)
        assertEquals(otherEditor, typeOf("flow.mmd"))
        assertEquals(otherEditor, typeOf("flow.mermaid"))
        assertFalse(claimsByContent("flow.mmd"))
    }

    fun testReclaimingGivesThemBack() {
        EditorOwnership.deferTo(otherEditor)
        EditorOwnership.reclaimFrom(otherEditor)
        assertEquals(MermaidFileType, typeOf("flow.mmd"))
        assertTrue(claimsByContent("flow.mmd"))
        assertFalse(EditorOwnership.deferEditingToNative)
    }

    fun testADeferralIsIgnoredOnceJetBrainsMermaidIsGone() {
        EditorOwnership.deferTo(otherEditor)
        NativeMermaid.enabledOverride = false
        assertTrue(claimsByContent("flow.mmd"))
    }

    fun testNonMermaidFilesAreNeverClaimed() {
        assertFalse(claimsByContent("notes.md"))
        EditorOwnership.deferTo(otherEditor)
        assertFalse(claimsByContent("notes.md"))
    }

    fun testTheChoiceIsOfferedOnlyOnceAndOnlyWhenItMatters() {
        assertTrue(EditorOwnership.shouldOfferChoice(nativeEnabled = true, deferred = false, noticeShown = false))
        assertFalse("no JetBrains Mermaid", EditorOwnership.shouldOfferChoice(nativeEnabled = false, deferred = false, noticeShown = false))
        assertFalse("already deferred", EditorOwnership.shouldOfferChoice(nativeEnabled = true, deferred = true, noticeShown = false))
        assertFalse("already asked", EditorOwnership.shouldOfferChoice(nativeEnabled = true, deferred = false, noticeShown = true))
    }
}
