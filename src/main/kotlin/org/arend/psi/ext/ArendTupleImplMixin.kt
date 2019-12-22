package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.term.abs.AbstractExpressionVisitor
import org.arend.psi.ArendTuple

abstract class ArendTupleImplMixin(node: ASTNode) : ArendExprImplMixin(node), ArendTuple {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
        val exprList = tupleExprList
        return if (exprList.size == 1) {
            exprList[0].accept(visitor, params)
        } else {
            visitor.visitTuple(this, tupleExprList, params)
        }
    }
}