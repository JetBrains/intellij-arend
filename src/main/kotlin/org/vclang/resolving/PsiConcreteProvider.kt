package org.vclang.resolving

import com.jetbrains.jetpad.vclang.error.ErrorReporter
import com.jetbrains.jetpad.vclang.naming.error.ReferenceError
import com.jetbrains.jetpad.vclang.naming.reference.GlobalReferable
import com.jetbrains.jetpad.vclang.naming.reference.converter.ReferableConverter
import com.jetbrains.jetpad.vclang.naming.resolving.visitor.DefinitionResolveNameVisitor
import com.jetbrains.jetpad.vclang.naming.scope.CachingScope
import com.jetbrains.jetpad.vclang.term.concrete.Concrete
import com.jetbrains.jetpad.vclang.typechecking.error.ProxyError
import com.jetbrains.jetpad.vclang.typechecking.typecheckable.provider.ConcreteProvider
import org.vclang.psi.ext.PsiConcreteReferable
import org.vclang.psi.ext.PsiLocatedReferable
import org.vclang.typechecking.execution.TypecheckingEventsProcessor


class PsiConcreteProvider(private val referableConverter: ReferableConverter, private val errorReporter: ErrorReporter, private val eventsProcessor: TypecheckingEventsProcessor?) : ConcreteProvider {
    var isResolving = false

    override fun getConcrete(referable: GlobalReferable): Concrete.ReferableDefinition? {
        if (referable !is PsiConcreteReferable) {
            if (referable !is PsiLocatedReferable) {
                errorReporter.report(ProxyError(referable, ReferenceError("Unknown type of reference", referable)))
            }
            return null
        }

        val typecheckable = referable.typecheckable as? PsiLocatedReferable
        if (typecheckable != null && eventsProcessor != null) {
            eventsProcessor.onTestStarted(typecheckable)
            eventsProcessor.startTimer(typecheckable)
        }
        val def = referable.computeConcrete(referableConverter, errorReporter)
        if (def == null) {
            if (typecheckable != null && eventsProcessor != null) {
                eventsProcessor.stopTimer(typecheckable)
                eventsProcessor.onTestFailure(typecheckable)
                eventsProcessor.onTestFinished(typecheckable)
            }
        } else {
            if (isResolving) {
                def.relatedDefinition.accept(DefinitionResolveNameVisitor(errorReporter), CachingScope.make(referable.scope))
            }
            if (typecheckable != null && eventsProcessor != null) {
                eventsProcessor.stopTimer(typecheckable)
            }
        }
        return def
    }
}