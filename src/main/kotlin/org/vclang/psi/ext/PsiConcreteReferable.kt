package org.vclang.psi.ext

import com.jetbrains.jetpad.vclang.error.ErrorReporter
import com.jetbrains.jetpad.vclang.naming.reference.converter.ReferableConverter
import com.jetbrains.jetpad.vclang.term.concrete.Concrete


interface PsiConcreteReferable: PsiLocatedReferable {
    fun computeConcrete(referableConverter: ReferableConverter, errorReporter: ErrorReporter): Concrete.Definition?
}