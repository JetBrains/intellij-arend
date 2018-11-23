package org.arend

import com.intellij.ProjectTopics
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.startup.StartupActivity
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiTreeChangeAdapter
import com.intellij.psi.PsiTreeChangeEvent
import org.arend.error.DummyErrorReporter
import org.arend.library.LibraryDependency
import org.arend.module.ArendPreludeLibrary
import org.arend.module.ArendRawLibrary
import org.arend.module.util.*
import org.arend.prelude.Prelude
import org.arend.resolving.PsiConcreteProvider
import org.arend.typechecking.PsiInstanceProviderSet
import org.arend.typechecking.TypeCheckingService
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue


class ArendStartupActivity : StartupActivity {

    override fun runActivity(project: Project) {
        val service = TypeCheckingService.getInstance(project)

        val preludeLibrary = ArendPreludeLibrary(project, service.typecheckerState)
        service.libraryManager.loadLibrary(preludeLibrary)
        val referableConverter = service.newReferableConverter(false)
        val concreteProvider = PsiConcreteProvider(project, referableConverter, DummyErrorReporter.INSTANCE, null)
        preludeLibrary.resolveNames(referableConverter, concreteProvider, service.libraryManager.libraryErrorReporter)
        Prelude.PreludeTypechecking(PsiInstanceProviderSet(concreteProvider, referableConverter), service.typecheckerState, concreteProvider).typecheckLibrary(preludeLibrary)

        val addedLibraries = mutableSetOf<String>()
        for (module in project.arendModules) {
            service.libraryManager.loadLibrary(ArendRawLibrary(module, service.typecheckerState))
            syncModuleDependencies(module, addedLibraries, true)

            module.messageBus.connect().subscribe(ProjectTopics.PROJECT_ROOTS, object : ModuleRootListener {
                override fun rootsChanged(event: ModuleRootEvent) {
                    // System.out.println("Roots changed. Source: ${event.source}")
                    val orderEntries = ModuleRootManager.getInstance(module).orderEntries
                    val libEntriesNames = orderEntries.filter { it is LibraryOrderEntry }.map { (it as LibraryOrderEntry).libraryName }.toSet()
                    if (!rootsChangedExternally) return
                    // if (module.libraryConfig?.dependencies?.map { it.name }?.toSet()?.equals(libEntriesNames) == true) return
                    ApplicationManager.getApplication().invokeLater {
                        module.libraryConfig?.dependencies = libEntriesNames.map { LibraryDependency(it) }.toList()
                    }
                    //}
                }
            })
        }

        project.messageBus.connect(project).subscribe(ProjectTopics.MODULES, object : ModuleListener {
            override fun moduleAdded(project: Project, module: Module) {
                if (module.isArendModule) {
                    service.libraryManager.loadLibrary(ArendRawLibrary(module, service.typecheckerState))
                    syncModuleDependencies(module, addedLibraries, true)
                }
            }

            override fun beforeModuleRemoved(project: Project, module: Module) {
                service.libraryManager.getRegisteredLibrary(module.name)?.let { service.libraryManager.unloadLibrary(it) }
            }

            override fun moduleRemoved(project: Project, module: Module) {
                syncModuleDependencies(module, mutableSetOf(), true)
                cleanObsoleteProjectLibraries(project)
            }
        })

        ProjectManager.getInstance().addProjectManagerListener(project, object : ProjectManagerListener {
            override fun projectClosed(project: Project) {
                service.libraryManager.unload()
            }
        })

        PsiManager.getInstance(project).addPsiTreeChangeListener(LibHeaderPsiTreeChangeListener)

        /*LibraryTablesRegistrar.getInstance().getLibraryTable(project).addListener(object: LibraryTable.Listener {
            override fun afterLibraryAdded(newLibrary: Library) {
                for (module in project.arendModules) {
                    for (orderEntry in ModuleRootManager.getInstance(module).orderEntries) {
                        if (orderEntry is LibraryOrderEntry) {
                            if (orderEntry.library?.name == newLibrary.name) {
                                newLibrary.name?.let { module.libraryConfig?.addDependency(it) }
                            }
                        }
                    }
                }
                // val pathUrl = VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, Paths.get("").toString())
                //val file = VirtualFileManager.getInstance().findFileByUrl(pathUrl)
                //newLibrary.getFiles()
                //if (file != null) {
                 //   val libraryModel = newLibrary.modifiableModel
                  //  libraryModel.addRoot(file, OrderRootType.CLASSES)
                  //  libraryModel.commit()
                  //  ModuleRootModificationUtil.addDependency(project.arendModules.elementAt(0), newLibrary)
                //}

                //ModuleRootModificationUtil.addDependency(project.arendModules.elementAt(0), newLibrary)
                /*
                WriteAction.run<Exception> {
                    val table = LibraryTablesRegistrar.getInstance().getLibraryTable(project)
                    val tableModel = table.modifiableModel
                    val library = tableModel.createLibrary(dep.name)

                    val pathUrl = VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, Paths.get("").toString())
                    val file = VirtualFileManager.getInstance().findFileByUrl(pathUrl)
                    if (file != null) {
                        val libraryModel = library.modifiableModel
                        libraryModel.addRoot(file, OrderRootType.CLASSES)
                        libraryModel.commit()
                        tableModel.commit()
                        ModuleRootModificationUtil.addDependency(module.project.arendModules.elementAt(0), library)
                    }
                }*/
            }
        })*/
    }

    companion object {
        var rootsChangedExternally = true

        private fun cleanObsoleteProjectLibraries(project: Project) {
            val depNames = mutableSetOf<String>()
            project.arendModules.forEach { mod -> mod.libraryConfig?.dependencies?.let { depNames.addAll(it.map { it.name })}}

            val table = LibraryTablesRegistrar.getInstance().getLibraryTable(project)
            for (library in table.libraries) {
                if (!depNames.contains(library.name)) {
                    ApplicationManager.getApplication().invokeLater {
                        WriteAction.run<Exception> {
                            table.removeLibrary(library)
                        }
                    }
                }
            }
        }

        private fun syncModuleDependencies(module: Module, addedLibraries: MutableSet<String>, useInvokeLater: Boolean) {
            val deps = module.libraryConfig?.dependencies ?: return
            val depNames = deps.map { it.name }

            rootsChangedExternally = false
            for (dep in deps) {
                addDependency(module, dep.name, addedLibraries, useInvokeLater)
                addedLibraries.add(dep.name)
            }

            WriteAction.run<Exception> {
                val rootModel = ModuleRootManager.getInstance(module).modifiableModel
                for (entry in rootModel.orderEntries) {
                    if (entry is LibraryOrderEntry && !depNames.contains(entry.libraryName)) {
                        rootModel.removeOrderEntry(entry)
                        System.out.println("Removed ${entry.libraryName}")
                    }
                }
                rootModel.commit()
                rootsChangedExternally = true
            }
        }

        private fun addDependency(module: Module, libName: String, addedLibraries: MutableSet<String>, useInvokeLater: Boolean) {
            val table = LibraryTablesRegistrar.getInstance().getLibraryTable(module.project)
            val tableModel = table.modifiableModel
            var library: Library? = table.getLibraryByName(libName)

            if (library == null) {
                library = tableModel.createLibrary(libName)
                val addLibrary = { WriteAction.run<Exception> {
                    val libModel = library.modifiableModel
                    val libHeader = findLibHeader(module.project, libName)?.let { libHeaderByPath(it, module.project) }
                    val srcDir = libHeader?.sourcesDirPath
                    if (srcDir != null) libModel.addRoot(srcDir.toString(), OrderRootType.SOURCES)
                    val outDir = libHeader?.outputPath
                    if (outDir != null) libModel.addRoot(outDir.toString(), OrderRootType.CLASSES)
                    libModel.commit()
                    tableModel.commit()
                } }

                if (!addedLibraries.contains(libName) && findLibHeader(module.project, libName) != null) {
                    if (useInvokeLater) {
                        ApplicationManager.getApplication().invokeLater { addLibrary() }
                    } else {
                        addLibrary()
                    }
                }
            }

            WriteAction.run<Exception> {
                val orderEntries = ModuleRootManager.getInstance(module).orderEntries
                val libEntriesNames = orderEntries.filter { it is LibraryOrderEntry }.map { (it as LibraryOrderEntry).libraryName }
                val rootModel = ModuleRootManager.getInstance(module).modifiableModel
                if (!libEntriesNames.contains(libName)) {
                    rootModel.addLibraryEntry(library)
                    System.out.println("Added $libName")
                }
                rootModel.commit()
             //   rootsChangedExternally = true
            }
        }

    }

    private object LibHeaderPsiTreeChangeListener: PsiTreeChangeAdapter() {
        override fun childAdded(event: PsiTreeChangeEvent) { sync(event) }

        override fun childRemoved(event: PsiTreeChangeEvent) { sync(event) }

        override fun childReplaced(event: PsiTreeChangeEvent) { sync(event) }

        override fun childMoved(event: PsiTreeChangeEvent) { sync(event) }

        override fun childrenChanged(event: PsiTreeChangeEvent) { sync(event) }

        private fun sync(event: PsiTreeChangeEvent) {
            val file = event.file as? YAMLFile ?: return

            if (isDependency(event.child)) {
                val project = event.child.project
                for (module in project.arendModules) {
                    if (module.name == file.libName) {
                        syncModuleDependencies(module, mutableSetOf(), false)
                    }
                }
                cleanObsoleteProjectLibraries(project)
            }
        }

        private fun isDependency(element: PsiElement?): Boolean {
            val parent = element?.parent?.parent?.parent?.parent ?: return false
            return parent is YAMLKeyValue && parent.keyText == "dependencies"
        }
    }
}
