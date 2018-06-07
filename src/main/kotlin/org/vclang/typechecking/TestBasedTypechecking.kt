package org.vclang.typechecking

import com.intellij.openapi.application.runReadAction
import com.jetbrains.jetpad.vclang.core.definition.Definition
import com.jetbrains.jetpad.vclang.error.ErrorReporter
import com.jetbrains.jetpad.vclang.naming.reference.TCReferable
import com.jetbrains.jetpad.vclang.typechecking.TypecheckerState
import com.jetbrains.jetpad.vclang.typechecking.order.listener.TypecheckingOrderingListener
import com.jetbrains.jetpad.vclang.typechecking.order.dependency.DependencyListener
import com.jetbrains.jetpad.vclang.typechecking.typecheckable.provider.ConcreteProvider
import org.vclang.psi.VcFile
import org.vclang.psi.ext.PsiLocatedReferable
import org.vclang.resolving.DataLocatedReferable
import org.vclang.typechecking.execution.FullModulePath
import org.vclang.typechecking.execution.TypecheckingEventsProcessor


class TestBasedTypechecking(
    private val eventsProcessor: TypecheckingEventsProcessor,
    state: TypecheckerState,
    concreteProvider: ConcreteProvider,
    errorReporter: ErrorReporter,
    dependencyListener: DependencyListener)
    : TypecheckingOrderingListener(state, concreteProvider, errorReporter, dependencyListener) {

    val typecheckedModules = LinkedHashSet<FullModulePath>()

    private fun startTimer(definition: TCReferable) {
        ((definition as? DataLocatedReferable)?.data as? PsiLocatedReferable)?.let { eventsProcessor.startTimer(it) }
    }

    private fun stopTimer(definition: TCReferable) {
        ((definition as? DataLocatedReferable)?.data as? PsiLocatedReferable)?.let { eventsProcessor.stopTimer(it) }
    }

    override fun typecheckingUnitStarted(definition: TCReferable) {
        startTimer(definition)
    }

    override fun typecheckingUnitFinished(referable: TCReferable, definition: Definition) {
        stopTimer(referable)

        val ref = PsiLocatedReferable.fromReferable(referable) ?: return
        if (definition.status() != Definition.TypeCheckingStatus.NO_ERRORS) {
            definition.setStatus(if (definition.status().headerIsOK()) Definition.TypeCheckingStatus.MAY_BE_TYPE_CHECKED else Definition.TypeCheckingStatus.HEADER_NEEDS_TYPE_CHECKING)
            eventsProcessor.onTestFailure(ref)
        }
        eventsProcessor.onTestFinished(ref)
        runReadAction {
            val file = ref.containingFile as? VcFile ?: return@runReadAction
            typecheckedModules.add(FullModulePath(file.libraryName ?: return@runReadAction, file.modulePath))
        }
    }

    override fun typecheckingHeaderStarted(definition: TCReferable) {
        startTimer(definition)
    }

    override fun typecheckingHeaderFinished(referable: TCReferable, definition: Definition?) {
        stopTimer(referable)
    }

    override fun typecheckingBodyStarted(definition: TCReferable) {
        startTimer(definition)
    }

    override fun typecheckingBodyFinished(referable: TCReferable, definition: Definition) {
        typecheckingUnitFinished(referable, definition)
    }
}