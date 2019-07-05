package org.arend

import com.intellij.AppTopics
import com.intellij.ProjectTopics
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkModel
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.vfs.*
import org.arend.module.ArendModuleType
import org.arend.module.ArendRawLibrary
import org.arend.module.config.ArendModuleConfigService
import org.arend.resolving.ArendResolveCache
import org.arend.typechecking.TypeCheckingService
import org.arend.util.FileUtils


class ArendStartupActivity : StartupActivity {

    override fun runActivity(project: Project) {
        project.messageBus.connect(project).subscribe(ProjectTopics.MODULES, object : ModuleListener {
            override fun moduleAdded(project: Project, module: Module) {
                if (ArendModuleType.has(module)) {
                    addModule(module, false)
                }
            }

            override fun beforeModuleRemoved(project: Project, module: Module) {
                val libraryManager = TypeCheckingService.getInstance(project).libraryManager
                libraryManager.getRegisteredLibrary(module.name)?.let { libraryManager.unloadLibrary(it) }
            }
        })

        ProjectManager.getInstance().addProjectManagerListener(project, object : ProjectManagerListener {
            override fun projectClosed(project: Project) {
                TypeCheckingService.getInstance(project).libraryManager.unload()
            }
        })

        for (module in project.arendModules) {
            addModule(module, false)
        }

        var sdkHome: String? = ProjectRootManager.getInstance(project).projectSdk?.homePath
        project.messageBus.connect().subscribe(ProjectTopics.PROJECT_ROOTS, object : ModuleRootListener {
            override fun rootsChanged(event: ModuleRootEvent) {
                val newHome = ProjectRootManager.getInstance(project).projectSdk?.homePath
                if (sdkHome != newHome) {
                    ServiceManager.getService(project, ArendResolveCache::class.java)?.clear()
                    val service = TypeCheckingService.getInstance(project)
                    service.libraryManager.unloadExceptPrelude()
                    for (module in project.arendModules) {
                        addModule(module, true)
                    }
                    sdkHome = newHome
                } else {
                    for (module in project.arendModules) {
                        ArendModuleConfigService.getInstance(module)?.updateFromIdea()
                    }
                }
            }
        })

        VirtualFileManager.getInstance().addVirtualFileListener(MyVirtualFileListener(project), project)
    }

    companion object {
        private fun addModule(module: Module, fromRootsChanged: Boolean) {
            val project = module.project
            val service = TypeCheckingService.getInstance(project)

            if (service.initialize()) {
                project.messageBus.connect().subscribe(AppTopics.FILE_DOCUMENT_SYNC, object : FileDocumentManagerListener {
                    override fun beforeDocumentSaving(document: Document) {
                        val file = FileDocumentManager.getInstance().getFile(document) ?: return
                        if (file.name != FileUtils.LIBRARY_CONFIG_FILE) {
                            return
                        }

                        val fileModule = ModuleUtilCore.findModuleForFile(file, project) ?: return
                        ArendModuleConfigService.getInstance(fileModule)?.updateFromYAML()
                    }
                })
            }

            if (fromRootsChanged) {
                ApplicationManager.getApplication().invokeLater { ArendModuleConfigService.getInstance(module)?.updateFromYAML() }
            } else {
                ArendModuleConfigService.getInstance(module)?.updateFromYAML()
            }

            service.libraryManager.loadLibrary(ArendRawLibrary(module, service.typecheckerState))
        }

        private class MyVirtualFileListener(private val project: Project) : VirtualFileListener {
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
                    val module = ModuleUtil.findModuleForFile(event.file, project) ?: return
                    val service = ArendModuleConfigService.getInstance(module) ?: return
                    val root = service.root ?: return
                    if (root == oldParent || root == newParent) {
                        service.updateFromYAML()
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
    }
}
