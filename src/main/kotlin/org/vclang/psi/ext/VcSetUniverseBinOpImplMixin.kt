package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.term.abs.AbstractExpressionVisitor
import org.vclang.psi.VcSetUniverseBinOp


abstract class VcSetUniverseBinOpImplMixin(node: ASTNode) : VcExprImplMixin(node), VcSetUniverseBinOp {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P): R =
        visitor.visitUniverse(this, set.text.substring("\\Set".length).toIntOrNull(), 0, atomLevelExpr, null, params)
}