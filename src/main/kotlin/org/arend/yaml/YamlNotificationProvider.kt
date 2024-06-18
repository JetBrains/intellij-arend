package org.arend.yaml

import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import org.arend.util.ArendBundle
import java.util.function.Function
import javax.swing.JComponent

class YamlNotificationProvider : EditorNotificationProvider {

    override fun collectNotificationData(project: Project, virtualFile: VirtualFile): Function<in FileEditor, out JComponent?>? {
        val yamlFileService = project.service<YamlFileService>()
        if (!yamlFileService.containsChangedFile(virtualFile)) {
            return null
        }
        return Function<FileEditor, EditorNotificationPanel?> { fileEditor: FileEditor? ->
            fileEditor?.let { createPanel(yamlFileService, virtualFile, it) }
        }
    }

    private fun createPanel(yamlFileService: YamlFileService, file: VirtualFile, editor: FileEditor): EditorNotificationPanel {
        val panel = EditorNotificationPanel(editor, EditorNotificationPanel.Status.Info)
        panel.text = ArendBundle.message("arend.updateYamlConfigurationQuestion")
        panel.createActionLabel(ArendBundle.message("arend.updateYamlConfiguration")) {
            panel.isVisible = false
            yamlFileService.removeChangedFile(file)
            yamlFileService.updateIdea(file)
        }
        return panel
    }
}
