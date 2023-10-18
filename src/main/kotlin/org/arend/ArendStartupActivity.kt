package org.arend

import com.intellij.ProjectTopics
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.DumbModeTask
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import org.arend.module.ArendModuleType
import org.arend.module.config.ArendModuleConfigService
import org.arend.typechecking.TypeCheckingService
import org.arend.util.ArendBundle
import org.arend.util.arendModules
import org.arend.util.register


class ArendStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        val service = project.service<TypeCheckingService>()
        val libraryManager = service.libraryManager

        project.messageBus.connect(service).subscribe(ProjectTopics.MODULES, object : ModuleListener {
            override fun modulesAdded(project: Project, modules: List<Module>) {
                for (module in modules) {
                    if (ArendModuleType.has(module)) {
                        module.register()
                    }
                }
            }

            override fun beforeModuleRemoved(project: Project, module: Module) {
                val config = ArendModuleConfigService.getInstance(module) ?: return
                config.isInitialized = ApplicationManager.getApplication().isUnitTestMode
                runReadAction {
                    libraryManager.unloadLibrary(config.library)
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
                ApplicationManager.getApplication().executeOnPooledThread {
                    val modules = project.arendModules
                    indicator.text = ArendBundle.message("arend.startup.loading.arend.modules")
                    indicator.isIndeterminate = false
                    indicator.fraction = 0.0
                    val progressFraction = 1.0 / modules.size.toDouble()
                    for (module in modules) {
                        module.register()
                        indicator.fraction = indicator.fraction + progressFraction
                    }
                }
            }
        })
        disableActions()
    }

    companion object {
        fun disableActions() {
            val actionManager = ApplicationManager.getApplication().getServiceIfCreated(ActionManager::class.java)
            actionManager?.getAction(ArendBundle.message("arend.disableActionExclude"))?.let {
                actionManager.unregisterAction(ArendBundle.message("arend.disableActionExclude"))
            }
            actionManager?.getAction(ArendBundle.message("arend.disableGroupSource"))?.let {
                actionManager.unregisterAction(ArendBundle.message("arend.disableGroupSource"))
            }
            actionManager?.getAction(ArendBundle.message("arend.disableActionUnmark"))?.let {
                actionManager.unregisterAction(ArendBundle.message("arend.disableActionUnmark"))
            }
        }
    }
}
