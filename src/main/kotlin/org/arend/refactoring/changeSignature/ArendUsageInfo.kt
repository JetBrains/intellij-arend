package org.arend.refactoring.changeSignature

import com.intellij.psi.PsiElement
import com.intellij.psi.util.startOffset
import com.intellij.usageView.UsageInfo
import org.arend.psi.ancestor
import org.arend.psi.childOfType
import org.arend.psi.descendantOfType
import org.arend.psi.ext.*

class ArendUsageInfo(val psi: PsiElement, val task: ChangeSignatureRefactoringDescriptor) : UsageInfo(psi),
    Comparable<ArendUsageInfo> {
    val psiParentRoot: PsiElement?

    init {
        var e: PsiElement? = psi
        while (e != null && !(e is ArendPattern ||
                    e is ArendArgumentAppExpr && !(isParenthesizedLongName(e)) ||
                    e is CoClauseBase ||
                    e is ArendTypeTele ||
                    e is ArendAtomFieldsAcc && e.numberList.isNotEmpty())) {
            e = e.parent
        }

        psiParentRoot = when {
            (e is ArendPattern && e.parent is ArendPattern) -> e.parent
            e != null -> e
            else -> null
        }
    }

    override fun compareTo(other: ArendUsageInfo): Int {
        if (psi == other.psi) return 0
        val otherPsiParentRoot = other.psiParentRoot
        if (psiParentRoot == null && otherPsiParentRoot != null) return -1
        if (psiParentRoot != null && otherPsiParentRoot == null) return 1
        if (otherPsiParentRoot == null || psiParentRoot == null) return this.hashCode().compareTo(other.hashCode())
        val result = psiParentRoot.textLength.compareTo(otherPsiParentRoot.textLength) // safe
        if (result != 0) return result
        return psiParentRoot.startOffset.compareTo(otherPsiParentRoot.startOffset)
    }

    companion object {
        fun isParenthesizedLongName(psi: PsiElement) : Boolean {
            if (psi is ArendArgumentAppExpr) {
                val parentTuple = psi.ancestor<ArendTupleExpr>()
                if (parentTuple == null || parentTuple.textRange != psi.textRange || parentTuple.parent !is ArendTuple || (parentTuple.parent as ArendTuple).tupleExprList.size > 1)
                    return false

                val childTuple = psi.descendantOfType<ArendTuple>()
                if (childTuple != null && childTuple.textRange == psi.textRange && childTuple.lparen != null && childTuple.tupleExprList.size == 1) {
                    val childAppExpr = childTuple.childOfType<ArendArgumentAppExpr>()
                    if (childAppExpr != null && childAppExpr.textRange == childTuple.textRange) return isParenthesizedLongName(childAppExpr)
                }

                val atom = psi.descendantOfType<ArendAtom>()
                val result = atom != null && atom.textRange == psi.textRange
                return result
            }

            return false
        }
    }
}