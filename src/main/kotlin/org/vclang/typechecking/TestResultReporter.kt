package org.vclang.typechecking

import com.intellij.execution.testframework.sm.runner.events.TestFailedEvent
import com.intellij.execution.testframework.sm.runner.events.TestFinishedEvent
import com.jetbrains.jetpad.vclang.core.definition.Definition
import com.jetbrains.jetpad.vclang.typechecking.TypecheckedReporter
import org.vclang.psi.ext.PsiGlobalReferable
import org.vclang.psi.ext.fullName
import org.vclang.typechecking.execution.TypecheckingEventsProcessor


class TestResultReporter(private val eventsProcessor: TypecheckingEventsProcessor): TypecheckedReporter {
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