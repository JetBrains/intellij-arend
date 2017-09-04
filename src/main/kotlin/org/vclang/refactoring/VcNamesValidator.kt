package org.vclang.refactoring

import com.intellij.lang.refactoring.NamesValidator
import com.intellij.openapi.project.Project
import com.intellij.psi.tree.IElementType
import org.vclang.lexer.VcLexerAdapter
import org.vclang.psi.VC_KEYWORDS
import org.vclang.psi.VcElementTypes.INFIX
import org.vclang.psi.VcElementTypes.PREFIX

class VcNamesValidator : NamesValidator {

    override fun isKeyword(name: String, project: Project?): Boolean {
        return getLexerType(name) in VC_KEYWORDS
    }

    override fun isIdentifier(name: String, project: Project?): Boolean =
        (isPrefixName(name) || isInfixName(name)) && !containsComment(name)

    fun isPrefixName(name: String): Boolean = getLexerType(name) == PREFIX

    fun isInfixName(name: String): Boolean = getLexerType(name) == INFIX && !containsComment(name)

    private fun containsComment(name: String): Boolean = name.contains("--")

    private fun getLexerType(text: String): IElementType? {
        val lexer = VcLexerAdapter()
        lexer.start(text)
        return if (lexer.tokenEnd == text.length) lexer.tokenType else null
    }
}
