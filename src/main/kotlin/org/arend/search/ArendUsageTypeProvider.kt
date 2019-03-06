package org.arend.search

import com.intellij.psi.PsiElement
import com.intellij.usages.UsageTarget
import com.intellij.usages.impl.rules.UsageType
import com.intellij.usages.impl.rules.UsageTypeProviderEx
import org.arend.psi.*

class ArendUsageTypeProvider: UsageTypeProviderEx {
    override fun getUsageType(element: PsiElement?, targets: Array<out UsageTarget>): UsageType? = getUsageType(element)

    override fun getUsageType(element: PsiElement?): UsageType? {
        val parent = element?.parent

        when {
            parent is ArendStatCmd || (parent is ArendNsId && parent.parent is ArendNsUsing) -> return nsUsageInList
            parent is ArendPattern || parent is ArendAtomPatternOrPrefix -> return usagesInPatterns
        }

        if (parent !is ArendLongName) {
            return defaultUsage
        }

        val pParent = parent.parent
        when {
            pParent is ArendStatCmd -> return nsUsage
            pParent is ArendCoClause -> return usagesInCoClauses
            pParent is ArendDefClass -> return extendsUsages
            element.rightSiblings.filterIsInstance<ArendRefIdentifier>().firstOrNull() != null -> return leftUsage
        }

        var expr: ArendExpr = ((pParent as? ArendLongNameExpr)?.parent as? ArendArgumentAppExpr)?.parent as? ArendNewExpr ?: pParent as? ArendLiteral ?: return defaultUsage
        if (((expr as? ArendLiteral)?.parent as? ArendTypeTele)?.parent is ArendDefData) {
            return parameters
        }

        while (true) {
            val atomFieldsAcc = (expr.parent as? ArendAtom)?.parent as? ArendAtomFieldsAcc
            val atomParent = atomFieldsAcc?.parent
            if (atomParent is ArendReturnExpr) {
                val list = atomParent.atomFieldsAccList
                if (list.size > 1 && list[1] == atomFieldsAcc) {
                    return levelProof
                }
                expr = atomFieldsAcc
                break
            }

            val newExpr = (atomFieldsAcc?.parent as? ArendArgumentAppExpr)?.parent as? ArendNewExpr ?: expr as? ArendNewExpr ?: return defaultUsage
            if (newExpr.newKw != null) {
                return newInstances
            }

            val tupleExpr = newExpr.parent as? ArendTupleExpr
            if (tupleExpr != null && tupleExpr.exprList.firstOrNull() == newExpr) {
                val tuple = tupleExpr.parent as? ArendTuple ?: return defaultUsage
                if (tuple.tupleExprList.size != 1) {
                    return defaultUsage
                }
                expr = tuple
            } else {
                expr = newExpr
                break
            }
        }

        val exprParent = expr.parent
        if ((exprParent is ArendNameTele || exprParent is ArendReturnExpr) && exprParent.parent?.let { it is ArendDefFunction || it is ArendDefInstance } == true ||
                ((exprParent as? ArendTypedExpr)?.parent as? ArendTypeTele)?.parent is ArendDefData) {
            if (exprParent !is ArendReturnExpr) {
                return parameters
            }

            val exprPP = exprParent.parent
            return if (exprPP is ArendDefInstance || (exprPP as? ArendDefFunction)?.functionBody?.cowithKw != null) newInstances else resultTypes
        }

        return defaultUsage
    }

    companion object {
        val nsUsage           = UsageType("Namespace commands")
        val nsUsageInList     = UsageType("Hiding or using lists of namespace commands")
        val leftUsage         = UsageType("Left sides of dot expressions")
        val usagesInCoClauses = UsageType("Coclauses")
        val usagesInPatterns  = UsageType("Patterns")
        val newInstances      = UsageType("New instances")
        val levelProof        = UsageType("Level proof")
        val parameters        = UsageType("Parameter types")
        val resultTypes       = UsageType("Result types")
        val extendsUsages     = UsageType("Extends clauses")
        val defaultUsage      = UsageType("Unclassified usages")
    }
}