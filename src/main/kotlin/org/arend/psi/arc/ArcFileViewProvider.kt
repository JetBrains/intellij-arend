package org.arend.psi.arc

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.SingleRootFileViewProvider
import org.arend.ArendLanguage
import org.arend.psi.ArendFile

class ArcFileViewProvider(manager: PsiManager, virtualFile: VirtualFile, eventSystemEnabled: Boolean = true) :
    SingleRootFileViewProvider(manager, virtualFile, eventSystemEnabled, ArendLanguage.INSTANCE) {

    override fun createFile(project: Project, file: VirtualFile, fileType: FileType): PsiFile? {
        if (fileType is ArcFileType) {
            return ArendFile(this)
        }
        return super.createFile(project, file, fileType)
    }
}
