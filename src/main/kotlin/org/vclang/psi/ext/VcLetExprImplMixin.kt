package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.term.abs.AbstractExpressionVisitor
import org.vclang.psi.VcLetExpr

abstract class VcLetExprImplMixin(node: ASTNode) : VcExprImplMixin(node), VcLetExpr {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P): R =
        visitor.visitLet(this, letClauseList, expr, params)

//    TODO[abstract]
//    override val namespace: Namespace
//        get() = NamespaceProvider.forExpression(this)
}
