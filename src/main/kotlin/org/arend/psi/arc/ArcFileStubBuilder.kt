package org.arend.psi.arc

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.compiled.ClassFileDecompilers
import com.intellij.psi.stubs.BinaryFileStubBuilder
import com.intellij.psi.stubs.Stub
import com.intellij.util.cls.ClsFormatException
import com.intellij.util.indexing.FileContent
import java.util.stream.Stream

class ArcFileStubBuilder : BinaryFileStubBuilder.CompositeBinaryFileStubBuilder<ClassFileDecompilers.Full> {
    override fun acceptsFile(file: VirtualFile): Boolean {
        return file.fileType is ArcFileType
    }

    override fun getStubVersion(): Int {
        return STUB_VERSION
    }

    override fun getAllSubBuilders(): Stream<ClassFileDecompilers.Full> {
        return ClassFileDecompilers.getInstance().EP_NAME.extensionList.stream()
            .filter { d: ClassFileDecompilers.Decompiler? -> d is ClassFileDecompilers.Full }
            .map { d: ClassFileDecompilers.Decompiler? -> d as ClassFileDecompilers.Full? }
    }

    override fun getSubBuilder(fileContent: FileContent): ClassFileDecompilers.Full? {
        return fileContent.file.computeWithPreloadedContentHint(fileContent.content) {
            ClassFileDecompilers.getInstance()
                .find(fileContent.file, ClassFileDecompilers.Full::class.java)
        }
    }

    override fun getSubBuilderVersion(decompiler: ClassFileDecompilers.Full?): String {
        if (decompiler == null) return "default"
        val version = decompiler.stubBuilder.stubVersion
        return decompiler.javaClass.name + ":" + version
    }

    override fun buildStubTree(fileContent: FileContent, decompiler: ClassFileDecompilers.Full?): Stub? {
        if (decompiler == null) return null
        return fileContent.file.computeWithPreloadedContentHint(fileContent.content) {
            try {
                return@computeWithPreloadedContentHint decompiler.stubBuilder.buildFileStub(fileContent)
            } catch (_: ClsFormatException) {
                // TODO()
            }
            null
        }
    }

    companion object {
        const val STUB_VERSION: Int = 1
    }
}
