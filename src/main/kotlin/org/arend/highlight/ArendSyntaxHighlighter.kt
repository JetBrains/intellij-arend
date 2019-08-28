package org.arend.highlight

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import org.arend.lexer.ArendLexerAdapter
import org.arend.psi.AREND_KEYWORDS
import org.arend.psi.ArendElementTypes

class ArendSyntaxHighlighter : SyntaxHighlighterBase() {

    override fun getHighlightingLexer(): Lexer = ArendLexerAdapter()

    override fun getTokenHighlights(tokenType: IElementType?): Array<TextAttributesKey> =
            pack(map(tokenType)?.textAttributesKey)

    companion object {
        fun map(tokenType: IElementType?): ArendHighlightingColors? = when (tokenType) {
            ArendElementTypes.ID -> ArendHighlightingColors.IDENTIFIER
            ArendElementTypes.NUMBER, ArendElementTypes.NEGATIVE_NUMBER -> ArendHighlightingColors.NUMBER
            ArendElementTypes.PROP_KW, ArendElementTypes.SET, ArendElementTypes.UNIVERSE, ArendElementTypes.TRUNCATED_UNIVERSE -> ArendHighlightingColors.UNIVERSE
            in AREND_KEYWORDS -> ArendHighlightingColors.KEYWORD
            ArendElementTypes.UNDERSCORE -> ArendHighlightingColors.IMPLICIT

            ArendElementTypes.INFIX, ArendElementTypes.POSTFIX -> ArendHighlightingColors.OPERATORS
            ArendElementTypes.DOT -> ArendHighlightingColors.DOT
            ArendElementTypes.COMMA -> ArendHighlightingColors.COMMA
            ArendElementTypes.COLON -> ArendHighlightingColors.COLON
            ArendElementTypes.PIPE -> ArendHighlightingColors.PIPE
            ArendElementTypes.ARROW, ArendElementTypes.FAT_ARROW -> ArendHighlightingColors.ARROW

            ArendElementTypes.LBRACE, ArendElementTypes.RBRACE -> ArendHighlightingColors.BRACES
            ArendElementTypes.LPAREN, ArendElementTypes.RPAREN -> ArendHighlightingColors.PARENTHESIS

            ArendElementTypes.BLOCK_COMMENT -> ArendHighlightingColors.BLOCK_COMMENT
            ArendElementTypes.LINE_COMMENT -> ArendHighlightingColors.LINE_COMMENT
            ArendElementTypes.LINE_DOC_COMMENT_START, ArendElementTypes.LINE_DOC_TEXT,
                ArendElementTypes.BLOCK_DOC_COMMENT_START, ArendElementTypes.BLOCK_DOC_TEXT, ArendElementTypes.BLOCK_COMMENT_END -> ArendHighlightingColors.DOC_COMMENT

            TokenType.BAD_CHARACTER -> ArendHighlightingColors.BAD_CHARACTER
            else -> null
        }
    }
}
