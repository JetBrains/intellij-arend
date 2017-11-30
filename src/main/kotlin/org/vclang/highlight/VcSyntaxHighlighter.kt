package org.vclang.highlight

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import org.vclang.lexer.VcLexerAdapter
import org.vclang.psi.VC_KEYWORDS
import org.vclang.psi.VcElementTypes
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors as Default

class VcSyntaxHighlighter : SyntaxHighlighterBase() {

    override fun getHighlightingLexer(): Lexer = VcLexerAdapter()

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> =
            pack(map(tokenType)?.textAttributesKey)

    companion object {
        fun map(tokenType: IElementType?): VcHighlightingColors? = when (tokenType) {
            VcElementTypes.ID -> VcHighlightingColors.IDENTIFIER
            VcElementTypes.NUMBER -> VcHighlightingColors.NUMBER
            in VC_KEYWORDS -> VcHighlightingColors.KEYWORD
            VcElementTypes.SET, VcElementTypes.UNIVERSE, VcElementTypes.TRUNCATED_UNIVERSE -> VcHighlightingColors.UNIVERSE
            VcElementTypes.UNDERSCORE, VcElementTypes.GOAL -> VcHighlightingColors.IMPLICIT

            VcElementTypes.INFIX, VcElementTypes.POSTFIX -> VcHighlightingColors.OPERATORS
            VcElementTypes.DOT -> VcHighlightingColors.DOT
            VcElementTypes.COMMA -> VcHighlightingColors.COMMA
            VcElementTypes.COLON -> VcHighlightingColors.COLON
            VcElementTypes.PIPE -> VcHighlightingColors.COMMA
            VcElementTypes.ARROW, VcElementTypes.FAT_ARROW -> VcHighlightingColors.ARROW

            VcElementTypes.LBRACE, VcElementTypes.RBRACE -> VcHighlightingColors.BRACES
            VcElementTypes.LPAREN, VcElementTypes.RPAREN -> VcHighlightingColors.PARENTHESIS

            VcElementTypes.BLOCK_COMMENT -> VcHighlightingColors.BLOCK_COMMENT
            VcElementTypes.LINE_COMMENT -> VcHighlightingColors.LINE_COMMENT

            TokenType.BAD_CHARACTER -> VcHighlightingColors.BAD_CHARACTER
            else -> null
        }
    }
}
