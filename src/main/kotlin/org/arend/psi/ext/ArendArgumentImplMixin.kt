package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.psi.*


abstract class ArendImplicitArgumentImplMixin(node: ASTNode) : ArendSourceNodeImpl(node), ArendImplicitArgument {
    override fun isExplicit() = false

    override fun isVariable() = false

    override fun getExpression() = expr
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