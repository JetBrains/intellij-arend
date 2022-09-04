package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.psi.firstRelevantChild
import org.arend.term.abs.AbstractExpressionVisitor


class ArendUniverseExpr(node: ASTNode) : ArendExpr(node) {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
        val child = firstRelevantChild ?: error ("Incorrect expression: universeExpr")
        return if (child is ArendAppExpr) child.accept(visitor, params) else visitor.visitUniverse(this, 0, -1, null, null, params)
    }
}