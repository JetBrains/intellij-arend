package com.jetbrains.arend.ide.highlight

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.jetbrains.arend.ide.lexer.ArdLexerAdapter
import com.jetbrains.arend.ide.psi.ArdElementTypes
import com.jetbrains.arend.ide.psi.VC_KEYWORDS
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors as Default

class ArdSyntaxHighlighter : SyntaxHighlighterBase() {

    override fun getHighlightingLexer(): Lexer = ArdLexerAdapter()

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> =
            pack(map(tokenType)?.textAttributesKey)

    companion object {
        fun map(tokenType: IElementType?): ArdHighlightingColors? = when (tokenType) {
            ArdElementTypes.ID -> ArdHighlightingColors.IDENTIFIER
            ArdElementTypes.NUMBER -> ArdHighlightingColors.NUMBER
            in VC_KEYWORDS -> ArdHighlightingColors.KEYWORD
            ArdElementTypes.SET, ArdElementTypes.UNIVERSE, ArdElementTypes.TRUNCATED_UNIVERSE -> ArdHighlightingColors.UNIVERSE
            ArdElementTypes.UNDERSCORE, ArdElementTypes.GOAL -> ArdHighlightingColors.IMPLICIT

            ArdElementTypes.INFIX, ArdElementTypes.POSTFIX -> ArdHighlightingColors.OPERATORS
            ArdElementTypes.DOT -> ArdHighlightingColors.DOT
            ArdElementTypes.COMMA -> ArdHighlightingColors.COMMA
            ArdElementTypes.COLON -> ArdHighlightingColors.COLON
            ArdElementTypes.PIPE -> ArdHighlightingColors.COMMA
            ArdElementTypes.ARROW, ArdElementTypes.FAT_ARROW -> ArdHighlightingColors.ARROW

            ArdElementTypes.LBRACE, ArdElementTypes.RBRACE -> ArdHighlightingColors.BRACES
            ArdElementTypes.LPAREN, ArdElementTypes.RPAREN -> ArdHighlightingColors.PARENTHESIS

            ArdElementTypes.BLOCK_COMMENT -> ArdHighlightingColors.BLOCK_COMMENT
            ArdElementTypes.LINE_COMMENT -> ArdHighlightingColors.LINE_COMMENT

            TokenType.BAD_CHARACTER -> ArdHighlightingColors.BAD_CHARACTER
            else -> null
        }
    }
}
