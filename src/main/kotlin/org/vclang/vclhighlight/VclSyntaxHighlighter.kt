package org.vclang.vclhighlight

import com.intellij.lexer.FlexAdapter
import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import org.vclang.highlight.VcHighlightingColors
import org.vclang.lexer.VclLexer
import org.vclang.vclpsi.VclElementTypes
import java.io.Reader
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors as Default

class VclSyntaxHighlighter : SyntaxHighlighterBase() {
    override fun getTokenHighlights(tokenType: IElementType?): Array<TextAttributesKey> =
        pack(map(tokenType)?.textAttributesKey)

    override fun getHighlightingLexer(): Lexer = FlexAdapter(VclLexer(null as Reader?))

    companion object {
        fun map(tokenType: IElementType?) = when (tokenType) {
            VclElementTypes.BINARY, VclElementTypes.DEPS, VclElementTypes.SOURCE, VclElementTypes.MODULES -> VcHighlightingColors.KEYWORD
            VclElementTypes.COLON -> VcHighlightingColors.COLON
            VclElementTypes.BLOCK_COMMENT -> VcHighlightingColors.BLOCK_COMMENT
            VclElementTypes.LINE_COMMENT -> VcHighlightingColors.LINE_COMMENT
            TokenType.BAD_CHARACTER -> VcHighlightingColors.BAD_CHARACTER
            else -> null
        }
    }
}