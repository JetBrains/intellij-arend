package org.vclang.typechecking

import com.jetbrains.jetpad.vclang.core.definition.Definition
import com.jetbrains.jetpad.vclang.error.ErrorReporter
import com.jetbrains.jetpad.vclang.module.ModulePath
import com.jetbrains.jetpad.vclang.naming.reference.LocatedReferable
import com.jetbrains.jetpad.vclang.typechecking.TypecheckerState
import com.jetbrains.jetpad.vclang.typechecking.Typechecking
import com.jetbrains.jetpad.vclang.typechecking.order.DependencyListener
import com.jetbrains.jetpad.vclang.typechecking.typecheckable.provider.ConcreteProvider
import org.vclang.psi.ext.PsiLocatedReferable
import org.vclang.typechecking.execution.TypecheckingEventsProcessor


class TestBasedTypechecking(
    private val eventsProcessor: TypecheckingEventsProcessor,
    state: TypecheckerState,
    concreteProvider: ConcreteProvider,
    errorReporter: ErrorReporter,
    dependencyListener: DependencyListener)
    : Typechecking(state, concreteProvider, errorReporter, dependencyListener) {

    private val typecheckedModules = LinkedHashSet<ModulePath>()
    private val typecheckedModulesWithErrors = HashSet<ModulePath>()

    override fun typecheckingUnitStarted(definition: LocatedReferable) {
        eventsProcessor.startTimer(definition)
    }

    override fun typecheckingUnitFinished(referable: LocatedReferable, definition: Definition) {
        eventsProcessor.stopTimer(referable)

        val ref = referable as? PsiLocatedReferable ?: return
        if (definition.status() != Definition.TypeCheckingStatus.NO_ERRORS) {
            eventsProcessor.onTestFailure(ref)
        }
        eventsProcessor.onTestFinished(ref)

        val modulePath = referable.getLocation() ?: return
        if (definition.status() == Definition.TypeCheckingStatus.NO_ERRORS) {
            typecheckedModules.add(modulePath)
        } else {
            typecheckedModulesWithErrors.add(modulePath)
        }
    }

    override fun typecheckingHeaderStarted(definition: LocatedReferable) {
        eventsProcessor.startTimer(definition)
    }

    override fun typecheckingHeaderFinished(referable: LocatedReferable, definition: Definition?) {
        eventsProcessor.stopTimer(referable)
    }

    override fun typecheckingBodyStarted(definition: LocatedReferable) {
        eventsProcessor.startTimer(definition)
    }

    override fun typecheckingBodyFinished(referable: LocatedReferable, definition: Definition) {
        typecheckingUnitFinished(referable, definition)
    }

    val typecheckedModulesWithoutErrors
        get() = typecheckedModules.minus(typecheckedModulesWithErrors)
}