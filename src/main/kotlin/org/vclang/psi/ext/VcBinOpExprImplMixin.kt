package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.term.abs.AbstractExpressionVisitor
import org.vclang.psi.VcBinOpExpr


abstract class VcBinOpExprImplMixin(node: ASTNode) : VcExprImplMixin(node), VcBinOpExpr {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P): R {
        val binOpSeq = binOpRightList
        if (binOpSeq.isEmpty()) {
            return newExpr.accept(visitor, params)
        }
        return visitor.visitBinOpSequence(this, newExpr, binOpSeq, params)
    }
}