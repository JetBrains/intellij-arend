package org.arend.refactoring

import com.intellij.lang.refactoring.NamesValidator
import com.intellij.openapi.project.Project
import com.intellij.psi.tree.IElementType
import org.arend.documentation.ArendKeyword.Companion.AREND_KEYWORDS
import org.arend.lexer.ArendLexerAdapter
import org.arend.psi.ArendElementTypes

class ArendNamesValidator : NamesValidator {
    override fun isKeyword(name: String, project: Project?): Boolean =
        getLexerType(name) in AREND_KEYWORDS

    override fun isIdentifier(name: String, project: Project?): Boolean {
        val tokenType = getLexerType(name)
        return tokenType == ArendElementTypes.ID || tokenType == ArendElementTypes.REPL_COMMAND
    }

    private fun getLexerType(text : String): IElementType? {
        val lexer = ArendLexerAdapter()
        lexer.start(text)
        return if (lexer.tokenEnd == text.length) lexer.tokenType else null
    }

    companion object {
        @JvmField
        val INSTANCE = ArendNamesValidator()
    }
}
