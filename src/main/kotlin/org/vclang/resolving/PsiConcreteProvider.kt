package org.vclang.resolving

import com.jetbrains.jetpad.vclang.error.ErrorReporter
import com.jetbrains.jetpad.vclang.naming.error.ReferenceError
import com.jetbrains.jetpad.vclang.naming.reference.GlobalReferable
import com.jetbrains.jetpad.vclang.naming.resolving.visitor.DefinitionResolveNameVisitor
import com.jetbrains.jetpad.vclang.naming.scope.CachingScope
import com.jetbrains.jetpad.vclang.term.concrete.Concrete
import com.jetbrains.jetpad.vclang.typechecking.error.ProxyError
import com.jetbrains.jetpad.vclang.typechecking.typecheckable.provider.ConcreteProvider
import org.vclang.psi.ext.PsiConcreteReferable
import org.vclang.psi.ext.PsiGlobalReferable
import org.vclang.typechecking.execution.TypecheckingEventsProcessor


class PsiConcreteProvider(private val errorReporter: ErrorReporter, private val eventsProcessor: TypecheckingEventsProcessor) : ConcreteProvider {
    var isResolving = false

    override fun getConcrete(referable: GlobalReferable): Concrete.ReferableDefinition? {
        if (referable !is PsiConcreteReferable) {
            if (referable !is PsiGlobalReferable) {
                errorReporter.report(ProxyError(referable, ReferenceError("Unknown type of reference", referable)))
            }
            return null
        }

        eventsProcessor.onTestStarted(referable)
        val def = referable.computeConcrete(errorReporter)
        if (def == null) {
            eventsProcessor.onTestFailure(referable)
            eventsProcessor.onTestFinished(referable)
        } else if (isResolving) {
            def.relatedDefinition.accept(DefinitionResolveNameVisitor(errorReporter), CachingScope.make(referable.scope))
        }
        return def
    }
}