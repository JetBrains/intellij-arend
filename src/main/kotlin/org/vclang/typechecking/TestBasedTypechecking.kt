package org.vclang.typechecking

import com.jetbrains.jetpad.vclang.core.definition.Definition
import com.jetbrains.jetpad.vclang.error.ErrorReporter
import com.jetbrains.jetpad.vclang.naming.reference.GlobalReferable
import com.jetbrains.jetpad.vclang.typechecking.TypecheckerState
import com.jetbrains.jetpad.vclang.typechecking.Typechecking
import com.jetbrains.jetpad.vclang.typechecking.order.DependencyListener
import com.jetbrains.jetpad.vclang.typechecking.typecheckable.provider.ConcreteProvider
import org.vclang.psi.ext.PsiGlobalReferable
import org.vclang.typechecking.execution.TypecheckingEventsProcessor


class TestBasedTypechecking(
    private val eventsProcessor: TypecheckingEventsProcessor,
    state: TypecheckerState,
    concreteProvider: ConcreteProvider,
    errorReporter: ErrorReporter,
    dependencyListener: DependencyListener)
    : Typechecking(state, concreteProvider, errorReporter, dependencyListener) {

    override fun typecheckingStarted(definition: GlobalReferable) {
        if (definition is PsiGlobalReferable) {
            eventsProcessor.onTestStarted(definition)
        }
    }

    override fun typecheckingFinished(definition: Definition) {
        val ref = definition.referable as? PsiGlobalReferable ?: return
        if (definition.status() != Definition.TypeCheckingStatus.NO_ERRORS) {
            eventsProcessor.onTestFailure(ref)
        }
        eventsProcessor.onTestFinished(ref)
    }
}