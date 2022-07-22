package org.arend.psi.ext

import org.arend.ext.error.ErrorReporter
import org.arend.naming.reference.converter.ReferableConverter
import org.arend.term.concrete.Concrete


interface PsiConcreteReferable: PsiDefReferable {
    fun computeConcrete(referableConverter: ReferableConverter, errorReporter: ErrorReporter): Concrete.ResolvableDefinition
}