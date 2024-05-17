package org.arend.refactoring.changeSignature.entries

import org.arend.psi.ext.ArendArgumentAppExpr
import org.arend.refactoring.changeSignature.*
import org.arend.refactoring.changeSignature.entries.AbstractExpressionEntry.Companion.PrintableArgument
import org.arend.term.concrete.Concrete

open class AppExpressionEntry(
    refactoringContext: ChangeSignatureRefactoringContext,
    psiAppExpr: ArendArgumentAppExpr,
    descriptor: ChangeSignatureRefactoringDescriptor?,
    concreteAppExpr: Concrete.AppExpression
):
    AbstractExpressionEntry(
        refactoringContext,
        psiAppExpr,
        descriptor,
        concreteAppExpr,
        getBlocksInRange(psiAppExpr, refactoringContext.rangeData[concreteAppExpr.function]!!).let{ if (it.size != 1) throw IllegalArgumentException() else it.first() },
        (concreteAppExpr.function as? Concrete.ReferenceExpression)?.referent,
        concreteAppExpr.arguments.map { ExprPrintableArgument(it, psiAppExpr, refactoringContext) }) {
    
    companion object {
        class ExprPrintableArgument(val argument: Concrete.Argument,
                                    private val psiAppExpr: ArendArgumentAppExpr,
                                    private val refactoringContext: ChangeSignatureRefactoringContext): PrintableArgument {
            override fun print(): IntermediatePrintResult = printAppExpr(argument.expression, psiAppExpr, refactoringContext)

            override fun isExplicit(): Boolean = argument.isExplicit

            override fun getExpression(): Concrete.SourceNode = argument.expression
        }
    }
}