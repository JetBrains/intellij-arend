package org.arend.typechecking

import com.intellij.openapi.application.runReadAction
import org.arend.core.definition.Definition
import org.arend.naming.reference.TCReferable
import org.arend.naming.reference.converter.ReferableConverter
import org.arend.psi.ArendDefinition
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.resolving.DataLocatedReferable
import org.arend.typechecking.error.TypecheckingErrorReporter
import org.arend.typechecking.execution.TypecheckingEventsProcessor
import org.arend.typechecking.order.dependency.DependencyListener
import org.arend.typechecking.typecheckable.provider.ConcreteProvider


class TestBasedTypechecking(
    private val eventsProcessor: TypecheckingEventsProcessor,
    instanceProviderSet: PsiInstanceProviderSet,
    private val typeCheckingService: TypeCheckingService,
    concreteProvider: ConcreteProvider,
    referableConverter: ReferableConverter,
    private val errorReporter: TypecheckingErrorReporter,
    dependencyListener: DependencyListener)
    : SilentTypechecking(instanceProviderSet, typeCheckingService, concreteProvider, referableConverter, errorReporter, dependencyListener) {

    private fun startTypechecking(definition: TCReferable, clearErrors: Boolean) {
        val psiPtr = (definition as? DataLocatedReferable)?.data ?: return
        val element = runReadAction {
            val element = psiPtr.element
            if (clearErrors && element is ArendDefinition) {
                typeCheckingService.clearErrors(element)
            }
            element
        }
        if (element is PsiLocatedReferable) {
            eventsProcessor.startTimer(element)
        }
    }

    private fun stopTimer(definition: TCReferable) {
        val psiPtr = (definition as? DataLocatedReferable)?.data ?: return
        (runReadAction { psiPtr.element } as? PsiLocatedReferable)?.let { eventsProcessor.stopTimer(it) }
    }

    override fun typecheckingUnitStarted(definition: TCReferable) {
        startTypechecking(definition, true)
    }

    override fun typecheckingFinished(ref: PsiLocatedReferable, definition: Definition) {
        super.typecheckingFinished(ref, definition)
        if (!definition.status().isOK) {
            eventsProcessor.onTestFailure(ref)
        }
        eventsProcessor.onTestFinished(ref)
    }

    override fun typecheckingHeaderStarted(definition: TCReferable) {
        startTypechecking(definition, true)
    }

    override fun typecheckingHeaderFinished(referable: TCReferable, definition: Definition?) {
        stopTimer(referable)
    }

    override fun typecheckingBodyStarted(definition: TCReferable) {
        startTypechecking(definition, false)
    }

    override fun typecheckingUnitFinished(referable: TCReferable, definition: Definition) {
        errorReporter.flush()
        super.typecheckingUnitFinished(referable, definition)
    }

    override fun typecheckingBodyFinished(referable: TCReferable, definition: Definition) {
        errorReporter.flush()
        super.typecheckingBodyFinished(referable, definition)
    }

    override fun typecheckingInterrupted(definition: TCReferable) {
        val ref = PsiLocatedReferable.fromReferable(definition) ?: return
        eventsProcessor.stopTimer(ref)
        eventsProcessor.onTestFailure(ref, true)
        eventsProcessor.onTestFinished(ref)
    }
}