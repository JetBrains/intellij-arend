package org.arend.refactoring.changeSignature.entries

import org.arend.psi.ext.ArendCompositeElement
import org.arend.refactoring.changeSignature.*
import org.arend.term.concrete.Concrete

class NoArgumentsEntry(refExpr: Concrete.ReferenceExpression, refactoringContext: ChangeSignatureRefactoringContext, private val descriptor1: ChangeSignatureRefactoringDescriptor): UsageEntry(refactoringContext, refExpr.data as ArendCompositeElement, descriptor1, refExpr.referent) {
    override fun getArguments(): List<ArgumentPrintResult> = emptyList()

    override fun getParameters(): Pair<List<Parameter>, List<NewParameter>> = Pair(descriptor1.oldParameters, descriptor1.newParameters)
}