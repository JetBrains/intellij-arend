package org.arend

import com.intellij.ProjectTopics
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.startup.StartupActivity
import org.arend.module.ArendModuleType
import org.arend.module.ArendRawLibrary
import org.arend.typechecking.TypeCheckingService
import org.arend.util.arendModules
import org.arend.util.register


class ArendStartupActivity : StartupActivity {
    override fun runActivity(project: Project) {
        val libraryManager = project.service<TypeCheckingService>().libraryManager

        project.messageBus.connect(project).subscribe(ProjectTopics.MODULES, object : ModuleListener {
            override fun moduleAdded(project: Project, module: Module) {
                if (ArendModuleType.has(module)) {
                    module.register()
                }
            }

            override fun beforeModuleRemoved(project: Project, module: Module) {
                ArendRawLibrary.getLibraryFor(libraryManager, module)?.let {
                    libraryManager.unloadLibrary(it)
                }
            }
        })

        ProjectManager.getInstance().addProjectManagerListener(project, object : ProjectManagerListener {
            override fun projectClosed(project: Project) {
                libraryManager.unload()
            }
        })

        for (module in project.arendModules) {
            module.register()
        }
    }
}
