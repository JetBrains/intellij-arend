package org.arend.psi.arc

import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import org.arend.module.config.ArendModuleConfigService
import org.arend.psi.ArendFile
import org.arend.psi.arc.ArcErrorService.Companion.DEFINITION_IS_NOT_LOADED
import org.arend.util.ArendBundle
import org.arend.util.FileUtils.EXTENSION
import org.arend.util.arendModules
import org.arend.util.getRelativeFile
import java.util.function.Function
import javax.swing.JComponent

class ArcFileNotificationProvider : EditorNotificationProvider {

    override fun collectNotificationData(project: Project, virtualFile: VirtualFile): Function<in FileEditor, out JComponent?>? {
        if (!project.service<ArcErrorService>().errors.containsKey(virtualFile)) {
            return null
        }
        return Function { createPanel(project, virtualFile, it) }
    }

    private fun createPanel(project: Project, virtualFile: VirtualFile, editor: FileEditor): EditorNotificationPanel {
        val panel = EditorNotificationPanel(editor, EditorNotificationPanel.Status.Info)
        val service = project.service<ArcErrorService>()
        val (modulePath, _) = DEFINITION_IS_NOT_LOADED.find(service.errors[virtualFile]!!)!!.destructured
        panel.text = ArendBundle.message("arend.arc.retypecheck", modulePath)
        panel.createActionLabel(ArendBundle.message("arend.arc.open")) {
            service.errors.remove(virtualFile)
            val config = project.arendModules.map { ArendModuleConfigService.getInstance(it) }.find {
                it?.root?.let { root -> VfsUtilCore.isAncestor(root, virtualFile, true) } ?: false
            } ?: ArendModuleConfigService.getInstance(project.arendModules.getOrNull(0))
            val arendFile = config?.sourcesDirFile?.getRelativeFile(modulePath.split("."), EXTENSION)
                ?.let { PsiManager.getInstance(project).findFile(it) } as? ArendFile?

            val arendDescriptor = arendFile?.let { OpenFileDescriptor(project, it.virtualFile) }
            val fileEditorManager = FileEditorManager.getInstance(project)
            arendDescriptor?.let { fileEditorManager.openTextEditor(it, false) }
            EditorNotifications.getInstance(project).updateNotifications(virtualFile)
        }
        return panel
    }
}
