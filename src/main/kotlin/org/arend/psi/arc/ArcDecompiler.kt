package org.arend.psi.arc

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiManager
import com.intellij.psi.compiled.ClassFileDecompilers
import com.intellij.psi.compiled.ClsStubBuilder
import com.intellij.psi.stubs.PsiFileStub
import com.intellij.util.indexing.FileContent

class ArcDecompiler : ClassFileDecompilers.Full() {

    private val myStubBuilder = ArcClsStubBuilder()

    override fun accepts(file: VirtualFile): Boolean {
        return file.fileType is ArcFileType
    }

    override fun getStubBuilder(): ClsStubBuilder {
        return myStubBuilder
    }

    override fun createFileViewProvider(file: VirtualFile, manager: PsiManager, physical: Boolean): FileViewProvider {
        return ArcFileViewProvider(manager, file, physical)
    }

    companion object {
        const val STUB_VERSION: Int = 1

        class ArcClsStubBuilder : ClsStubBuilder() {
            override fun getStubVersion(): Int {
                return STUB_VERSION
            }

            override fun buildFileStub(fileContent: FileContent): PsiFileStub<*>? {
                return ArcFile.buildFileStub(fileContent.file, fileContent.content)
            }
        }
    }
}
