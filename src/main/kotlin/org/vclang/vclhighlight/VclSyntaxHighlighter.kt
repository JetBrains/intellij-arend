package org.vclang.vclhighlight

import com.intellij.lexer.FlexAdapter
import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import org.vclang.lang.lexer.VclLexer
import org.vclang.vclpsi.VclElementTypes
import java.io.Reader
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors as Default

class VclSyntaxHighlighter : SyntaxHighlighterBase() {
    override fun getTokenHighlights(tokenType: IElementType?): Array<TextAttributesKey> =
        pack(map(tokenType))

    override fun getHighlightingLexer(): Lexer = FlexAdapter(VclLexer(null as Reader?))

    companion object {
        fun map(tokenType: IElementType?): TextAttributesKey? = when (tokenType) {
            VclElementTypes.BINARY -> Default.KEYWORD
            VclElementTypes.DEPS -> Default.KEYWORD
            VclElementTypes.SOURCE -> Default.KEYWORD
            VclElementTypes.MODULES -> Default.KEYWORD
            VclElementTypes.COLON -> Default.COMMA
            TokenType.BAD_CHARACTER -> HighlighterColors.BAD_CHARACTER
            else -> null
        }
    }
}