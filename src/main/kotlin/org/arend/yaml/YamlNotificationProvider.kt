package org.arend.yaml

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import org.arend.module.config.ArendModuleConfigService
import org.arend.util.ArendBundle
import org.jetbrains.yaml.YAMLFileType
import java.util.function.Function
import javax.swing.JComponent

class YamlNotificationProvider : EditorNotificationProvider {

    override fun collectNotificationData(project: Project, virtualFile: VirtualFile): Function<in FileEditor, out JComponent?>? {
        val yamlFileService = project.service<YamlFileService>()
        return if (virtualFile.fileType !is YAMLFileType) {
            null
        } else if (yamlFileService.getSameFields().isNotEmpty() || yamlFileService.checkSameFields(virtualFile)) {
            Function<FileEditor, EditorNotificationPanel?> { fileEditor: FileEditor? ->
                fileEditor?.let { createWarningPanel(it, yamlFileService.getSameFields()) }
            }
        } else if (yamlFileService.containsChangedFile(virtualFile) || yamlFileService.compareSettings(virtualFile)) {
            Function<FileEditor, EditorNotificationPanel?> { fileEditor: FileEditor? ->
                fileEditor?.let { createUpdatePanel(project, yamlFileService, virtualFile, it) }
            }
        } else {
            null
        }
    }

    private fun createWarningPanel(editor: FileEditor, fields: Set<String>): EditorNotificationPanel {
        val panel = EditorNotificationPanel(editor, EditorNotificationPanel.Status.Info)
        panel.text = ArendBundle.message("arend.warningYamlConfiguration", fields)
        return panel
    }

    private fun createUpdatePanel(project: Project, yamlFileService: YamlFileService, file: VirtualFile, editor: FileEditor): EditorNotificationPanel {
        val panel = EditorNotificationPanel(editor, EditorNotificationPanel.Status.Info)
        panel.text = ArendBundle.message("arend.updateYamlConfigurationQuestion")
        panel.createActionLabel(ArendBundle.message("arend.updateYamlConfiguration")) {
            panel.isVisible = false
            yamlFileService.removeChangedFile(file)

            val applicationManager = ApplicationManager.getApplication()
            var module: Module? = null
            applicationManager.executeOnPooledThread {
                module = ModuleUtilCore.findModuleForFile(file, project)
            }.get()
            val arendModuleConfigService = ArendModuleConfigService.getInstance(module)

            invokeLater {
                runReadAction {
                    yamlFileService.updateIdea(file, arendModuleConfigService)
                    .run {
                        applicationManager.executeOnPooledThread {
                            runReadAction {
                                arendModuleConfigService?.copyFromYAML(true)
                            }
                        }
                    }
                }
            }
        }
        return panel
    }
}
