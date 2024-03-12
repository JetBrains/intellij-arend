package org.arend.refactoring.changeSignature

import com.intellij.psi.PsiElement
import com.intellij.refactoring.suggested.startOffset
import com.intellij.usageView.UsageInfo
import org.arend.psi.ext.*

class ArendUsageInfo(val psi: PsiElement, val task: ChangeSignatureRefactoringDescriptor): UsageInfo(psi), Comparable<ArendUsageInfo> {
    val psiParentRoot: PsiElement?
    init {
        var e : PsiElement? = psi
        val isAdmissibleUsage = when (psi) {
            is ArendRefIdentifier -> (psi.parent as? ArendLongName)?.let { longName -> longName.refIdentifierList.lastOrNull() == psi } ?: true
            else -> true
        }

        psiParentRoot = if (isAdmissibleUsage) {
            while (e != null && e !is ArendPattern && e !is ArendArgumentAppExpr && e !is CoClauseBase) e = e.parent
            when {
                (e is ArendPattern && e.parent is ArendPattern) -> e.parent
                e != null -> e
                else -> null
            }
        } else null
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
}