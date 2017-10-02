package org.vclang.typechecking

import com.intellij.execution.testframework.sm.runner.events.TestFailedEvent
import com.intellij.execution.testframework.sm.runner.events.TestFinishedEvent
import com.jetbrains.jetpad.vclang.term.concrete.Concrete
import com.jetbrains.jetpad.vclang.typechecking.TypecheckedReporter
import org.vclang.psi.ext.PsiGlobalReferable
import org.vclang.psi.ext.fullName
import org.vclang.typechecking.execution.TypecheckingEventsProcessor


class TestResultReporter(private val eventsProcessor: TypecheckingEventsProcessor): TypecheckedReporter {
    override fun typecheckingSucceeded(definition: Concrete.Definition) {
        val ref = definition.data as? PsiGlobalReferable ?: return
        eventsProcessor.onTestFinished(TestFinishedEvent(ref.fullName, null))
    }

    override fun typecheckingFailed(definition: Concrete.Definition) {
        typecheckingFailed(definition.data as? PsiGlobalReferable ?: return)
    }

    fun typecheckingFailed(ref: PsiGlobalReferable) {
        val definitionName = ref.fullName
        val definitionProxy = eventsProcessor.getProxyByFullName(definitionName)
        val hasErrors = definitionProxy?.hasErrors() ?: false
        eventsProcessor.onTestFailure(TestFailedEvent(
            definitionName,
            "",
            null,
            hasErrors,
            null,
            null
        ))
        eventsProcessor.onTestFinished(TestFinishedEvent(definitionName, null))
    }
}