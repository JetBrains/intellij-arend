package org.arend.typechecking

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.psi.PsiManager
import org.arend.ext.error.ErrorReporter
import org.arend.ext.error.ListErrorReporter
import org.arend.library.SourceLibrary
import org.arend.module.ModuleLocation
import org.arend.naming.reference.converter.ReferableConverter
import org.arend.psi.ArendFile


class BinaryFileSaver(private val project: Project) {
    private val typeCheckingService = project.service<TypeCheckingService>()
    private val typecheckedModules = LinkedHashMap<ArendFile, ReferableConverter>()

    init {
        // TODO: Replace with AsyncFileListener?
        project.messageBus.connect().subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                if (typecheckedModules.isEmpty()) {
                    return
                }

                for (event in events) {
                    val file = (if (event is VFileContentChangeEvent && event.isFromSave) PsiManager.getInstance(project).findFile(event.file) as? ArendFile else null) ?: continue
                    synchronized(project) {
                        saveFile(file, typecheckedModules.remove(file) ?: return@synchronized, typeCheckingService.libraryManager.libraryErrorReporter)
                    }
                }
            }
        })

        ProjectManager.getInstance().addProjectManagerListener(project, object : ProjectManagerListener {
            override fun projectClosing(closedProject: Project) {
                if (closedProject == project) {
                    saveAll()
                }
            }
        })
    }

    fun saveFile(file: ArendFile, referableConverter: ReferableConverter, errorReporter: ErrorReporter) {
        val moduleLocation = file.moduleLocation ?: return
        if (moduleLocation.locationKind != ModuleLocation.LocationKind.SOURCE) {
            return
        }
        val library = typeCheckingService.libraryManager.getRegisteredLibrary(moduleLocation.libraryName) as? SourceLibrary ?: return
        if (library.supportsPersisting()) {
            runReadAction { library.persistModule(moduleLocation.modulePath, referableConverter, errorReporter) }
        }
    }

    fun addToQueue(file: ArendFile, referableConverter: ReferableConverter) {
        synchronized(project) {
            typecheckedModules[file] = referableConverter
        }
    }

    fun saveAll() {
        val errorReporter = ListErrorReporter()
        runInEdt { runWriteAction {
            synchronized(project) {
                for (entry in typecheckedModules) {
                    saveFile(entry.key, entry.value, errorReporter)
                }
                typecheckedModules.clear()
            }
        } }
        errorReporter.reportTo(typeCheckingService.libraryManager.libraryErrorReporter)
    }
}