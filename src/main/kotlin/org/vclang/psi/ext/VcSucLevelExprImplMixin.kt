package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.term.abs.AbstractLevelExpressionVisitor
import org.vclang.psi.VcSucLevelExpr


abstract class VcSucLevelExprImplMixin(node: ASTNode) : VcLevelExprImplMixin(node), VcSucLevelExpr {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractLevelExpressionVisitor<in P, out R>, params: P): R =
        visitor.visitSuc(this, atomLevelExpr, params)
}