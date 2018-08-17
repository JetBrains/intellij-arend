package org.vclang.parser

import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.FlexAdapter
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
import org.vclang.VclLanguage
import org.vclang.vclpsi.VclElementTypes
import org.vclang.vclpsi.VclFile
import org.vclang.lang.lexer.VclLexer
import org.vclang.lang.parser.VclParser
import java.io.Reader

class VclParserDefinition : ParserDefinition {
    override fun createParser(project: Project?): PsiParser = VclParser()

    override fun createFile(viewProvider: FileViewProvider): PsiFile = VclFile(viewProvider)

    override fun getStringLiteralElements(): TokenSet = TokenSet.EMPTY

    override fun getFileNodeType(): IFileElementType = VclFileStub.Type

    override fun createLexer(project: Project?): Lexer = FlexAdapter(VclLexer(null as Reader?))

    override fun createElement(node: ASTNode?): PsiElement = VclElementTypes.Factory.createElement(node)

    override fun getCommentTokens(): TokenSet = TokenSet.EMPTY

    class VclFileStub(file: VclFile?) : PsiFileStubImpl<VclFile>(file) {

        override fun getType(): Type = Type

        object Type : IStubFileElementType<VclFileStub>(VclLanguage.INSTANCE) {

            override fun getStubVersion(): Int = 1

            override fun getBuilder(): StubBuilder = object : DefaultStubBuilder() {
                override fun createStubForFile(file: PsiFile): StubElement<*> =
                        VclFileStub(file as VclFile)
            }

            override fun serialize(stub: VclFileStub, dataStream: StubOutputStream) {
            }

            override fun deserialize(
                    dataStream: StubInputStream,
                    parentStub: StubElement<*>?
            ): VclFileStub = VclFileStub(null)

            override fun getExternalId(): String = "LibHeader.file"
        }
    }
}