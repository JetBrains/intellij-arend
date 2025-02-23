package org.arend

import com.intellij.codeInsight.editorActions.SelectionQuotingTypedHandler
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.*
import com.intellij.openapi.startup.ProjectActivity
import org.arend.module.ArendModuleType
import org.arend.module.config.ArendModuleConfigService
import org.arend.server.ArendServerService
import org.arend.util.ArendBundle
import org.arend.util.arendModules
import org.arend.util.register


class ArendStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        val service = project.service<ArendServerService>()

        project.messageBus.connect(service).subscribe(ModuleListener.TOPIC, object : ModuleListener {
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
            }

            override fun moduleRemoved(project: Project, module: Module) {
                service.server.removeLibrary(module.name)
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
                        indicator.fraction += progressFraction
                    }
                }
            }
        })

        ApplicationManager.getApplication().messageBus.connect(service)
            .subscribe<AppLifecycleListener>(AppLifecycleListener.TOPIC, object : AppLifecycleListener {
                override fun appWillBeClosed(isRestart: Boolean) {
                    for (module in project.arendModules) {
                        ArendModuleConfigService.getInstance(module)?.saveSettings()
                    }
                }
            })

        // TODO[server2]: disableActions()
    }

    private fun disableActions() {
        val actionManager = ApplicationManager.getApplication().getServiceIfCreated(ActionManager::class.java)
        listOf(
            ArendBundle.message("arend.disableGroupSource"),
            ArendBundle.message("arend.disableActionExclude"),
            ArendBundle.message("arend.disableActionUnmark"),
            ArendBundle.message("arend.disableActionCompile")
        ).forEach { action ->
            actionManager?.getAction(action)?.let {
                actionManager.unregisterAction(action)
            }
        }
        TypedHandlerDelegate.EP_NAME.point.unregisterExtension(SelectionQuotingTypedHandler::class.java)
    }
}
