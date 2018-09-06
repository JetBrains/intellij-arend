package com.jetbrains.arend.ide.parser

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
import com.jetbrains.arend.ide.lexer.ArdLexerAdapter
import com.jetbrains.arend.ide.psi.ArdElementTypes
import com.jetbrains.arend.ide.psi.ArdFile
import com.jetbrains.arend.ide.psi.VC_COMMENTS
import com.jetbrains.arend.ide.psi.VC_WHITE_SPACES
import com.jetbrains.arend.ide.psi.stubs.ArdFileStub

class ArdParserDefinition : ParserDefinition {

    override fun createLexer(project: Project): Lexer = ArdLexerAdapter()

    override fun getWhitespaceTokens(): TokenSet = VC_WHITE_SPACES

    override fun getCommentTokens(): TokenSet = VC_COMMENTS

    override fun getStringLiteralElements(): TokenSet = TokenSet.EMPTY

    override fun createParser(project: Project): PsiParser = ArdParser()

    override fun getFileNodeType(): IFileElementType = ArdFileStub.Type

    override fun createFile(viewProvider: FileViewProvider): PsiFile = ArdFile(viewProvider)

    override fun spaceExistanceTypeBetweenTokens(
            left: ASTNode,
            right: ASTNode
    ): ParserDefinition.SpaceRequirements = ParserDefinition.SpaceRequirements.MAY

    override fun createElement(node: ASTNode): PsiElement =
            ArdElementTypes.Factory.createElement(node)
}
