package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.term.abs.AbstractExpressionVisitor


open class ArendAppExpr(node: ASTNode) : ArendExpr(node) {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
        visitor.visitInferHole(this, params)
}
