package org.arend.yaml

import com.intellij.openapi.components.service
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.*
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.EditorNotifications
import org.arend.module.config.ArendModuleConfigService
import org.arend.util.FileUtils

class YAMLFileListener(private val project: Project) : BulkFileListener, DocumentListener {
    private val yamlFileService = project.service<YamlFileService>()

    fun register() {
        val connection = project.messageBus.connect()
        connection.subscribe(VirtualFileManager.VFS_CHANGES, this)
    }

    override fun documentChanged(event: DocumentEvent) {
        val document = event.document
        val file = FileDocumentManager.getInstance().getFile(document) ?: return
        if (file.name == FileUtils.LIBRARY_CONFIG_FILE && file !is LightVirtualFile) {
            val text = document.text
            val newYamlFile = createFromText(text, project)

            val arendModuleConfigService = ArendModuleConfigService.getInstance(ModuleUtil.findModuleForFile(file, project))
            val updateFlag = arendModuleConfigService?.sourcesDir != newYamlFile?.sourcesDir.orEmpty() ||
                    arendModuleConfigService.binariesDirectory != newYamlFile?.binariesDir.orEmpty() ||
                    arendModuleConfigService.testsDir != newYamlFile?.testsDir.orEmpty() ||
                    arendModuleConfigService.extensionsDirectory != newYamlFile?.extensionsDir.orEmpty() ||
                    arendModuleConfigService.extensionMainClassData != newYamlFile?.extensionMainClass.orEmpty() ||
                    arendModuleConfigService.modules.orEmpty() != newYamlFile?.modules.orEmpty() ||
                    arendModuleConfigService.dependencies != newYamlFile?.dependencies.orEmpty() ||
                    arendModuleConfigService.versionString != newYamlFile?.version.orEmpty() ||
                    arendModuleConfigService.langVersionString != newYamlFile?.langVersion.orEmpty()

            if (updateFlag) {
                yamlFileService.addChangedFile(file)
            } else {
                yamlFileService.removeChangedFile(file)
            }
            EditorNotifications.getInstance(project).updateNotifications(file)
        }
        super.documentChanged(event)
    }

    override fun before(events: List<VFileEvent>) {
        for (event in events) {
            if (event is VFileDeleteEvent) {
                process(event, event.file.name, event.file.parent, null)
            }
        }
    }

    override fun after(events: List<VFileEvent>) {
        for (event in events) {
            when (event) {
                is VFileCreateEvent -> process(event, event.childName, null, event.parent)
                is VFileMoveEvent -> process(event, event.file.name, event.oldParent, event.newParent)
                is VFileCopyEvent -> process(event, event.file.name, null, event.newParent)
                is VFilePropertyChangeEvent -> if (event.propertyName == VirtualFile.PROP_NAME) {
                    process(event, event.oldValue as String, event.file.parent, null)
                    process(event, event.newValue as String, null, event.file.parent)
                }
            }
        }
    }

    private fun process(event: VFileEvent, fileName: String, oldParent: VirtualFile?, newParent: VirtualFile?) {
        if (oldParent == null && newParent == null) {
            return
        }
        if (fileName == FileUtils.LIBRARY_CONFIG_FILE) {
            val file = event.file ?: return
            val module = ModuleUtil.findModuleForFile(file, project)
            val service = ArendModuleConfigService.getInstance(module) ?: return
            val root = service.root ?: return
            if (root == oldParent || root == newParent) {
                yamlFileService.updateIdea(file)
            }
        }
    }
}
