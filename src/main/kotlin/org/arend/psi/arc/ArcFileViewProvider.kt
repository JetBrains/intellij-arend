package org.arend.psi.arc

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findDocument
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.SingleRootFileViewProvider
import org.arend.ArendLanguage
import java.util.concurrent.atomic.AtomicLong

class ArcFileViewProvider(manager: PsiManager, virtualFile: VirtualFile, eventSystemEnabled: Boolean = true) :
    SingleRootFileViewProvider(manager, virtualFile, eventSystemEnabled, ArendLanguage.INSTANCE) {

    override fun createFile(project: Project, file: VirtualFile, fileType: FileType): PsiFile? {
        if (fileType is ArcFileType) {
            return ArcFile(this, ARC_COUNTER.getAndIncrement())
        }
        return super.createFile(project, file, fileType)
    }

    override fun getContents(): CharSequence {
        return virtualFile.findDocument()?.text ?: ""
    }

    companion object {
        private val ARC_COUNTER = AtomicLong(0)
    }
}
