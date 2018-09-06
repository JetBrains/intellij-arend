package com.jetbrains.arend.ide.ardlhighlight

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.jetbrains.arend.ide.ardlpsi.ArdlElementTypes
import com.jetbrains.arend.ide.highlight.ArdHighlightingColors
import com.jetbrains.arend.ide.lexer.ArdlLexerAdapter
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors as Default

class ArdlSyntaxHighlighter : SyntaxHighlighterBase() {

    override fun getHighlightingLexer(): Lexer = ArdlLexerAdapter()

    override fun getTokenHighlights(tokenType: IElementType?): Array<TextAttributesKey> =
            pack(map(tokenType)?.textAttributesKey)

    companion object {
        fun map(tokenType: IElementType?) = when (tokenType) {
            ArdlElementTypes.BINARY, ArdlElementTypes.DEPS, ArdlElementTypes.SOURCE, ArdlElementTypes.MODULES -> ArdHighlightingColors.KEYWORD
            ArdlElementTypes.COLON -> ArdHighlightingColors.COLON
            ArdlElementTypes.BLOCK_COMMENT -> ArdHighlightingColors.BLOCK_COMMENT
            ArdlElementTypes.LINE_COMMENT -> ArdHighlightingColors.LINE_COMMENT
            TokenType.BAD_CHARACTER -> ArdHighlightingColors.BAD_CHARACTER
            else -> null
        }
    }
}