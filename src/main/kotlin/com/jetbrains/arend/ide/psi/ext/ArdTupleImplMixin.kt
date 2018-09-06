package com.jetbrains.arend.ide.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.arend.ide.psi.ArdTuple
import com.jetbrains.jetpad.vclang.term.abs.AbstractExpressionVisitor

abstract class ArdTupleImplMixin(node: ASTNode) : ArdExprImplMixin(node), ArdTuple {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
        val exprList = tupleExprList
        return if (exprList.size == 1) {
            exprList[0].accept(visitor, params)
        } else {
            visitor.visitTuple(this, tupleExprList, if (visitor.visitErrors()) com.jetbrains.arend.ide.psi.ext.getErrorData(this) else null, params)
        }
    }
}