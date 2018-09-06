package com.jetbrains.arend.ide

import com.intellij.ProjectTopics
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.startup.StartupActivity
import com.jetbrains.arend.ide.module.ArdPreludeLibrary
import com.jetbrains.arend.ide.module.ArdRawLibrary
import com.jetbrains.arend.ide.module.util.isArdModule
import com.jetbrains.arend.ide.resolving.PsiConcreteProvider
import com.jetbrains.arend.ide.typechecking.PsiInstanceProviderSet
import com.jetbrains.arend.ide.typechecking.TypeCheckingService
import com.jetbrains.jetpad.vclang.error.DummyErrorReporter
import com.jetbrains.jetpad.vclang.prelude.Prelude

class ArdStartupActivity : StartupActivity {
    override fun runActivity(project: Project) {
        val service = TypeCheckingService.getInstance(project)

        // TODO[references]: Load typechecked prelude library from resources
        val preludeLibrary = ArdPreludeLibrary(project, service.typecheckerState)
        service.libraryManager.loadLibrary(preludeLibrary)
        val referableConverter = service.referableConverter
        val concreteProvider = PsiConcreteProvider(project, referableConverter, DummyErrorReporter.INSTANCE, null)
        preludeLibrary.resolveNames(referableConverter, concreteProvider, service.libraryManager.libraryErrorReporter)
        Prelude.PreludeTypechecking(PsiInstanceProviderSet(concreteProvider, referableConverter), service.typecheckerState, concreteProvider).typecheckLibrary(preludeLibrary)

        for (module in project.vcModules) {
            service.libraryManager.loadLibrary(ArdRawLibrary(module, service.typecheckerState))
        }

        project.messageBus.connect(project).subscribe(ProjectTopics.MODULES, object : ModuleListener {
            override fun moduleAdded(project: Project, module: Module) {
                if (module.isArdModule) {
                    service.libraryManager.loadLibrary(ArdRawLibrary(module, service.typecheckerState))
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