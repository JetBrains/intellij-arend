package org.vclang.resolving

import com.jetbrains.jetpad.vclang.error.ErrorReporter
import com.jetbrains.jetpad.vclang.naming.error.ReferenceError
import com.jetbrains.jetpad.vclang.naming.reference.GlobalReferable
import com.jetbrains.jetpad.vclang.term.concrete.Concrete
import com.jetbrains.jetpad.vclang.typechecking.error.ProxyError
import com.jetbrains.jetpad.vclang.typechecking.typecheckable.provider.ConcreteProvider
import org.vclang.psi.ext.PsiConcreteReferable


class PsiConcreteProvider(private val errorReporter: ErrorReporter) : ConcreteProvider {
    override fun getConcrete(referable: GlobalReferable): Concrete.ReferableDefinition? =
        if (referable is PsiConcreteReferable) {
            referable.computeConcrete(errorReporter)
        } else {
            errorReporter.report(ProxyError(referable, ReferenceError("Unknown type of reference", referable)))
            null
        }
}
