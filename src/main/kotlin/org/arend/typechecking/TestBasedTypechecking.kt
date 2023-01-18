package org.arend.typechecking

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import org.arend.core.definition.Definition
import org.arend.naming.reference.TCDefReferable
import org.arend.psi.ArendFile
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.ext.TCDefinition
import org.arend.typechecking.error.ErrorService
import org.arend.typechecking.error.TypecheckingErrorReporter
import org.arend.typechecking.execution.TypecheckingEventsProcessor
import org.arend.typechecking.order.dependency.DependencyListener
import org.arend.typechecking.provider.ConcreteProvider


class TestBasedTypechecking(
    private val eventsProcessor: TypecheckingEventsProcessor,
    instanceProviderSet: PsiInstanceProviderSet,
    typeCheckingService: TypeCheckingService,
    concreteProvider: ConcreteProvider,
    private val errorReporter: TypecheckingErrorReporter,
    dependencyListener: DependencyListener)
    : ArendTypechecking(typeCheckingService, instanceProviderSet, concreteProvider, errorReporter, dependencyListener, LibraryArendExtensionProvider(typeCheckingService.libraryManager)) {

    private val definitionBlacklistService = service<DefinitionBlacklistService>()
    val filesToRestart = LinkedHashSet<ArendFile>()

    private fun startTypechecking(definition: PsiLocatedReferable, clearErrors: Boolean) {
        if (clearErrors && definition is TCDefinition) {
            runReadAction {
                typeCheckingService.project.service<ErrorService>().clearTypecheckingErrors(definition)
            }
        }
        eventsProcessor.startTimer(definition)
    }

    private fun stopTimer(definition: TCDefReferable) {
        PsiLocatedReferable.fromReferable(definition)?.let { eventsProcessor.stopTimer(it) }
    }

    override fun typecheckingUnitStarted(definition: TCDefReferable) {
        startTypechecking(PsiLocatedReferable.fromReferable(definition) ?: return, true)
    }

    override fun typecheckingFinished(ref: PsiLocatedReferable?, definition: Definition) {
        if (ref == null) {
            super.typecheckingFinished(ref, definition)
            return
        }

        eventsProcessor.stopTimer(ref)?.let { diff ->
            if (ref is TCDefinition && definitionBlacklistService.removeFromBlacklist(ref, (diff / 1000).toInt())) {
                runReadAction {
                    val file = ref.containingFile as? ArendFile ?: return@runReadAction
                    filesToRestart.add(file)
                }
            }
        }

        errorReporter.flush()
        super.typecheckingFinished(ref, definition)
        if (!definition.status().isOK) {
            eventsProcessor.onTestFailure(ref)
        }
        eventsProcessor.onTestFinished(ref)
    }

    override fun typecheckingHeaderStarted(definition: TCDefReferable) {
        startTypechecking(PsiLocatedReferable.fromReferable(definition) ?: return, true)
    }

    override fun typecheckingHeaderFinished(referable: TCDefReferable, definition: Definition?) {
        stopTimer(referable)
    }

    override fun typecheckingBodyStarted(definition: TCDefReferable) {
        startTypechecking(PsiLocatedReferable.fromReferable(definition) ?: return, false)
    }

    override fun typecheckingInterrupted(definition: TCDefReferable, typechecked: Definition?) {
        val ref = PsiLocatedReferable.fromReferable(definition) ?: return
        eventsProcessor.stopTimer(ref)
        eventsProcessor.onTestFailure(ref, true)
        eventsProcessor.onTestFinished(ref)
    }
}