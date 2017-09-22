package org.vclang.psi.ext

import com.jetbrains.jetpad.vclang.error.ErrorReporter
import com.jetbrains.jetpad.vclang.term.concrete.Concrete


interface PsiConcreteReferable: PsiGlobalReferable {
    fun computeConcrete(errorReporter: ErrorReporter): Concrete.ReferableDefinition?
}