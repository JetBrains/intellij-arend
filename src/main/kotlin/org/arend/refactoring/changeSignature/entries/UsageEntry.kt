package org.arend.refactoring.changeSignature.entries

import org.arend.ext.module.LongName
import org.arend.naming.reference.Referable
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.quickfix.referenceResolve.ResolveReferenceAction
import org.arend.refactoring.changeSignature.*
import java.util.*
import kotlin.collections.ArrayList

abstract class UsageEntry(val refactoringContext: ChangeSignatureRefactoringContext,
                          val contextPsi: ArendCompositeElement,
                          val descriptor: ChangeSignatureRefactoringDescriptor?,
                          val target: Referable?) {
    abstract fun getArguments(): List<ArgumentPrintResult>

    private fun getTrailingParameters(): List<Parameter> {
        val result = ArrayList<Parameter>()
        val newParameters = getParameters().second
        for (newParam in newParameters.reversed()) if (newParam.isExplicit != newParam.oldParameter?.isExplicit) break else {
            result.add(newParam.oldParameter)
        }
        return result
    }

    open fun getLambdaParams(parameterMap: Set<Parameter>, includingSuperfluousTrailingParams: Boolean): List<Parameter> {
        val lambdaParameters = ArrayList<Parameter>(getParameters().first)
        lambdaParameters.removeAll(parameterMap)
        if (!includingSuperfluousTrailingParams) lambdaParameters.removeAll(getTrailingParameters().toSet())
        return lambdaParameters
    }
    abstract fun getParameters(): Pair<List<Parameter>, List<NewParameter>>
    open fun getContextName(): String {
        val affectedDefinition = descriptor?.affectedDefinition
        val renameNeeded = target == affectedDefinition && affectedDefinition != null
        val longNameString =
            ResolveReferenceAction.getTargetName(descriptor?.affectedDefinition as PsiLocatedReferable, contextPsi)
        val longName = LongName.fromString(longNameString).toList()
        if (affectedDefinition != null && renameNeeded && descriptor.newName != null && longName.last() == affectedDefinition.name) {
            return LongName(longName.take(longName.size - 1) + Collections.singletonList(descriptor.newName)).toString()
        }
        return longNameString!!
    }

    open fun getUnmodifiablePrefix(): String? = null
    open fun getUnmodifiableSuffix(): String? = null
}