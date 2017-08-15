package org.vclang.ide.typecheck

import com.intellij.execution.testframework.sm.runner.events.TestFailedEvent
import com.intellij.execution.testframework.sm.runner.events.TestFinishedEvent
import com.intellij.execution.testframework.sm.runner.events.TestStartedEvent
import com.intellij.execution.testframework.sm.runner.events.TestSuiteFinishedEvent
import com.intellij.execution.testframework.sm.runner.events.TestSuiteStartedEvent
import com.jetbrains.jetpad.vclang.error.ErrorReporter
import com.jetbrains.jetpad.vclang.frontend.resolving.OpenCommand
import com.jetbrains.jetpad.vclang.naming.namespace.DynamicNamespaceProvider
import com.jetbrains.jetpad.vclang.naming.namespace.StaticNamespaceProvider
import com.jetbrains.jetpad.vclang.term.Abstract
import com.jetbrains.jetpad.vclang.term.BaseAbstractVisitor
import com.jetbrains.jetpad.vclang.typechecking.TypecheckedReporter
import com.jetbrains.jetpad.vclang.typechecking.TypecheckerState
import com.jetbrains.jetpad.vclang.typechecking.Typechecking
import com.jetbrains.jetpad.vclang.typechecking.order.DependencyListener
import org.vclang.lang.core.parser.fullyQualifiedName
import java.util.function.Function

class TypecheckingAdapter(
    state: TypecheckerState,
    staticNsProvider: StaticNamespaceProvider,
    dynamicNsProvider: DynamicNamespaceProvider,
    opens: Function<Abstract.Definition, Iterable<OpenCommand>>,
    errorReporter: ErrorReporter,
    dependencyListener: DependencyListener,
    private val eventsProcessor: TypecheckEventsProcessor
) {
    private val typechecking = Typechecking(
        state,
        staticNsProvider,
        dynamicNsProvider,
        opens,
        errorReporter,
        MyTypecheckedReporter(),
        dependencyListener
    )

    init {
        eventsProcessor.onTestsReporterAttached()
    }

    fun typecheckDefinition(definition: Abstract.Definition) {
        eventsProcessor.onTestStarted(TestStartedEvent(definition.fullyQualifiedName, null))
        definition.accept(TestStartVisitor(), null)
        typechecking.typecheckDefinitions(listOf(definition))
    }

    fun typecheckModule(module: Abstract.ClassDefinition) {
        eventsProcessor.onSuiteStarted(TestSuiteStartedEvent(module.name, null))
        module.accept(TestStartVisitor(), null)
        typechecking.typecheckModules(listOf(module))
        eventsProcessor.onSuiteFinished(TestSuiteFinishedEvent(module.name))
    }

    private inner class MyTypecheckedReporter : TypecheckedReporter {
        override fun typecheckingSucceeded(definition: Abstract.Definition) {
            val testName = definition.fullyQualifiedName
            if (eventsProcessor.isStarted(testName)) {
                eventsProcessor.onTestFinished(TestFinishedEvent(testName, null))
            }
        }

        override fun typecheckingFailed(definition: Abstract.Definition) {
            val testName = definition.fullyQualifiedName
            if (eventsProcessor.isStarted(testName)) {
                eventsProcessor.onTestFailure(TestFailedEvent(testName, "Some error", null, true, null, null))
                eventsProcessor.onTestFinished(TestFinishedEvent(testName, null))
            }
        }
    }

    private inner class TestStartVisitor : BaseAbstractVisitor<Void, Void>() {

        override fun visitFunction(definition: Abstract.FunctionDefinition, params: Void?): Void? {
            definition.globalDefinitions.forEach {
                eventsProcessor.onTestStarted(TestStartedEvent(it.fullyQualifiedName, null))
            }
            return null
        }

        override fun visitClass(definition: Abstract.ClassDefinition, params: Void?): Void? {
            definition.globalDefinitions.forEach {
                eventsProcessor.onTestStarted(TestStartedEvent(it.fullyQualifiedName, null))
            }
            definition.instanceDefinitions.forEach {
                eventsProcessor.onTestStarted(TestStartedEvent(it.fullyQualifiedName, null))
            }
            return null
        }
    }
}
