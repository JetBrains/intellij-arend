package org.arend.injection

import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.EmptyLexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiUtil


class InjectionTextParserDefinition : ParserDefinition {
    override fun createLexer(project: Project?) = EmptyLexer()

    override fun createParser(project: Project?): PsiParser {
        throw UnsupportedOperationException("Not supported")
    }

    override fun createFile(viewProvider: FileViewProvider) = PsiInjectionTextFile(viewProvider)

    override fun getFileNodeType() = InjectionTextFileElementType

    override fun createElement(node: ASTNode?) = PsiUtil.NULL_PSI_ELEMENT!!

    override fun getWhitespaceTokens() = TokenSet.EMPTY!!

    override fun getStringLiteralElements() = TokenSet.EMPTY!!

    override fun getCommentTokens() = TokenSet.EMPTY!!
}