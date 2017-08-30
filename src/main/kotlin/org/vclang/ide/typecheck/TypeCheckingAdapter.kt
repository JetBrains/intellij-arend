package org.vclang.ide.typecheck

import com.intellij.execution.testframework.sm.runner.events.*
import com.jetbrains.jetpad.vclang.error.ErrorReporter
import com.jetbrains.jetpad.vclang.frontend.resolving.OpenCommand
import com.jetbrains.jetpad.vclang.naming.namespace.DynamicNamespaceProvider
import com.jetbrains.jetpad.vclang.naming.namespace.StaticNamespaceProvider
import com.jetbrains.jetpad.vclang.term.Abstract
import com.jetbrains.jetpad.vclang.term.BaseAbstractVisitor
import com.jetbrains.jetpad.vclang.typechecking.TypecheckedReporter
import com.jetbrains.jetpad.vclang.typechecking.TypecheckerState
import com.jetbrains.jetpad.vclang.typechecking.Typechecking
import com.jetbrains.jetpad.vclang.typechecking.order.DependencyCollector
import org.vclang.lang.core.parser.fullName
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

    fun typeCheckDefinition(definition: Abstract.Definition) {
        eventsProcessor.onTestStarted(TestStartedEvent(definition.fullName, null))
        val definitions = mutableSetOf<Abstract.Definition>()
        definition.accept(TestCollectVisitor(), definitions)
        definitions.forEach {
            dependencyListener.update(it)
            eventsProcessor.onTestStarted(TestStartedEvent(it.fullName, null))
        }
        typeChecking.typecheckDefinitions(listOf(definition))
    }

    fun typeCheckModule(module: Abstract.ClassDefinition) {
        eventsProcessor.onSuiteStarted(TestSuiteStartedEvent(module.name!!, null))
        val definitions = mutableSetOf<Abstract.Definition>()
        module.accept(TestCollectVisitor(), definitions)
        definitions.forEach {
            dependencyListener.update(it)
            eventsProcessor.onTestStarted(TestStartedEvent(it.fullName, null))
        }
        typeChecking.typecheckModules(listOf(module))
        eventsProcessor.onSuiteFinished(TestSuiteFinishedEvent(module.name!!))
    }

    private inner class MyTypeCheckedReporter : TypecheckedReporter {
        override fun typecheckingSucceeded(definition: Abstract.Definition) {
            val testName = definition.fullName
            if (eventsProcessor.isStarted(testName)) {
                eventsProcessor.onTestFinished(TestFinishedEvent(testName, null))
            }
        }

        override fun typecheckingFailed(definition: Abstract.Definition) {
            val testName = definition.fullName
            if (eventsProcessor.isStarted(testName)) {
                eventsProcessor.onTestFailure(TestFailedEvent(testName, "", null, true, null, null))
                eventsProcessor.onTestFinished(TestFinishedEvent(testName, null))
            }
        }
    }

    private inner class TestCollectVisitor
        : BaseAbstractVisitor<MutableSet<Abstract.Definition>, Void>() {

        override fun visitFunction(
            definition: Abstract.FunctionDefinition,
            params: MutableSet<Abstract.Definition>
        ): Void? {
            params.addAll(definition.globalDefinitions)
            return null
        }

        override fun visitClass(
            definition: Abstract.ClassDefinition,
            params: MutableSet<Abstract.Definition>
        ): Void? {
            params.addAll(definition.globalDefinitions)
            params.addAll(definition.instanceDefinitions)
            return null
        }
    }
}
