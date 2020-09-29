package org.arend.typechecking

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.arend.psi.ArendFile
import org.arend.psi.listener.ArendPsiChangeService
import org.arend.settings.ArendSettings
import org.arend.term.concrete.Concrete
import org.arend.typechecking.error.ErrorService
import org.arend.typechecking.error.NotificationErrorReporter
import org.arend.typechecking.provider.ConcreteProvider
import org.arend.typechecking.visitor.DesugarVisitor
import org.arend.typechecking.visitor.DumbTypechecker
import org.arend.util.FullName

class BackgroundTypechecker(private val project: Project) {
    private val definitionBlackListService = service<DefinitionBlacklistService>()
    private val modificationTracker = project.service<ArendPsiChangeService>().definitionModificationTracker
    private val errorService: ErrorService = project.service()

    fun runTypechecker(concreteProvider: ConcreteProvider, file: ArendFile, defs: List<Concrete.Definition>) {
        if (defs.isEmpty()) {
            return
        }

        val mode = service<ArendSettings>().typecheckingMode
        if (mode == ArendSettings.TypecheckingMode.OFF) {
            return
        }

        if (mode == ArendSettings.TypecheckingMode.DUMB) {
            for (def in defs) {
                runDumbTypechecker(def)
            }
            return
        }

        val modCount = modificationTracker.modificationCount
        if (file.lastDefinitionModification >= modCount) {
            return
        }

        val typechecking = ArendTypechecking.create(project, concreteProvider)
        val lastModified = runReadAction { file.lastModifiedDefinition }?.let { concreteProvider.getConcrete(it) as? Concrete.Definition }
        if (lastModified != null) {
            val typechecked = if (defs.contains(lastModified)) {
                typecheckDefinition(typechecking, lastModified)?.data?.typechecked
            } else null
            if (typechecked?.status()?.withoutErrors() == true) {
                file.lastModifiedDefinition = null
                for (def in defs) {
                    if (def.data != lastModified) {
                        typecheckDefinition(typechecking, def)
                    }
                }
            } else {
                for (def in defs) {
                    if (def.data != lastModified) {
                        runDumbTypechecker(def)
                    }
                }
            }
        } else {
            for (definition in defs) {
                typecheckDefinition(typechecking, definition)
            }
        }

        synchronized(BackgroundTypechecker::class.java) {
            if (file.lastDefinitionModification < modCount) {
                file.lastDefinitionModification = modCount
            }
        }
    }

    private fun typecheckDefinition(typechecking: ArendTypechecking, def: Concrete.Definition): Concrete.Definition? {
        val indicator = ModificationCancellationIndicator(modificationTracker)
        try {
            val ok = definitionBlackListService.runTimed(def.data, indicator) {
                typechecking.typecheckDefinitions(listOf(def), indicator)
            }

            if (!ok) {
                NotificationErrorReporter(project).warn("Typechecking of ${FullName(def.data)} was interrupted after ${service<ArendSettings>().typecheckingTimeLimit} second(s)")
            }
        } finally {
            if (indicator.isCanceled) {
                def.data.typechecked = null
            }
        }
        return def
    }

    private fun runDumbTypechecker(def: Concrete.Definition) {
        DesugarVisitor.desugar(def, errorService)
        def.accept(DumbTypechecker(errorService), null)
    }
}
