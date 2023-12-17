package org.arend

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.DumbModeTask
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import org.arend.module.ArendModuleType
import org.arend.module.config.ArendModuleConfigService
import org.arend.typechecking.TypeCheckingService
import org.arend.util.ArendBundle
import org.arend.util.arendModules
import org.arend.util.register


class ArendStartupActivity : StartupActivity.RequiredForSmartMode {
    override fun runActivity(project: Project) {
        val libraryManager = project.service<TypeCheckingService>().libraryManager

        project.messageBus.connect(project).subscribe(ModuleListener.TOPIC, object : ModuleListener {
            override fun modulesAdded(project: Project, modules: List<Module>) {
                for (module in modules) {
                    if (ArendModuleType.has(module)) {
                        module.register()
                    }
                }
            }

            override fun beforeModuleRemoved(project: Project, module: Module) {
                ArendModuleConfigService.getInstance(module)?.library?.let {
                    runReadAction {
                        libraryManager.unloadLibrary(it)
                    }
                }
            }
        })

        /* All resources are stored in TypeCheckingService, so they should be disposed anyway
        ProjectManager.getInstance().addProjectManagerListener(project, object : ProjectManagerListener {
            override fun projectClosing(project: Project) {
                libraryManager.unload()
            }
        })
        */

        DumbService.getInstance(project).queueTask(object : DumbModeTask() {
            override fun performInDumbMode(indicator: ProgressIndicator) {
                val modules = project.arendModules
                indicator.text = ArendBundle.message("arend.startup.loading.arend.modules")
                indicator.isIndeterminate = false
                indicator.fraction = 0.0
                val progressFraction = 1.0 / modules.size.toDouble()
                for (module in modules) {
                    module.register()
                    indicator.fraction += progressFraction
                }
            }
        })
    }
}
