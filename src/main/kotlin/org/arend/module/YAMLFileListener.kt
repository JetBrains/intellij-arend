package org.arend.module

import com.intellij.AppTopics
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.*
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.*
import org.arend.module.config.ArendModuleConfigService
import org.arend.util.FileUtils

class YAMLFileListener(private val project: Project) : BulkFileListener, FileDocumentManagerListener {
    fun register() {
        val connection = project.messageBus.connect()
        connection.subscribe(VirtualFileManager.VFS_CHANGES, this)
        connection.subscribe(AppTopics.FILE_DOCUMENT_SYNC, this)
    }

    override fun beforeDocumentSaving(document: Document) {
        val file = FileDocumentManager.getInstance().getFile(document) ?: return
        if (file.name == FileUtils.LIBRARY_CONFIG_FILE) {
            ArendModuleConfigService.getInstance(ModuleUtilCore.findModuleForFile(file, project))?.copyFromYAML()
        }
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
            val service = ArendModuleConfigService.getInstance(ModuleUtil.findModuleForFile(file, project)) ?: return
            val root = service.root ?: return
            if (root == oldParent || root == newParent) {
                service.copyFromYAML()
            }
        }
    }
}