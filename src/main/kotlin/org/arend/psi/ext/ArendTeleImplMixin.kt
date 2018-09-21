package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.naming.reference.Referable
import org.arend.term.abs.Abstract
import org.arend.psi.ArendFieldTele
import org.arend.psi.ArendNameTele
import org.arend.psi.ArendTypeTele


abstract class ArendNameTeleImplMixin(node: ASTNode): ArendSourceNodeImpl(node), ArendNameTele {
    override fun getData() = this

    override fun isExplicit() = lbrace == null

    override fun getReferableList(): List<Referable?> = identifierOrUnknownList.map { it.defIdentifier }

    override fun getType() = expr
}

abstract class ArendTypeTeleImplMixin(node: ASTNode): ArendSourceNodeImpl(node), ArendTypeTele {
    override fun getData() = this

    override fun isExplicit() = lbrace == null

    override fun getReferableList(): List<Referable?> {
        val list = typedExpr?.identifierOrUnknownList?.map { it.defIdentifier } ?: listOf(null)
        return if (list.isEmpty()) listOf(null) else list
    }

    override fun getType(): Abstract.Expression? = typedExpr?.expr ?: literal ?: universeAtom
}

abstract class ArendFieldTeleImplMixin(node: ASTNode): ArendSourceNodeImpl(node), ArendFieldTele {
    override fun getData() = this

    override fun isExplicit() = lbrace == null

    override fun getReferableList(): List<Referable> = fieldDefIdentifierList

    override fun getType() = expr
}
