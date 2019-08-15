package org.arend.typechecking

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.vfs.VirtualFileEvent
import com.intellij.openapi.vfs.VirtualFileListener
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiManager
import org.arend.library.SourceLibrary
import org.arend.naming.reference.converter.ReferableConverter
import org.arend.psi.ArendFile
import org.arend.typechecking.execution.FullModulePath


class BinaryFileSaver(private val project: Project) {
    private val typeCheckingService = TypeCheckingService.getInstance(project)
    private val typecheckedModules = LinkedHashMap<ArendFile, ReferableConverter>()

    init {
        VirtualFileManager.getInstance().addVirtualFileListener(object : VirtualFileListener {
            override fun contentsChanged(event: VirtualFileEvent) {
                if (typecheckedModules.isEmpty()) {
                    return
                }

                val file = PsiManager.getInstance(project).findFile(event.file) as? ArendFile ?: return
                synchronized(project) {
                    saveFile(file, typecheckedModules.remove(file) ?: return@synchronized)
                }
            }
        })

        ProjectManager.getInstance().addProjectManagerListener(project, object : ProjectManagerListener {
            override fun projectClosing(project: Project) {
                saveAll()
            }
        })
    }

    fun saveFile(file: ArendFile, referableConverter: ReferableConverter) {
        val fullName = FullModulePath(file.libraryName ?: return, file.modulePath ?: return)
        val library = typeCheckingService.libraryManager.getRegisteredLibrary(fullName.libraryName) as? SourceLibrary ?: return
        if (library.supportsPersisting()) {
            runReadAction { library.persistModule(fullName.modulePath, referableConverter, typeCheckingService.libraryManager.libraryErrorReporter) }
        }
    }

    fun addToQueue(file: ArendFile, referableConverter: ReferableConverter) {
        synchronized(project) {
            typecheckedModules[file] = referableConverter
        }
    }

    fun saveAll() {
        synchronized(project) {
            for (entry in typecheckedModules) {
                saveFile(entry.key, entry.value)
            }
            typecheckedModules.clear()
        }
    }
}