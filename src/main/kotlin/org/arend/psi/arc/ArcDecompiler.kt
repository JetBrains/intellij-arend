package org.arend.psi.arc

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findDocument
import com.intellij.psi.compiled.ClassFileDecompilers

class ArcDecompiler : ClassFileDecompilers.Light() {
    override fun accepts(file: VirtualFile): Boolean {
        return file.fileType is ArcFileType
    }

    override fun getText(p0: VirtualFile): CharSequence {
        return p0.findDocument()?.text ?: ""
    }
}
