package org.arend.structure

import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.ide.structureView.StructureViewBuilderProvider
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import org.arend.psi.ArendFile

class ArendStructureViewBuilderProvider : StructureViewBuilderProvider {
    override fun getStructureViewBuilder(fileType: FileType, file: VirtualFile, project: Project): StructureViewBuilder? {
        val arendFile = PsiManager.getInstance(project).findFile(file) as? ArendFile ?: return null
        return ArendPsiStructureViewFactory().getStructureViewBuilder(arendFile)
    }
}
