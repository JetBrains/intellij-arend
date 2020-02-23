package org.arend.serialized

import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiCompiledFile
import com.intellij.psi.impl.PsiManagerImpl
import com.intellij.psi.impl.file.PsiBinaryFileImpl
import org.arend.ArendLanguage
import org.arend.psi.ArendFile
import org.arend.psi.ArendPsiFactory

class ArendSerializedFile(
        manager: PsiManagerImpl,
        viewProvider: FileViewProvider
) : PsiBinaryFileImpl(manager, viewProvider), PsiCompiledFile {
    override fun getMirror() = decompiledPsiFile

    override fun getLanguage() = ArendLanguage.INSTANCE

    private fun decompile(bytes: ByteArray): String {
        TODO()
    }

    override fun getDecompiledPsiFile(): ArendFile? =
            ArendPsiFactory(project).createFromText(decompile(virtualFile.contentsToByteArray()))
}