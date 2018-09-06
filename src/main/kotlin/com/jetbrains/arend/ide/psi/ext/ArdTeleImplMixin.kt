package com.jetbrains.arend.ide.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.arend.ide.psi.ArdFieldTele
import com.jetbrains.arend.ide.psi.ArdNameTele
import com.jetbrains.arend.ide.psi.ArdTypeTele
import com.jetbrains.jetpad.vclang.naming.reference.Referable
import com.jetbrains.jetpad.vclang.term.abs.Abstract


abstract class ArdNameTeleImplMixin(node: ASTNode) : ArdSourceNodeImpl(node), ArdNameTele {
    override fun getData() = this

    override fun isExplicit() = lbrace == null

    override fun getReferableList(): List<Referable?> = identifierOrUnknownList.map { it.defIdentifier }

    override fun getType() = expr
}

abstract class ArdTypeTeleImplMixin(node: ASTNode) : ArdSourceNodeImpl(node), ArdTypeTele {
    override fun getData() = this

    override fun isExplicit() = lbrace == null

    override fun getReferableList(): List<Referable?> {
        val list = typedExpr?.identifierOrUnknownList?.map { it.defIdentifier } ?: listOf(null)
        return if (list.isEmpty()) listOf(null) else list
    }

    override fun getType(): Abstract.Expression? = typedExpr?.expr ?: literal ?: universeAtom
}

abstract class ArdFieldTeleImplMixin(node: ASTNode) : ArdSourceNodeImpl(node), ArdFieldTele {
    override fun getData() = this

    override fun isExplicit() = lbrace == null

    override fun getReferableList(): List<Referable> = fieldDefIdentifierList

    override fun getType() = expr
}
