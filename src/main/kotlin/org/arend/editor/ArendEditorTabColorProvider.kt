package org.arend.editor

import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.impl.EditorTabColorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.scope.NonProjectFilesScope
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.FileColorManager
import org.arend.server.ArendServerService
import org.jetbrains.annotations.Nullable
import java.awt.Color

class ArendEditorTabColorProvider : EditorTabColorProvider {
    override fun getEditorTabColor(project: Project, file: VirtualFile): Color? {
        val colorManager = FileColorManager.getInstance(project)
        return if (colorManager.isEnabledForTabs) getColor(project, file, colorManager) else null
    }

    override fun getProjectViewColor(project: Project, file: VirtualFile): Color? {
        val colorManager = FileColorManager.getInstance(project)
        return if (colorManager.isEnabledForProjectView) getColor(project, file, colorManager) else null
    }

    private fun getColor(project: Project, file: VirtualFile, colorManager: FileColorManager): @Nullable Color? {
        if (file !is LightVirtualFile) {
            return null
        }
        val preludeFile = project.service<ArendServerService>().prelude?.virtualFile
        return if (preludeFile == file) getNonProjectFileColor(colorManager) else null
    }

    private fun getNonProjectFileColor(colorManager: FileColorManager) =
            colorManager.getScopeColor(NonProjectFilesScope.NAME)
}