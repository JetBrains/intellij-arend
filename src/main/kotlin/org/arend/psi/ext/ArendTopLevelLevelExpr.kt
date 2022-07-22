package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.arend.psi.ArendOnlyLevelExpr

open class ArendTopLevelLevelExpr(node: ASTNode) : ArendSourceNodeImpl(node) {
    fun isPLevels(): Boolean {
        var expr: PsiElement? = this
        var parent = parent
        while (parent is ArendOnlyLevelExpr) {
            expr = parent.parent
            parent = expr?.parent
        }
        while (expr != null) {
            expr = expr.prevSibling
            if (expr?.javaClass == javaClass) {
                return false
            }
        }
        return true
    }
}