package org.arend

import com.intellij.AppTopics
import com.intellij.ProjectTopics
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.vfs.*
import org.arend.module.ArendModuleType
import org.arend.module.ArendRawLibrary
import org.arend.module.config.ArendModuleConfigService
import org.arend.resolving.ArendResolveCache
import org.arend.typechecking.TypeCheckingService
import org.arend.util.FileUtils
import org.arend.util.arendModules
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.KeyStroke


class ArendStartupActivity : StartupActivity {

    override fun runActivity(project: Project) {
        project.messageBus.connect(project).subscribe(ProjectTopics.MODULES, object : ModuleListener {
            override fun moduleAdded(project: Project, module: Module) {
                if (ArendModuleType.has(module)) {
                    addModule(module)
                }
            }

            override fun beforeModuleRemoved(project: Project, module: Module) {
                val libraryManager = project.service<TypeCheckingService>().libraryManager
                ArendRawLibrary.getLibraryFor(libraryManager, module)?.let {
                    libraryManager.unloadLibrary(it)
                }
            }
        })

        ProjectManager.getInstance().addProjectManagerListener(project, object : ProjectManagerListener {
            override fun projectClosed(project: Project) {
                project.service<TypeCheckingService>().libraryManager.unload()
            }
        })

        val service = project.service<TypeCheckingService>()
        val libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project)
        libraryTable.addListener(object : LibraryTable.Listener {
            override fun afterLibraryAdded(newLibrary: Library) {
                service.addLibrary(newLibrary)
            }

            override fun afterLibraryRemoved(library: Library) {
                val name = service.removeLibrary(library) ?: return
                ArendRawLibrary.getExternalLibrary(service.libraryManager, name)?.let {
                    service.libraryManager.unloadLibrary(it)
                }
            }
        })
        for (library in libraryTable.libraries) {
            service.addLibrary(library)
        }

        for (module in project.arendModules) {
            addModule(module)
        }

        var sdkHome: String? = ProjectRootManager.getInstance(project).projectSdk?.homePath
        project.messageBus.connect().subscribe(ProjectTopics.PROJECT_ROOTS, object : ModuleRootListener {
            override fun rootsChanged(event: ModuleRootEvent) {
                val newHome = ProjectRootManager.getInstance(project).projectSdk?.homePath
                if (sdkHome != newHome) {
                    project.service<ArendResolveCache>().clear()
                    project.service<TypeCheckingService>().libraryManager.unload()
                    for (module in project.arendModules) {
                        addModule(module)
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
        KeymapManager.getInstance().activeKeymap.addShortcut("Arend.ImplementedFields", KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_H,
                InputEvent.ALT_MASK or InputEvent.SHIFT_MASK), null))
    }

    companion object {
        private fun addModule(module: Module) {
            val project = module.project
            val service = project.service<TypeCheckingService>()

            if (service.initialize()) {
                project.messageBus.connect().subscribe(AppTopics.FILE_DOCUMENT_SYNC, object : FileDocumentManagerListener {
                    override fun beforeDocumentSaving(document: Document) {
                        val file = FileDocumentManager.getInstance().getFile(document) ?: return
                        if (file.name != FileUtils.LIBRARY_CONFIG_FILE) {
                            return
                        }

                        ArendModuleConfigService.getInstance(ModuleUtilCore.findModuleForFile(file, project))?.updateFromYAML()
                    }
                })
                ModuleRootManager.getInstance(module)
            }

            ArendModuleConfigService.getInstance(module)?.updateFromYAML()
            service.libraryManager.loadLibrary(ArendRawLibrary(module))
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
                    val service = ArendModuleConfigService.getInstance(ModuleUtil.findModuleForFile(event.file, project)) ?: return
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
