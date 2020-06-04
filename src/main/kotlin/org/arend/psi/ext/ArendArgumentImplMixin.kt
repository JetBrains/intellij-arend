package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.util.elementType
import org.arend.psi.*
import org.arend.term.abs.Abstract
import org.arend.term.abs.AbstractExpressionVisitor


abstract class ArendImplicitArgumentImplMixin(node: ASTNode) : ArendSourceNodeImpl(node), ArendImplicitArgument, Abstract.Expression {
    override fun isExplicit() = false

    override fun isVariable() = false

    override fun getExpression() = this

    override fun getData() = this

    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
        acceptTupleExpression(this, tupleExprList, visitor, params)
}

internal fun <P : Any?, R : Any?> acceptTupleExpression(data: Any?, exprList: List<ArendTupleExpr>, visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
    val element = exprList.lastOrNull()?.findNextSibling()
    val isComma = element?.elementType == ArendElementTypes.COMMA
    return if (exprList.size == 1 && !isComma) {
        exprList[0].accept(visitor, params)
    } else {
        visitor.visitTuple(data, exprList, if (isComma) element else null, params)
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