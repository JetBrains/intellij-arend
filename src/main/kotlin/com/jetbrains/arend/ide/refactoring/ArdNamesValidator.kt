package com.jetbrains.arend.ide.refactoring

import com.intellij.lang.refactoring.NamesValidator
import com.intellij.openapi.project.Project
import com.intellij.psi.tree.IElementType
import com.jetbrains.arend.ide.lexer.ArdLexerAdapter
import com.jetbrains.arend.ide.psi.ArdElementTypes
import com.jetbrains.arend.ide.psi.VC_KEYWORDS

class ArdNamesValidator : NamesValidator {

    override fun isKeyword(name: String, project: Project?): Boolean =
            getLexerType(name) in VC_KEYWORDS

    override fun isIdentifier(name: String, project: Project?): Boolean =
            isPrefixName(name) || isInfixName(name) || isPostfixName(name)

    companion object {
        fun isPrefixName(name: String): Boolean = getLexerType(name) == ArdElementTypes.ID && !containsComment(name)

        fun isInfixName(name: String): Boolean = getLexerType(name) == ArdElementTypes.INFIX && !containsComment(name)

        fun isPostfixName(name: String): Boolean = getLexerType(name) == ArdElementTypes.POSTFIX && !containsComment(name)

        private fun containsComment(name: String): Boolean = name.contains("--")
        fun getLexerType(text: String): IElementType? {
            val lexer = ArdLexerAdapter()
            lexer.start(text)
            return if (lexer.tokenEnd == text.length) lexer.tokenType else null
        }
    }
}
