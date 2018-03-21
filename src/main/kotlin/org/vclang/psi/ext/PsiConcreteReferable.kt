package org.vclang.psi.ext

import com.jetbrains.jetpad.vclang.error.ErrorReporter
import com.jetbrains.jetpad.vclang.naming.reference.GlobalReferable
import com.jetbrains.jetpad.vclang.term.concrete.Concrete


interface PsiConcreteReferable: PsiReferable, GlobalReferable {
    fun computeConcrete(errorReporter: ErrorReporter): Concrete.ReferableDefinition?
}