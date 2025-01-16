package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.util.elementType
import org.arend.psi.*
import org.arend.term.abs.Abstract
import org.arend.term.abs.AbstractExpressionVisitor
import org.arend.typechecking.error.FixedPsiSourceInfo


interface ArendArgument : ArendCompositeElement, Abstract.Argument, Abstract.BinOpSequenceElem

class ArendImplicitArgument(node: ASTNode) : ArendSourceNodeImpl(node), ArendArgument, Abstract.Expression {
    val tupleExprList: List<ArendTupleExpr>
        get() = getChildrenOfType()

    override fun isExplicit() = false

    override fun isVariable() = false

    override fun getExpression() = this

    override fun getData() = this

    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
        acceptTupleExpression(this, tupleExprList, visitor, params)
}

class ArendLamArgument(node: ASTNode) : ArendSourceNodeImpl(node), ArendArgument {
    override fun isVariable() = false

    override fun isExplicit() = true

    override fun getExpression(): ArendExpr = childOfTypeStrict()
}

class ArendCaseArgument(node: ASTNode) : ArendSourceNodeImpl(node), ArendArgument {
    override fun isVariable() = false

    override fun isExplicit() = true

    override fun getExpression(): ArendExpr = childOfTypeStrict()
}

class ArendLetArgument(node: ASTNode) : ArendSourceNodeImpl(node), ArendArgument {
    override fun isVariable() = false

    override fun isExplicit() = true

    override fun getExpression(): ArendExpr = childOfTypeStrict()
}

internal fun <P : Any?, R : Any?> acceptTupleExpression(data: Any?, exprList: List<ArendTupleExpr>, visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
    val element = exprList.lastOrNull()?.findNextSibling()
    val isComma = element?.elementType == ArendElementTypes.COMMA
    return if (exprList.size == 1 && !isComma) {
        exprList[0].accept(visitor, params)
    } else {
        visitor.visitTuple(data, exprList, if (isComma) FixedPsiSourceInfo(element!!) else null, params)
    }
}

class ArendAtomArgument(node: ASTNode) : ArendSourceNodeImpl(node), ArendArgument {
    val atomFieldsAcc: ArendAtomFieldsAcc
        get() = childOfTypeStrict()

    override fun isExplicit() = true

    override fun isVariable(): Boolean {
        val atomFieldsAcc = atomFieldsAcc
        for (fieldAcc in atomFieldsAcc.fieldAccList) {
            if (fieldAcc.refIdentifier == null) {
                return false
            }
        }

        val literal = atomFieldsAcc.atom.literal ?: return false
        return literal.refIdentifier != null || literal.ipName != null
    }

    override fun getExpression() = atomFieldsAcc
}