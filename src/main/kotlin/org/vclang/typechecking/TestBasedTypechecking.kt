package org.vclang.typechecking

import com.intellij.execution.testframework.sm.runner.events.TestFailedEvent
import com.intellij.execution.testframework.sm.runner.events.TestFinishedEvent
import com.intellij.execution.testframework.sm.runner.events.TestStartedEvent
import com.jetbrains.jetpad.vclang.core.definition.Definition
import com.jetbrains.jetpad.vclang.error.ErrorReporter
import com.jetbrains.jetpad.vclang.naming.reference.GlobalReferable
import com.jetbrains.jetpad.vclang.typechecking.TypecheckerState
import com.jetbrains.jetpad.vclang.typechecking.Typechecking
import com.jetbrains.jetpad.vclang.typechecking.order.DependencyListener
import com.jetbrains.jetpad.vclang.typechecking.typecheckable.provider.ConcreteProvider
import org.vclang.psi.ext.PsiGlobalReferable
import org.vclang.psi.ext.fullName
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
            eventsProcessor.onTestStarted(TestStartedEvent(definition.fullName, null))
        }
    }

    override fun typecheckingFinished(definition: Definition) {
        val ref = definition.referable as? PsiGlobalReferable ?: return
        val definitionName = ref.fullName
        if (definition.status() != Definition.TypeCheckingStatus.NO_ERRORS) {
            val definitionProxy = eventsProcessor.getProxyByFullName(definitionName)
            val hasErrors = definitionProxy?.hasErrors() ?: false
            eventsProcessor.onTestFailure(TestFailedEvent(definitionName, "", null, hasErrors, null, null))
        }
        eventsProcessor.onTestFinished(TestFinishedEvent(definitionName, null))
    }
}