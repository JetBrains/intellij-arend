package org.vclang

import com.intellij.ProjectTopics
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.startup.StartupActivity
import com.jetbrains.jetpad.vclang.error.DummyErrorReporter
import com.jetbrains.jetpad.vclang.prelude.Prelude
import org.vclang.module.VcPreludeLibrary
import org.vclang.module.VcRawLibrary
import org.vclang.module.util.isVcModule
import org.vclang.resolving.PsiConcreteProvider
import org.vclang.typechecking.TypeCheckingService

class VcStartupActivity : StartupActivity {
    override fun runActivity(project: Project) {
        if (ProjectRootManager.getInstance(project).contentSourceRoots.isEmpty()) {
            Notifications.Bus.notify(Notification("","No source roots detected!", "", NotificationType.ERROR), project)
        }

        val service = TypeCheckingService.getInstance(project)

        // TODO[references]: Load typechecked prelude library from resources
        val preludeLibrary = VcPreludeLibrary(project, service.typecheckerState)
        service.libraryManager.loadLibrary(preludeLibrary)
        val referableConverter = service.referableConverter
        val concreteProvider = PsiConcreteProvider(referableConverter, DummyErrorReporter.INSTANCE, null)
        preludeLibrary.resolveNames(referableConverter, concreteProvider, service.libraryManager.typecheckingErrorReporter)
        preludeLibrary.typecheck(Prelude.PreludeTypechecking(service.typecheckerState, concreteProvider))

        for (module in project.vcModules) {
            service.libraryManager.loadLibrary(VcRawLibrary(module, service.typecheckerState))
        }

        project.messageBus.connect(project).subscribe(ProjectTopics.MODULES, object : ModuleListener {
            override fun moduleAdded(project: Project, module: Module) {
                if (module.isVcModule) {
                    service.libraryManager.loadLibrary(VcRawLibrary(module, service.typecheckerState))
                }
            }

            override fun beforeModuleRemoved(project: Project, module: Module) {
                service.libraryManager.getRegisteredLibrary(module.name)?.let { service.libraryManager.unloadLibrary(it) }
            }
        })

        ProjectManager.getInstance().addProjectManagerListener(project, object : ProjectManagerListener {
            override fun projectClosed(project: Project?) {
                service.libraryManager.unload()
            }
        })
    }
}