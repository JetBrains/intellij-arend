package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.term.abs.AbstractExpressionVisitor
import org.vclang.psi.VcTuple

abstract class VcTupleImplMixin(node: ASTNode) : VcExprImplMixin(node), VcTuple {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
        return visitor.visitTuple(this, exprList, params)
    }
}