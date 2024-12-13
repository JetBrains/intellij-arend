package org.arend.psi.arc

import com.intellij.openapi.vfs.findDocument
import com.intellij.psi.FileViewProvider
import org.arend.psi.ArendFile

class ArcFile(viewProvider: FileViewProvider, val arcTimestamp: Long) : ArendFile(viewProvider) {
    override fun getText(): String {
        return virtualFile.findDocument()?.text ?: ""
    }
    override fun getTextLength(): Int {
        return virtualFile.findDocument()?.text?.length ?: 0
    }
}