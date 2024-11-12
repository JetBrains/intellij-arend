package org.arend.psi.arc

import com.intellij.lang.Language
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.FileViewProvider
import com.intellij.psi.FileViewProviderFactory
import com.intellij.psi.PsiManager
import com.intellij.psi.compiled.ClassFileDecompilers

class ArcFileViewProviderFactory : FileViewProviderFactory {
    override fun createFileViewProvider(file: VirtualFile, language: Language?, manager: PsiManager, eventSystemEnabled: Boolean): FileViewProvider {
        val decompiler = ClassFileDecompilers.getInstance().find(file, ClassFileDecompilers.Full::class.java)
        return decompiler.createFileViewProvider(file, manager, eventSystemEnabled)
    }
}
