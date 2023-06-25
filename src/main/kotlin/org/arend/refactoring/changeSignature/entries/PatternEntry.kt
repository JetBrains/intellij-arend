package org.arend.refactoring.changeSignature.entries

import com.intellij.psi.PsiElement
import org.arend.psi.ext.ArendPattern
import org.arend.refactoring.changeSignature.*
import org.arend.term.concrete.Concrete

class PatternEntry(private val concreteConstructorPattern: Concrete.ConstructorPattern,
                   private val psiPattern: ArendPattern,
                   refactoringContext: ChangeSignatureRefactoringContext,
                   descriptor: ChangeSignatureRefactoringDescriptor?):
    BinOpEntry<Concrete.Pattern, Concrete.Pattern>(concreteConstructorPattern, psiPattern, refactoringContext, descriptor, concreteConstructorPattern.constructor) {
    override fun printParameter(parameter: Concrete.Pattern): IntermediatePrintResult =
        printPattern(parameter, psiPattern, refactoringContext)
    override fun isExplicit(parameter: Concrete.Pattern): Boolean = parameter.isExplicit
    override fun getConcreteParameters(): List<Concrete.Pattern> = concreteConstructorPattern.patterns
    override fun getExpressionByParameter(parameter: Concrete.Pattern): Concrete.Pattern = parameter
    override fun getOperatorData(): PsiElement = concreteConstructorPattern.constructorData as ArendPattern
    override fun getParameters(): Pair<List<Parameter>, List<NewParameter>> =
        Pair(descriptor!!.oldParametersWithoutImplicitPrefix ?: descriptor.oldParameters,
            (descriptor.newParametersWithoutImplicitPrefix ?: descriptor.newParameters).let { if (isDotExpression) it.drop(1) else it })
}