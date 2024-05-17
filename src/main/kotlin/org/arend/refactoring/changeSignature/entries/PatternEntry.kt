package org.arend.refactoring.changeSignature.entries

import org.arend.psi.ext.ArendPattern
import org.arend.refactoring.changeSignature.*
import org.arend.refactoring.changeSignature.entries.AbstractExpressionEntry.Companion.PrintableArgument
import org.arend.term.concrete.Concrete

class PatternEntry(
    refactoringContext: ChangeSignatureRefactoringContext,
    psiPattern: ArendPattern,
    descriptor: ChangeSignatureRefactoringDescriptor?,
    concreteConstructorPattern: Concrete.ConstructorPattern
):
    AbstractExpressionEntry(refactoringContext,
        psiPattern,
        descriptor,
        concreteConstructorPattern,
        concreteConstructorPattern.constructorData as ArendPattern,
        concreteConstructorPattern.constructor,
        concreteConstructorPattern.patterns.map { PatternPrintableArgument(it, psiPattern, refactoringContext) }) {
    
    companion object {
        class PatternPrintableArgument(val argument: Concrete.Pattern,
                                       private val psiPattern: ArendPattern,
                                       private val refactoringContext: ChangeSignatureRefactoringContext): PrintableArgument {
            override fun print(): IntermediatePrintResult = printPattern(argument, psiPattern, refactoringContext)

            override fun isExplicit(): Boolean = argument.isExplicit

            override fun getExpression(): Concrete.SourceNode = argument
        }
    }
}