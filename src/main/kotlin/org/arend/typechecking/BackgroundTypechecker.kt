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

class BackgroundTypechecker(private val project: Project, private val modificationCount: Long) {
    private val definitionBlackListService = service<DefinitionBlacklistService>()
    private val modificationTracker = project.service<ArendPsiChangeService>().definitionModificationTracker
    private val errorService: ErrorService = project.service()

    fun runTypechecker(concreteProvider: ConcreteProvider, file: ArendFile, defs: List<Concrete.Definition>): Boolean {
        if (defs.isEmpty()) {
            return true
        }

        val mode = service<ArendSettings>().typecheckingMode
        if (mode == ArendSettings.TypecheckingMode.OFF) {
            return true
        }

        if (mode == ArendSettings.TypecheckingMode.DUMB) {
            for (def in defs) {
                runDumbTypechecker(def)
            }
            return true
        }

        if (file.lastDefinitionModification >= modificationCount) {
            return false
        }

        val typechecking = ArendTypechecking.create(project, concreteProvider)
        val lastModified = runReadAction { file.lastModifiedDefinition }?.let { concreteProvider.getConcrete(it) as? Concrete.Definition }
        if (lastModified != null) {
            if (defs.contains(lastModified)) {
                if (!typecheckDefinition(typechecking, lastModified)) {
                    return false
                }
            }
            if (lastModified.data.typechecked?.status()?.withoutErrors() == true) {
                file.lastModifiedDefinition = null
                for (def in defs) {
                    if (def.data != lastModified) {
                        if (!typecheckDefinition(typechecking, def)) {
                            return false
                        }
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
                if (!typecheckDefinition(typechecking, definition)) {
                    return false
                }
            }
        }

        synchronized(BackgroundTypechecker::class.java) {
            if (file.lastDefinitionModification < modificationCount) {
                file.lastDefinitionModification = modificationCount
            }
        }

        return true
    }

    private fun typecheckDefinition(typechecking: ArendTypechecking, def: Concrete.Definition): Boolean {
        val indicator = ModificationCancellationIndicator(modificationTracker, modificationCount)
        try {
            val ok = definitionBlackListService.runTimed(def.data, indicator) {
                typechecking.typecheckDefinitions(listOf(def), indicator)
            }

            if (ok == null) {
                NotificationErrorReporter(project).warn("Typechecking of ${FullName(def.data)} was interrupted after ${service<ArendSettings>().typecheckingTimeLimit} second(s)")
            }

            return ok == true
        } finally {
            if (indicator.isCanceled) {
                def.data.typechecked = null
            }
        }
    }

    private fun runDumbTypechecker(def: Concrete.Definition) {
        DesugarVisitor.desugar(def, errorService)
        def.accept(DumbTypechecker(errorService), null)
    }
}
