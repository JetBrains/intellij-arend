package org.arend.parser

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
import org.arend.lexer.ArendLexerAdapter
import org.arend.psi.AREND_COMMENTS
import org.arend.psi.AREND_WHITE_SPACES
import org.arend.psi.ArendElementTypes
import org.arend.psi.ArendFile
import org.arend.psi.stubs.ArendFileStub

class ArendParserDefinition : ParserDefinition {

    override fun createLexer(project: Project): Lexer = ArendLexerAdapter()

    override fun getWhitespaceTokens(): TokenSet = AREND_WHITE_SPACES

    override fun getCommentTokens(): TokenSet = AREND_COMMENTS

    override fun getStringLiteralElements(): TokenSet = TokenSet.EMPTY

    override fun createParser(project: Project): PsiParser = ArendParser()

    override fun getFileNodeType(): IFileElementType = ArendFileStub.Type

    override fun createFile(viewProvider: FileViewProvider): PsiFile = ArendFile(viewProvider)

    override fun spaceExistenceTypeBetweenTokens(
            left: ASTNode,
            right: ASTNode
    ): ParserDefinition.SpaceRequirements = ParserDefinition.SpaceRequirements.MAY

    override fun createElement(node: ASTNode): PsiElement =
            ArendElementTypes.Factory.createElement(node)
}
