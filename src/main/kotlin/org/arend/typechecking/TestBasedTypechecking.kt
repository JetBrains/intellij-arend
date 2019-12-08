package org.arend.typechecking

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import org.arend.core.definition.Definition
import org.arend.naming.reference.TCReferable
import org.arend.naming.reference.converter.ReferableConverter
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
    private val typeCheckingService: TypeCheckingService,
    concreteProvider: ConcreteProvider,
    referableConverter: ReferableConverter,
    private val errorReporter: TypecheckingErrorReporter,
    dependencyListener: DependencyListener)
    : ArendTypechecking(instanceProviderSet, typeCheckingService.typecheckerState, concreteProvider, referableConverter, errorReporter, dependencyListener) {

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

    private fun stopTimer(definition: TCReferable) {
        PsiLocatedReferable.fromReferable(definition)?.let { eventsProcessor.stopTimer(it) }
    }

    override fun typecheckingUnitStarted(definition: TCReferable) {
        startTypechecking(PsiLocatedReferable.fromReferable(definition) ?: return, true)
    }

    override fun typecheckingFinished(ref: PsiLocatedReferable, definition: Definition) {
        eventsProcessor.stopTimer(ref)?.let { diff ->
            if (ref is TCDefinition && definitionBlacklistService.removeFromBlacklist(ref, (diff / 1000).toInt())) {
                runReadAction {
                    val file = ref.containingFile as? ArendFile ?: return@runReadAction
                    if (definition.status().withoutErrors()) {
                        file.lastModifiedDefinition = null
                    }
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

    override fun typecheckingHeaderStarted(definition: TCReferable) {
        startTypechecking(PsiLocatedReferable.fromReferable(definition) ?: return, true)
    }

    override fun typecheckingHeaderFinished(referable: TCReferable, definition: Definition?) {
        stopTimer(referable)
    }

    override fun typecheckingBodyStarted(definition: TCReferable) {
        startTypechecking(PsiLocatedReferable.fromReferable(definition) ?: return, false)
    }

    override fun typecheckingInterrupted(definition: TCReferable, typechecked: Definition?) {
        val ref = PsiLocatedReferable.fromReferable(definition) ?: return
        eventsProcessor.stopTimer(ref)
        eventsProcessor.onTestFailure(ref, true)
        eventsProcessor.onTestFinished(ref)
    }
}