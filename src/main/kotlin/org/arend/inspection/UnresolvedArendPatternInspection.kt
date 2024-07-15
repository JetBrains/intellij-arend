package org.arend.inspection

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.arend.psi.ext.*
import org.arend.util.ArendBundle

class UnresolvedArendPatternInspection : ArendInspectionBase() {
    override fun buildArendVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                super.visitElement(element)
                if (element is ArendPattern && element.parent !is ArendPattern) {
                    val parent = element.parent
                    if ((parent is ArendClause && parent.patterns.size > 1) || parent.parent is ArendFunctionClauses ||
                        (parent.parent is ArendDataBody && (parent.parent as ArendDataBody).elim != null)) {
                        return
                    }
                    val resolve = element.singleReferable?.reference?.resolve() ?: return
                    if (resolve !is ArendConstructor && resolve !is ArendDefInstance) {
                        val message = ArendBundle.message("arend.inspection.unresolved.pattern", element.text)
                        holder.registerProblem(element, message)
                    }
                }
            }
        }
    }
}
