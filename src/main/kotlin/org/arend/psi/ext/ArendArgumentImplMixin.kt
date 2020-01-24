package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.psi.ArendAtomArgument
import org.arend.psi.ArendImplicitArgument
import org.arend.term.abs.Abstract
import org.arend.term.abs.AbstractExpressionVisitor


abstract class ArendImplicitArgumentImplMixin(node: ASTNode) : ArendSourceNodeImpl(node), ArendImplicitArgument, Abstract.Expression {
    override fun isExplicit() = false

    override fun isVariable() = false

    override fun getExpression() = this

    override fun getData() = this

    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
        val list = tupleExprList
        return if (list.size == 1) list[0].accept(visitor, params) else visitor.visitTuple(this, list, params)
    }
}

abstract class ArendAtomArgumentImplMixin(node: ASTNode) : ArendSourceNodeImpl(node), ArendAtomArgument {
    override fun isExplicit() = true

    override fun isVariable(): Boolean {
        val atomFieldsAcc = atomFieldsAcc
        if (atomFieldsAcc.fieldAccList.isNotEmpty()) {
            return false
        }

        val literal = atomFieldsAcc.atom.literal ?: return false
        return literal.longName != null || literal.ipName != null
    }

    override fun getExpression() = atomFieldsAcc
}