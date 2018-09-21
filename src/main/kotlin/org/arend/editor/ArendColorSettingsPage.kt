package org.arend.editor

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import org.arend.ArendIcons
import org.arend.highlight.ArendHighlightingColors
import org.arend.highlight.ArendSyntaxHighlighter
import javax.swing.Icon

class ArendColorSettingsPage : ColorSettingsPage {

    override fun getIcon(): Icon? = ArendIcons.AREND

    override fun getHighlighter(): SyntaxHighlighter = ArendSyntaxHighlighter()

    override fun getDemoText(): String = DEMO_TEXT

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey>?
            = null

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = DESCRIPTORS

    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY

    override fun getDisplayName(): String = "Arend"

    companion object {
        private val DESCRIPTORS = ArendHighlightingColors.values()
                .map { it.attributesDescriptor }
                .toTypedArray()

        // TODO: update demo text
        private const val DEMO_TEXT =
            "\\import Data.Bool\n" +
            "\n" +
            "\\class Semigroup {\n" +
            "  \\field X : \\Type0\n" +
            "  \\field op : X -> X -> X\n" +
            "  \\field assoc : \\Pi (x y z : X) -> op (op x y) z = op x (op y z)\n" +
            "}\n" +
            "\n" +
            "\\func xor-semigroup => \\new Semigroup { X => Bool | op => xor | assoc => {?} }"
    }
}
