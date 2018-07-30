package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.term.abs.AbstractExpressionVisitor
import org.vclang.psi.VcAppExpr


abstract class VcAppExprImplMixin(node: ASTNode) : VcExprImplMixin(node), VcAppExpr {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
        visitor.visitInferHole(this, if (visitor.visitErrors()) org.vclang.psi.ext.getErrorData(this) else null, params)
}
