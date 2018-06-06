package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.term.abs.AbstractExpressionVisitor
import org.vclang.psi.VcCaseExpr


abstract class VcCaseExprImplMixin(node: ASTNode) : VcExprImplMixin(node), VcCaseExpr {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
        visitor.visitCase(this, exprList, clauseList, if (visitor.visitErrors()) org.vclang.psi.ext.getErrorData(this) else null, params)
}