package org.vclang.typechecking

import com.intellij.execution.testframework.sm.runner.events.TestFailedEvent
import com.intellij.execution.testframework.sm.runner.events.TestFinishedEvent
import com.intellij.execution.testframework.sm.runner.events.TestSuiteFinishedEvent
import com.jetbrains.jetpad.vclang.error.ErrorReporter
import com.jetbrains.jetpad.vclang.frontend.resolving.OpenCommand
import com.jetbrains.jetpad.vclang.naming.namespace.DynamicNamespaceProvider
import com.jetbrains.jetpad.vclang.naming.namespace.StaticNamespaceProvider
import com.jetbrains.jetpad.vclang.term.Abstract
import com.jetbrains.jetpad.vclang.typechecking.TypecheckedReporter
import com.jetbrains.jetpad.vclang.typechecking.TypecheckerState
import com.jetbrains.jetpad.vclang.typechecking.Typechecking
import com.jetbrains.jetpad.vclang.typechecking.order.DependencyCollector
import org.vclang.parser.fullName
import org.vclang.typechecking.execution.TypeCheckingEventsProcessor
import java.util.function.Function

class TypeCheckingAdapter(
        state: TypecheckerState,
        staticNsProvider: StaticNamespaceProvider,
        dynamicNsProvider: DynamicNamespaceProvider,
        opens: Function<Abstract.Definition, Iterable<OpenCommand>>,
        errorReporter: ErrorReporter,
        private val dependencyListener: DependencyCollector,
        private val eventsProcessor: TypeCheckingEventsProcessor
) {
    private val typeChecking = Typechecking(
            state,
            staticNsProvider,
            dynamicNsProvider,
            opens,
            errorReporter,
            MyTypeCheckedReporter(),
            dependencyListener
    )

    init {
        eventsProcessor.onTestsReporterAttached()
    }

    fun typeCheckDefinition(definition: Abstract.Definition) =
        typeChecking.typecheckDefinitions(listOf(definition))

    fun typeCheckModule(module: Abstract.ClassDefinition) {
        module.globalDefinitions.forEach { dependencyListener.update(it) }
        typeChecking.typecheckModules(listOf(module))
        eventsProcessor.onSuiteFinished(TestSuiteFinishedEvent(module.name!!))
    }

    private inner class MyTypeCheckedReporter : TypecheckedReporter {

        override fun typecheckingSucceeded(definition: Abstract.Definition) {
            val definitionName = definition.fullName
            eventsProcessor.onTestFinished(TestFinishedEvent(definitionName, null))
        }

        override fun typecheckingFailed(definition: Abstract.Definition) {
            val definitionName = definition.fullName
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
