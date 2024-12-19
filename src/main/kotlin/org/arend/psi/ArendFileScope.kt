package org.arend.psi

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.ProjectAndLibrariesScope
import org.arend.prelude.Prelude

class ArendFileScope(project: Project): ProjectAndLibrariesScope(project) {
    override fun contains(file: VirtualFile): Boolean {
        val project = project ?: return false
        val psiFile = PsiManager.getInstance(project).findFile(file) as? ArendFile ?: return false
        if (psiFile.libraryName == Prelude.LIBRARY_NAME) {
            return true
        }
        if (!super.contains(file)) return false
        return psiFile.moduleLocation != null
    }
}