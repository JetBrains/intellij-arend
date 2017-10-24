package org.vclang.resolving

import com.jetbrains.jetpad.vclang.error.ErrorReporter
import com.jetbrains.jetpad.vclang.naming.NameResolver
import com.jetbrains.jetpad.vclang.naming.error.ReferenceError
import com.jetbrains.jetpad.vclang.naming.reference.GlobalReferable
import com.jetbrains.jetpad.vclang.naming.resolving.visitor.DefinitionResolveNameVisitor
import com.jetbrains.jetpad.vclang.term.concrete.Concrete
import com.jetbrains.jetpad.vclang.typechecking.error.ProxyError
import com.jetbrains.jetpad.vclang.typechecking.typecheckable.provider.ConcreteProvider
import org.vclang.psi.ext.PsiConcreteReferable


class PsiConcreteProvider(errorReporter: ErrorReporter) : ConcreteProvider {
    private val visitor = DefinitionResolveNameVisitor(errorReporter)

    override fun getConcrete(referable: GlobalReferable): Concrete.ReferableDefinition? {
        if (referable !is PsiConcreteReferable) {
            visitor.errorReporter.report(ProxyError(referable, ReferenceError("Unknown type of reference", referable)))
            return null
        }
        val def = referable.computeConcrete(visitor.errorReporter)
        def?.relatedDefinition?.accept(visitor, referable.scope)
        return def
    }
}