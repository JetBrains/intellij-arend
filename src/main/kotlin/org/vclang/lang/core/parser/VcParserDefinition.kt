package org.vclang.lang.core.parser

import com.intellij.lang.*
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.tree.*
import org.vclang.lang.VcLanguage
import org.vclang.lang.core.lexer.VcLexerAdapter
import org.vclang.lang.core.psi.*
import org.vclang.lang.core.psi.VcFile
import org.vclang.lang.core.psi.VcTypes

class VcParserDefinition : ParserDefinition {
    override fun createLexer(project: Project): Lexer = VcLexerAdapter()

    override fun getWhitespaceTokens(): TokenSet = VC_WHITE_SPACES

    override fun getCommentTokens(): TokenSet = VC_COMMENTS

    override fun getStringLiteralElements(): TokenSet = TokenSet.EMPTY

    override fun createParser(project: Project): PsiParser = VcParser()

    override fun getFileNodeType(): IFileElementType = IFileElementType(VcLanguage)

    override fun createFile(viewProvider: FileViewProvider): PsiFile = VcFile(viewProvider)

    override fun spaceExistanceTypeBetweenTokens(
            left: ASTNode,
            right: ASTNode
    ): ParserDefinition.SpaceRequirements = ParserDefinition.SpaceRequirements.MAY

    override fun createElement(node: ASTNode): PsiElement = VcTypes.Factory.createElement(node)
}
