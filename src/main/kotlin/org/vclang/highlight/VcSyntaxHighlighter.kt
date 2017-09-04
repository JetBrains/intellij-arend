package org.vclang.highlight

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import org.vclang.lexer.VcLexerAdapter
import org.vclang.psi.VC_KEYWORDS
import org.vclang.psi.VcElementTypes.ARROW
import org.vclang.psi.VcElementTypes.BLOCK_COMMENT
import org.vclang.psi.VcElementTypes.COLON
import org.vclang.psi.VcElementTypes.COLONCOLON
import org.vclang.psi.VcElementTypes.COMMA
import org.vclang.psi.VcElementTypes.DOT
import org.vclang.psi.VcElementTypes.FAT_ARROW
import org.vclang.psi.VcElementTypes.GOAL
import org.vclang.psi.VcElementTypes.GRAVE
import org.vclang.psi.VcElementTypes.INFIX
import org.vclang.psi.VcElementTypes.LBRACE
import org.vclang.psi.VcElementTypes.LINE_COMMENT
import org.vclang.psi.VcElementTypes.LPAREN
import org.vclang.psi.VcElementTypes.NUMBER
import org.vclang.psi.VcElementTypes.PIPE
import org.vclang.psi.VcElementTypes.PREFIX
import org.vclang.psi.VcElementTypes.RBRACE
import org.vclang.psi.VcElementTypes.RPAREN
import org.vclang.psi.VcElementTypes.SET
import org.vclang.psi.VcElementTypes.TRUNCATED_UNIVERSE
import org.vclang.psi.VcElementTypes.UNDERSCORE
import org.vclang.psi.VcElementTypes.UNIVERSE
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors as Default

class VcSyntaxHighlighter : SyntaxHighlighterBase() {

    override fun getHighlightingLexer(): Lexer = VcLexerAdapter()

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> =
            pack(map(tokenType)?.textAttributesKey)

    companion object {
        fun map(tokenType: IElementType?): VcHighlightingColors? = when (tokenType) {
            PREFIX -> VcHighlightingColors.IDENTIFIER
            NUMBER -> VcHighlightingColors.NUMBER
            in VC_KEYWORDS -> VcHighlightingColors.KEYWORD
            SET, UNIVERSE, TRUNCATED_UNIVERSE -> VcHighlightingColors.UNIVERSE
            UNDERSCORE, GOAL -> VcHighlightingColors.IMPLICIT

            INFIX -> VcHighlightingColors.OPERATORS
            DOT -> VcHighlightingColors.DOT
            COMMA -> VcHighlightingColors.COMMA
            COLON, COLONCOLON -> VcHighlightingColors.COLON
            GRAVE -> VcHighlightingColors.GRAVE
            PIPE -> VcHighlightingColors.COMMA
            ARROW, FAT_ARROW -> VcHighlightingColors.ARROW

            LBRACE, RBRACE -> VcHighlightingColors.BRACES
            LPAREN, RPAREN -> VcHighlightingColors.PARENTHESIS

            BLOCK_COMMENT -> VcHighlightingColors.BLOCK_COMMENT
            LINE_COMMENT -> VcHighlightingColors.LINE_COMMENT

            TokenType.BAD_CHARACTER -> VcHighlightingColors.BAD_CHARACTER
            else -> null
        }
    }
}
