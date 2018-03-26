package org.vclang.psi.ext

import com.jetbrains.jetpad.vclang.error.ErrorReporter
import com.jetbrains.jetpad.vclang.naming.reference.LocatedReferable
import com.jetbrains.jetpad.vclang.term.concrete.Concrete


interface PsiConcreteReferable: PsiReferable, LocatedReferable {
    fun computeConcrete(errorReporter: ErrorReporter): Concrete.ReferableDefinition?
}