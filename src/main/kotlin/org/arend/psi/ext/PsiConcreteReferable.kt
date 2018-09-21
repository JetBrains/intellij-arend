package org.arend.psi.ext

import org.arend.error.ErrorReporter
import org.arend.naming.reference.converter.ReferableConverter
import org.arend.term.concrete.Concrete


interface PsiConcreteReferable: PsiLocatedReferable {
    fun computeConcrete(referableConverter: ReferableConverter, errorReporter: ErrorReporter): Concrete.Definition?
}