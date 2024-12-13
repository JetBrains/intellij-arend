package org.arend.search

import com.intellij.psi.PsiElement
import com.intellij.usages.UsageTarget
import com.intellij.usages.impl.rules.UsageType
import com.intellij.usages.impl.rules.UsageTypeProviderEx
import org.arend.psi.ext.*
import org.arend.psi.rightSibling
import org.arend.term.concrete.Concrete
import org.arend.resolving.util.parseBinOp

class ArendUsageTypeProvider: UsageTypeProviderEx {
    override fun getUsageType(element: PsiElement, targets: Array<out UsageTarget>): UsageType = getUsageType(element)

    override fun getUsageType(element: PsiElement): UsageType {
        val parent = element as? ArendIPName ?: element.parent

        when {
            parent is ArendStatCmd || (parent is ArendNsId && parent.parent is ArendNsUsing) -> return nsUsageInList
            parent is ArendPattern -> return usagesInPatterns
        }

        if (!(parent is ArendLongName || parent is ArendIPName)) {
            return defaultUsage
        }

        val pParent = parent.parent
        when {
            pParent is ArendStatCmd -> return nsUsage
            pParent is CoClauseBase -> return usagesInCoClauses
            pParent is ArendSuperClass -> return extendsUsages
            element.rightSibling<ArendRefIdentifier>() != null || parent is ArendLongName && (pParent as? ArendLiteral)?.ipName != null -> return leftUsage
        }

        var expr: ArendExpr = ((pParent as? ArendLongNameExpr)?.parent as? ArendArgumentAppExpr)?.parent as? ArendNewExpr ?: pParent as? ArendLiteral ?: return defaultUsage
        if (isParameter((expr as? ArendLiteral)?.parent as? ArendTypeTele)) {
            return parameters
        }

        while (true) {
            val atomFieldsAcc = (expr.parent as? ArendAtom)?.parent as? ArendAtomFieldsAcc
            if (atomFieldsAcc?.fieldAccList?.isNotEmpty() == true) {
                return leftUsage
            }

            val atomParent = atomFieldsAcc?.parent
            val ppParent = (atomParent as? ArendArgumentAppExpr ?: (atomParent as? ArendAtomArgument)?.parent as? ArendArgumentAppExpr)?.parent
            val newExpr = ppParent as? ArendNewExpr ?: expr as? ArendNewExpr
            val appExpr = newExpr?.argumentAppExpr ?: (ppParent as? ArendNewExpr)?.argumentAppExpr ?: return defaultUsage

            val argList = appExpr.argumentList
            val cExpr =
                if (argList.isNotEmpty()) {
                    (appExpr.atomFieldsAcc ?: appExpr.longNameExpr)?.let { func -> parseBinOp(func, argList) }
                } else {
                    null
                }
            val topRef = ((cExpr as? Concrete.AppExpression)?.function as? Concrete.ReferenceExpression)?.data
            if (topRef != null && topRef != parent) {
                return defaultUsage
            }
            if (newExpr == null) {
                return defaultUsage
            }

            if (newExpr.appPrefix?.isNew == true) {
                return newInstances
            }
            if (newExpr.localCoClauseList.isNotEmpty()) {
                return classExt
            }

            val tupleExpr = newExpr.parent as? ArendTupleExpr
            if (tupleExpr != null && tupleExpr.expr == newExpr) {
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
                isParameter(((exprParent as? ArendTypedExpr)?.parent as? ArendTypeTele))) {
            if (exprParent !is ArendReturnExpr) {
                return parameters
            }

            val exprPP = exprParent.parent
            return if (exprPP is ArendDefInstance || (exprPP as? ArendDefFunction)?.body?.cowithKw != null) newInstances else resultTypes
        }

        return defaultUsage
    }

    private fun isParameter(typeTele: ArendTypeTele?): Boolean {
        if (typeTele == null) {
            return false
        }

        val parent = typeTele.parent
        return !(parent is ArendPiExpr || parent is ArendSigmaExpr)
    }

    companion object {
        val nsUsage           = UsageType { "Namespace commands" }
        val nsUsageInList     = UsageType { "Hiding or using lists of namespace commands" }
        val leftUsage         = UsageType { "Left sides of dot expressions" }
        val usagesInCoClauses = UsageType { "Coclauses" }
        val usagesInPatterns  = UsageType { "Patterns" }
        val newInstances      = UsageType { "New instances" }
        val classExt          = UsageType { "Class extensions" }
        val parameters        = UsageType { "Parameter types" }
        val resultTypes       = UsageType { "Result types" }
        val extendsUsages     = UsageType { "Extends clauses" }
        val defaultUsage      = UsageType { "Unclassified usages" }
    }
}