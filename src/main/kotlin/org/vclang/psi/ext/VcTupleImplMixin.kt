package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.term.abs.AbstractExpressionVisitor
import org.vclang.psi.VcTuple

abstract class VcTupleImplMixin(node: ASTNode) : VcExprImplMixin(node), VcTuple {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
        val exprList = tupleExprList
        return if (exprList.size == 1) {
            exprList[0].accept(visitor, params)
        } else {
            visitor.visitTuple(this, tupleExprList, if (visitor.visitErrors()) org.vclang.psi.ext.getErrorData(this) else null, params)
        }
    }
}