package org.arend.typechecking

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.arend.psi.ArendFile
import org.arend.psi.listener.ArendPsiChangeService
import org.arend.settings.ArendSettings
import org.arend.term.concrete.Concrete
import org.arend.typechecking.error.ErrorService
import org.arend.typechecking.error.NotificationErrorReporter
import org.arend.typechecking.order.listener.CollectingOrderingListener
import org.arend.typechecking.provider.ConcreteProvider
import org.arend.typechecking.visitor.DesugarVisitor
import org.arend.typechecking.visitor.DumbTypechecker
import org.arend.util.FullName

class BackgroundTypechecker(private val project: Project, private val modificationCount: Long) {
    private val definitionBlackListService = service<DefinitionBlacklistService>()
    private val modificationTracker = project.service<ArendPsiChangeService>().definitionModificationTracker
    private val errorService: ErrorService = project.service()

    fun runTypechecker(instanceProviderSet: PsiInstanceProviderSet, concreteProvider: ConcreteProvider, file: ArendFile, collector: CollectingOrderingListener): Boolean {
        if (collector.isEmpty) {
            return true
        }

        val mode = service<ArendSettings>().typecheckingMode
        if (mode == ArendSettings.TypecheckingMode.OFF) {
            return true
        }

        if (mode == ArendSettings.TypecheckingMode.DUMB) {
            for (def in collector.allDefinitions) {
                runDumbTypechecker(def)
            }
            return true
        }

        if (file.lastDefinitionModification >= modificationCount) {
            return false
        }

        val tcService = project.service<TypeCheckingService>()
        val typechecking = ArendTypechecking(instanceProviderSet, concreteProvider, errorService, tcService.dependencyListener, LibraryArendExtensionProvider(tcService.libraryManager))
        for (element in collector.elements) {
            if (!typecheckDefinition(typechecking, element)) {
                return false
            }
        }

        synchronized(BackgroundTypechecker::class.java) {
            if (file.lastDefinitionModification < modificationCount) {
                file.lastDefinitionModification = modificationCount
            }
        }

        return true
    }

    private fun typecheckDefinition(typechecking: ArendTypechecking, element: CollectingOrderingListener.Element): Boolean {
        val indicator = ModificationCancellationIndicator(modificationTracker, modificationCount)
        val def = element.anyDefinition.data
        val ok = definitionBlackListService.runTimed(def, indicator) {
            typechecking.run(indicator) {
                element.feedTo(typechecking)
                true
            }
        }

        if (ok == null) {
            NotificationErrorReporter(project).warn("Typechecking of ${FullName(def)} was interrupted after ${service<ArendSettings>().typecheckingTimeLimit} second(s)")
        }

        return ok == true
    }

    private fun runDumbTypechecker(def: Concrete.ResolvableDefinition) {
        DesugarVisitor.desugar(def, errorService)
        def.accept(DumbTypechecker(errorService), null)
    }
}
