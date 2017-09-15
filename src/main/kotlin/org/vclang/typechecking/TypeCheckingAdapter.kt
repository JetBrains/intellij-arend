package org.vclang.typechecking

import com.intellij.execution.testframework.sm.runner.events.TestFailedEvent
import com.intellij.execution.testframework.sm.runner.events.TestFinishedEvent
import com.intellij.execution.testframework.sm.runner.events.TestSuiteFinishedEvent
import com.jetbrains.jetpad.vclang.error.ErrorReporter
import com.jetbrains.jetpad.vclang.naming.namespace.DynamicNamespaceProvider
import com.jetbrains.jetpad.vclang.naming.namespace.StaticNamespaceProvider
import com.jetbrains.jetpad.vclang.term.Concrete
import com.jetbrains.jetpad.vclang.term.Group
import com.jetbrains.jetpad.vclang.typechecking.TypecheckedReporter
import com.jetbrains.jetpad.vclang.typechecking.TypecheckerState
import com.jetbrains.jetpad.vclang.typechecking.Typechecking
import com.jetbrains.jetpad.vclang.typechecking.order.DependencyCollector
import com.jetbrains.jetpad.vclang.typechecking.typecheckable.provider.ConcreteProvider
import org.vclang.psi.ext.PsiGlobalReferable
import org.vclang.typechecking.execution.TypeCheckingEventsProcessor

class TypeCheckingAdapter(
        state: TypecheckerState,
        staticNsProvider: StaticNamespaceProvider,
        dynamicNsProvider: DynamicNamespaceProvider,
        concreteProvider: ConcreteProvider,
        errorReporter: ErrorReporter,
        private val dependencyListener: DependencyCollector,
        private val eventsProcessor: TypeCheckingEventsProcessor
) {
    private val typeChecking = Typechecking(
            state,
            staticNsProvider,
            dynamicNsProvider,
            concreteProvider,
            errorReporter,
            MyTypeCheckedReporter(),
            dependencyListener
    )

    init {
        eventsProcessor.onTestsReporterAttached()
    }

    fun typeCheckDefinition(definition: Concrete.Definition) =
        typeChecking.typecheckDefinitions(listOf(definition))

    fun typeCheckModule(module: Group) {
        module.subgroups.forEach { dependencyListener.update(it.referable) }
        typeChecking.typecheckModules(listOf(module))
        eventsProcessor.onSuiteFinished(TestSuiteFinishedEvent(module.referable.textRepresentation()))
    }

    private inner class MyTypeCheckedReporter : TypecheckedReporter {

        override fun typecheckingSucceeded(definition: Concrete.Definition) {
            val ref = definition.referable as? PsiGlobalReferable ?: return
            eventsProcessor.onTestFinished(TestFinishedEvent(ref.fullName, null))
        }

        override fun typecheckingFailed(definition: Concrete.Definition) {
            val ref = definition.referable as? PsiGlobalReferable ?: return
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
}
