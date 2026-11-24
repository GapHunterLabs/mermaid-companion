package dev.gaphunter.mermaidcompanion.highlighting

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import dev.gaphunter.mermaidcompanion.lang.MermaidFileType
import dev.gaphunter.mermaidcompanion.lang.MermaidHighlighterColors
import dev.gaphunter.mermaidcompanion.lang.MermaidSyntaxHighlighter
import javax.swing.Icon

/**
 * Settings | Editor | Color Scheme | Mermaid Companion: every color this plugin uses, on a sample
 * diagram. Named after the plugin so it never shadows the page of JetBrains's own Mermaid plugin.
 */
class MermaidColorSettingsPage : ColorSettingsPage {

    override fun getDisplayName(): String = "Mermaid Companion"

    override fun getIcon(): Icon? = MermaidFileType.icon

    override fun getHighlighter(): SyntaxHighlighter = MermaidSyntaxHighlighter()

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = DESCRIPTORS

    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey> = TAGS

    override fun getDemoText(): String = DEMO

    companion object {
        val DESCRIPTORS = arrayOf(
            AttributesDescriptor("Diagram type", MermaidHighlighterColors.DIAGRAM_TYPE),
            AttributesDescriptor("Keyword", MermaidHighlighterColors.KNOWN_KEYWORD),
            AttributesDescriptor("Comment", MermaidHighlighterColors.COMMENT),
            AttributesDescriptor("String", MermaidHighlighterColors.STRING),
            AttributesDescriptor("Arrow", MermaidHighlighterColors.ARROW),
            AttributesDescriptor("Braces and operators//Brackets", MermaidHighlighterColors.BRACKETS),
            AttributesDescriptor("Braces and operators//Parentheses", MermaidHighlighterColors.PARENS),
            AttributesDescriptor("Braces and operators//Braces", MermaidHighlighterColors.BRACES),
            AttributesDescriptor("Errors//Unterminated string", MermaidHighlighterColors.UNTERMINATED_STRING),
            AttributesDescriptor("Errors//Bad character", MermaidHighlighterColors.BAD_CHARACTER),
        )

        val TAGS: Map<String, TextAttributesKey> = mapOf(
            "type" to MermaidHighlighterColors.DIAGRAM_TYPE,
            "kw" to MermaidHighlighterColors.KNOWN_KEYWORD,
        )

        val DEMO = """
            %% Order flow
            <type>flowchart</type> LR
                <kw>subgraph</kw> checkout [Checkout]
                    cart[Cart] --> pay{"Paid?"}
                <kw>end</kw>
                pay -->|yes| ship((Ship))
                pay -.->|no| cart
                <kw>classDef</kw> done fill:#9f6
        """.trimIndent()
    }
}
