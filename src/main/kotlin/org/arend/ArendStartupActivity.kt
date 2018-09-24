package org.arend

import com.intellij.ProjectTopics
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.startup.StartupActivity
import org.arend.error.DummyErrorReporter
import org.arend.module.ArendPreludeLibrary
import org.arend.module.ArendRawLibrary
import org.arend.module.util.isArendModule
import org.arend.prelude.Prelude
import org.arend.resolving.PsiConcreteProvider
import org.arend.typechecking.PsiInstanceProviderSet
import org.arend.typechecking.TypeCheckingService


class ArendStartupActivity : StartupActivity {
    override fun runActivity(project: Project) {
        val service = TypeCheckingService.getInstance(project)

        val preludeLibrary = ArendPreludeLibrary(project, service.typecheckerState)
        service.libraryManager.loadLibrary(preludeLibrary)
        val referableConverter = service.newReferableConverter(false)
        val concreteProvider = PsiConcreteProvider(project, referableConverter, DummyErrorReporter.INSTANCE, null)
        preludeLibrary.resolveNames(referableConverter, concreteProvider, service.libraryManager.libraryErrorReporter)
        Prelude.PreludeTypechecking(PsiInstanceProviderSet(concreteProvider, referableConverter), service.typecheckerState, concreteProvider).typecheckLibrary(preludeLibrary)

        for (module in project.arendModules) {
            service.libraryManager.loadLibrary(ArendRawLibrary(module, service.typecheckerState))
        }

        project.messageBus.connect(project).subscribe(ProjectTopics.MODULES, object : ModuleListener {
            override fun moduleAdded(project: Project, module: Module) {
                if (module.isArendModule) {
                    service.libraryManager.loadLibrary(ArendRawLibrary(module, service.typecheckerState))
                }
            }

            override fun beforeModuleRemoved(project: Project, module: Module) {
                service.libraryManager.getRegisteredLibrary(module.name)?.let { service.libraryManager.unloadLibrary(it) }
            }
        })

        ProjectManager.getInstance().addProjectManagerListener(project, object : ProjectManagerListener {
            override fun projectClosed(project: Project) {
                service.libraryManager.unload()
            }
        })
    }
}
