package org.arend.psi

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.ProjectAndLibrariesScope

class ArendFileScope(project: Project): ProjectAndLibrariesScope(project) {
    override fun contains(file: VirtualFile): Boolean {
        if (!super.contains(file)) return false
        val project = project ?: return false
        val psiFile = PsiManager.getInstance(project).findFile(file)
        return psiFile is ArendFile && psiFile.moduleLocation != null
    }
}