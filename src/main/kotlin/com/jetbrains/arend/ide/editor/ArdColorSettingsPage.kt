package com.jetbrains.arend.ide.editor

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import com.jetbrains.arend.ide.ArdIcons
import com.jetbrains.arend.ide.highlight.ArdHighlightingColors
import com.jetbrains.arend.ide.highlight.ArdSyntaxHighlighter
import javax.swing.Icon

class ArdColorSettingsPage : ColorSettingsPage {

    override fun getIcon(): Icon? = ArdIcons.AREND

    override fun getHighlighter(): SyntaxHighlighter = ArdSyntaxHighlighter()

    override fun getDemoText(): String = DEMO_TEXT

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey>? = null

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = DESCRIPTORS

    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY

    override fun getDisplayName(): String = "Arend"

    companion object {
        private val DESCRIPTORS = ArdHighlightingColors.values()
                .map { it.attributesDescriptor }
                .toTypedArray()

        // TODO: update demo text
        private val DEMO_TEXT = "\\import Data.Bool\n" +
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
