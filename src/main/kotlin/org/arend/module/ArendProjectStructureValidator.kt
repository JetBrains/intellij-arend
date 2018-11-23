package org.arend.module

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureValidator

class ArendProjectStructureValidator: ProjectStructureValidator() {
    override fun addLibraryToDependencies(library: Library, project: Project, allowEmptySelection: Boolean): Boolean {
        /*
        val dlg = ChooseModulesDialog(project, project.arendModules, ProjectBundle.message("choose.modules.dialog.title"),
                ProjectBundle
                        .message("choose.modules.dialog.description", library.name))
        if (dlg.showAndGet()) {
            val chosenModules = dlg.chosenElements
            ApplicationManager.getApplication().invokeLater {
                WriteAction.run<Exception> {
                    for (module in chosenModules) {
                        val rootModel = ModuleRootManager.getInstance(module).modifiableModel
                        val orderEntries = ModuleRootManager.getInstance(module).orderEntries
                        val libEntriesNames = orderEntries.filter { it is LibraryOrderEntry }.map { (it as LibraryOrderEntry).libraryName }.toSet()
                        //ArendStartupActivity.rootsChangedExternally = false
                        //library.name?.let { module.libraryConfig?.addDependency(it) }
                        if (!libEntriesNames.contains(library.name)) {
                            rootModel.addLibraryEntry(library)
                        }
                        rootModel.commit()
                        //ArendStartupActivity.rootsChangedExternally = true
                    }
                }
            }
        } */
        return false
    }
}