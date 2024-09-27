package org.arend.psi.arc

import com.intellij.openapi.fileTypes.BinaryFileDecompiler
import com.intellij.openapi.project.DefaultProjectFactory
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.compiled.ClassFileDecompilers
import com.intellij.psi.impl.compiled.ClsFileImpl

class ArcFileDecompiler : BinaryFileDecompiler {
    override fun decompile(file: VirtualFile): CharSequence {
        val decompiler = ClassFileDecompilers.getInstance().find(file, ClassFileDecompilers.Decompiler::class.java)
        if (decompiler is ArcDecompiler) {
            return ArcFile.decompile(file)
        }

        if (decompiler is ClassFileDecompilers.Full) {
            val manager = PsiManager.getInstance(DefaultProjectFactory.getInstance().defaultProject)
            return decompiler.createFileViewProvider(file, manager, true).contents
        }

        if (decompiler is ClassFileDecompilers.Light) {
            return try {
                decompiler.getText(file)
            } catch (e: ClassFileDecompilers.Light.CannotDecompileException) {
                ClsFileImpl.decompile(file)
            }
        }

        throw IllegalStateException(decompiler.javaClass.name +
                    " should be on of " +
                    ClassFileDecompilers.Full::class.java.name +
                    " or " +
                    ClassFileDecompilers.Light::class.java.name
        )
    }
}
