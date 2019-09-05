package org.arend.module

import com.intellij.AppTopics
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.*
import org.arend.module.config.ArendModuleConfigService
import org.arend.util.FileUtils

class YAMLFileListener(private val project: Project) : VirtualFileListener {
    fun register() {
        VirtualFileManager.getInstance().addVirtualFileListener(this, project)

        project.messageBus.connect().subscribe(AppTopics.FILE_DOCUMENT_SYNC, object : FileDocumentManagerListener {
            override fun beforeDocumentSaving(document: Document) {
                val file = FileDocumentManager.getInstance().getFile(document) ?: return
                if (file.name == FileUtils.LIBRARY_CONFIG_FILE) {
                    ArendModuleConfigService.getInstance(ModuleUtilCore.findModuleForFile(file, project))?.copyFromYAML()
                }
            }
        })
    }

    override fun beforeFileDeletion(event: VirtualFileEvent) {
        process(event, event.fileName, event.parent, null)
    }

    override fun fileCreated(event: VirtualFileEvent) {
        process(event, event.fileName, null, event.parent)
    }

    private fun process(event: VirtualFileEvent, fileName: String, oldParent: VirtualFile?, newParent: VirtualFile?) {
        if (oldParent == null && newParent == null) {
            return
        }
        if (fileName == FileUtils.LIBRARY_CONFIG_FILE) {
            val service = ArendModuleConfigService.getInstance(ModuleUtil.findModuleForFile(event.file, project)) ?: return
            val root = service.root ?: return
            if (root == oldParent || root == newParent) {
                service.copyFromYAML()
            }
        }
    }

    override fun fileMoved(event: VirtualFileMoveEvent) {
        process(event, event.fileName, event.oldParent, event.newParent)
    }

    override fun fileCopied(event: VirtualFileCopyEvent) {
        process(event, event.fileName, null, event.parent)
    }

    override fun propertyChanged(event: VirtualFilePropertyEvent) {
        if (event.propertyName == VirtualFile.PROP_NAME) {
            process(event, event.oldValue as String, event.parent, null)
            process(event, event.newValue as String, null, event.parent)
        }
    }
}