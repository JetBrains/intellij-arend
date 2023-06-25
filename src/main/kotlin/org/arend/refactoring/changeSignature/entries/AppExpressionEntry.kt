package org.arend.refactoring.changeSignature.entries

import com.intellij.psi.PsiElement
import org.arend.psi.ext.ArendArgumentAppExpr
import org.arend.refactoring.changeSignature.*
import org.arend.term.concrete.Concrete

open class AppExpressionEntry(private val concreteAppExpr: Concrete.AppExpression,
                              private val psiAppExpr: ArendArgumentAppExpr,
                              refactoringContext: ChangeSignatureRefactoringContext,
                              descriptor: ChangeSignatureRefactoringDescriptor?):
    BinOpEntry<Concrete.Expression, Concrete.Argument>(concreteAppExpr, psiAppExpr, refactoringContext, descriptor, (concreteAppExpr.function as? Concrete.ReferenceExpression)?.referent) {
    override fun printParameter(parameter: Concrete.Argument): IntermediatePrintResult =
        printAppExpr(parameter.expression, psiAppExpr, refactoringContext)
    override fun isExplicit(parameter: Concrete.Argument): Boolean = parameter.isExplicit
    override fun getConcreteParameters(): List<Concrete.Argument> = concreteAppExpr.arguments
    override fun getOperatorData(): PsiElement = getBlocksInRange(refactoringContext.rangeData[concreteAppExpr.function]!!).let{ if (it.size != 1) throw IllegalArgumentException() else it.first() }
    override fun getExpressionByParameter(parameter: Concrete.Argument): Concrete.Expression = parameter.expression
    override fun getParameters(): Pair<List<Parameter>, List<NewParameter>> =
        Pair(descriptor!!.oldParameters, descriptor.newParameters.let { if (isDotExpression) it.drop(1) else it })
    fun procFunction() = refactoringContext.textGetter(concreteAppExpr.function.data as PsiElement)
    override fun getContextName(): String = if (isDotExpression) procFunction() else super.getContextName() //TODO: Fixme (we should remove backticks in certain situations)
}