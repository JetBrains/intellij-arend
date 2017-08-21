package org.vclang.lang.core.parser

import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import org.vclang.lang.core.lexer.VcLexerAdapter
import org.vclang.lang.core.psi.VC_COMMENTS
import org.vclang.lang.core.psi.VC_WHITE_SPACES
import org.vclang.lang.core.psi.VcFile
import org.vclang.lang.core.psi.VcTypes
import org.vclang.lang.core.stubs.VcFileStub

class VcParserDefinition : ParserDefinition {

    override fun createLexer(project: Project): Lexer = VcLexerAdapter()

    override fun getWhitespaceTokens(): TokenSet = VC_WHITE_SPACES

    override fun getCommentTokens(): TokenSet = VC_COMMENTS

    override fun getStringLiteralElements(): TokenSet = TokenSet.EMPTY

    override fun createParser(project: Project): PsiParser = VcParser()

    override fun getFileNodeType(): IFileElementType = VcFileStub.Type

    override fun createFile(viewProvider: FileViewProvider): PsiFile = VcFile(viewProvider)

    override fun spaceExistanceTypeBetweenTokens(
            left: ASTNode,
            right: ASTNode
    ): ParserDefinition.SpaceRequirements = ParserDefinition.SpaceRequirements.MAY

    override fun createElement(node: ASTNode): PsiElement = VcTypes.Factory.createElement(node)
}
