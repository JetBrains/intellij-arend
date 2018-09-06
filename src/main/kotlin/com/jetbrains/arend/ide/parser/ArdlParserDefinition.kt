package com.jetbrains.arend.ide.parser

import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.StubBuilder
import com.intellij.psi.stubs.*
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.IStubFileElementType
import com.intellij.psi.tree.TokenSet
import com.jetbrains.arend.ide.ardlpsi.ArdlElementTypes
import com.jetbrains.arend.ide.ardlpsi.ArdlElementTypes.BLOCK_COMMENT
import com.jetbrains.arend.ide.ardlpsi.ArdlElementTypes.LINE_COMMENT
import com.jetbrains.arend.ide.ardlpsi.ArdlFile
import com.jetbrains.arend.ide.lexer.ArdlLexerAdapter

class ArdlParserDefinition : ParserDefinition {
    override fun createParser(project: Project?): PsiParser = ArdlParser()

    override fun createFile(viewProvider: FileViewProvider): PsiFile = ArdlFile(viewProvider)

    override fun getStringLiteralElements(): TokenSet = TokenSet.EMPTY

    override fun getFileNodeType(): IFileElementType = ArdlFileStub.Type

    override fun createLexer(project: Project?): Lexer = ArdlLexerAdapter()

    override fun createElement(node: ASTNode?): PsiElement = ArdlElementTypes.Factory.createElement(node)

    override fun getCommentTokens() = TokenSet.create(LINE_COMMENT, BLOCK_COMMENT)

    class ArdlFileStub(file: ArdlFile?) : PsiFileStubImpl<ArdlFile>(file) {

        override fun getType(): Type = Type

        object Type : IStubFileElementType<ArdlFileStub>(com.jetbrains.arend.ide.ArdlLanguage.INSTANCE) {

            override fun getStubVersion(): Int = 1

            override fun getBuilder(): StubBuilder = object : DefaultStubBuilder() {
                override fun createStubForFile(file: PsiFile): StubElement<*> =
                        ArdlFileStub(file as ArdlFile)
            }

            override fun serialize(stub: ArdlFileStub, dataStream: StubOutputStream) {
            }

            override fun deserialize(
                    dataStream: StubInputStream,
                    parentStub: StubElement<*>?
            ): ArdlFileStub = ArdlFileStub(null)

            override fun getExternalId(): String = "LibHeader.file"
        }
    }
}
