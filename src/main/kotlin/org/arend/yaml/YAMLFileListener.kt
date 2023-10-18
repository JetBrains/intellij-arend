package org.arend.yaml

import com.intellij.AppTopics
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.*
import com.intellij.psi.PsiManager
import org.arend.actions.addNewDirectory
import org.arend.actions.removeOldDirectory
import org.arend.module.config.ArendModuleConfigService
import org.arend.util.FileUtils
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.yaml.psi.YAMLFile
import java.io.File

class YAMLFileListener(private val project: Project) : BulkFileListener, FileDocumentManagerListener {
    fun register() {
        val connection = project.messageBus.connect()
        connection.subscribe(VirtualFileManager.VFS_CHANGES, this)
        connection.subscribe(AppTopics.FILE_DOCUMENT_SYNC, this)
    }
    override fun unsavedDocumentDropped(document: Document) {
        val file = FileDocumentManager.getInstance().getFile(document) ?: return
        if (file.name == FileUtils.LIBRARY_CONFIG_FILE) {
            updateIdea(file)
        }
    }

    override fun beforeDocumentSaving(document: Document) {
        val file = FileDocumentManager.getInstance().getFile(document) ?: return
        if (file.name == FileUtils.LIBRARY_CONFIG_FILE) {
            updateIdea(file)
        }
    }

    private fun updateIdea(file: VirtualFile) {
        val yaml = PsiManager.getInstance(project).findFile(file) as? YAMLFile
        val module = ModuleUtilCore.findModuleForFile(file, project)
        val arendModuleConfigService = ArendModuleConfigService.getInstance(module)

        updateSourceAndTestDirectories(module, yaml, file, arendModuleConfigService)

        arendModuleConfigService?.copyFromYAML(true)
    }

    private fun updateSourceAndTestDirectories(module: Module?, yaml: YAMLFile?, file: VirtualFile, arendModuleConfigService: ArendModuleConfigService?) {
        if (yaml?.sourcesDir != arendModuleConfigService?.sourcesDir) {
            removeOldDirectory(module, file, arendModuleConfigService, JavaSourceRootType.SOURCE)
        }
        if (yaml?.testsDir != arendModuleConfigService?.testsDir) {
            removeOldDirectory(module, file, arendModuleConfigService, JavaSourceRootType.TEST_SOURCE)
        }

        if (yaml?.sourcesDir != arendModuleConfigService?.sourcesDir) {
            val dirFile = File(arendModuleConfigService?.root?.path + File.separator + yaml?.sourcesDir)
            if (dirFile.exists()) {
                addNewDirectory(yaml?.sourcesDir, arendModuleConfigService, JavaSourceRootType.SOURCE)
            }
        }
        if (yaml?.testsDir != arendModuleConfigService?.testsDir) {
            val dirFile = File(arendModuleConfigService?.root?.path + File.separator + yaml?.testsDir)
            if (dirFile.exists()) {
                addNewDirectory(yaml?.testsDir, arendModuleConfigService, JavaSourceRootType.TEST_SOURCE)
            }
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
            val module = ModuleUtil.findModuleForFile(file, project)
            val service = ArendModuleConfigService.getInstance(module) ?: return
            val root = service.root ?: return
            if (root == oldParent || root == newParent) {
                updateIdea(file)
            }
        }
    }
}
