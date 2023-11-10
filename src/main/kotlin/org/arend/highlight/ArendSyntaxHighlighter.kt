package org.arend.highlight

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import org.arend.lexer.ArendLexerAdapter
import org.arend.parser.ParserMixin
import org.arend.psi.AREND_DOC_TOKENS
import org.arend.psi.AREND_GOALS
import org.arend.psi.ArendElementTypes
import org.arend.psi.ArendKeyword.Companion.AREND_KEYWORDS

class ArendSyntaxHighlighter : SyntaxHighlighterBase() {

    override fun getHighlightingLexer(): Lexer = ArendLexerAdapter()

    override fun getTokenHighlights(tokenType: IElementType?): Array<TextAttributesKey> =
            pack(map(tokenType)?.textAttributesKey)

    companion object {
        fun map(tokenType: IElementType?): ArendHighlightingColors? = when (tokenType) {
            ArendElementTypes.ID -> ArendHighlightingColors.IDENTIFIER
            ArendElementTypes.NUMBER, ArendElementTypes.NEGATIVE_NUMBER -> ArendHighlightingColors.NUMBER
            ArendElementTypes.STRING -> ArendHighlightingColors.STRING
            ArendElementTypes.PROP_KW, ArendElementTypes.SET, ArendElementTypes.UNIVERSE, ArendElementTypes.TRUNCATED_UNIVERSE -> ArendHighlightingColors.UNIVERSE
            in AREND_KEYWORDS -> ArendHighlightingColors.KEYWORD
            ArendElementTypes.UNDERSCORE, ArendElementTypes.APPLY_HOLE -> ArendHighlightingColors.IMPLICIT

            ArendElementTypes.INFIX, ArendElementTypes.POSTFIX, ArendElementTypes.LESS_OR_EQUALS, ArendElementTypes.GREATER_OR_EQUALS -> ArendHighlightingColors.OPERATORS
            ArendElementTypes.DOT -> ArendHighlightingColors.DOT
            ArendElementTypes.COMMA -> ArendHighlightingColors.COMMA
            ArendElementTypes.COLON -> ArendHighlightingColors.COLON
            ArendElementTypes.PIPE -> ArendHighlightingColors.PIPE
            ArendElementTypes.ARROW, ArendElementTypes.FAT_ARROW -> ArendHighlightingColors.ARROW

            ArendElementTypes.LBRACE, ArendElementTypes.RBRACE -> ArendHighlightingColors.BRACES
            ArendElementTypes.LPAREN, ArendElementTypes.RPAREN -> ArendHighlightingColors.PARENTHESIS

            ArendElementTypes.BLOCK_COMMENT -> ArendHighlightingColors.BLOCK_COMMENT
            ArendElementTypes.LINE_COMMENT -> ArendHighlightingColors.LINE_COMMENT
            ParserMixin.DOC_COMMENT -> ArendHighlightingColors.DOC_COMMENT

            TokenType.BAD_CHARACTER -> ArendHighlightingColors.BAD_CHARACTER

            ArendElementTypes.REF_IDENTIFIER -> ArendHighlightingColors.REF_IDENTIFIER
            in AREND_GOALS -> ArendHighlightingColors.GOALS
            in AREND_DOC_TOKENS -> ArendHighlightingColors.DOC_TOKENS

            else -> null
        }
    }
}
