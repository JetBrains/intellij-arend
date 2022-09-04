package org.arend.psi.ext

import com.intellij.psi.PsiElement
import org.arend.psi.findPrevSibling

interface ArendTopLevelLevelExpr : PsiElement {
    fun isPLevels(): Boolean {
        var expr: PsiElement? = this
        var parent = parent
        while (parent is ArendOnlyLevelExpr) {
            expr = parent.parent
            parent = expr?.parent
        }
        return expr?.findPrevSibling()?.javaClass != javaClass
    }
}