package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.naming.reference.Referable
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import org.vclang.psi.VcFieldTele
import org.vclang.psi.VcNameTele
import org.vclang.psi.VcTypeTele


abstract class VcNameTeleImplMixin(node: ASTNode): VcSourceNodeImpl(node), VcNameTele {
    override fun getData() = this

    override fun isExplicit(): Boolean = lbrace == null

    override fun getReferableList(): List<Referable?> = identifierOrUnknownList.map { it.defIdentifier }

    override fun getType() = expr
}

abstract class VcTypeTeleImplMixin(node: ASTNode): VcSourceNodeImpl(node), VcTypeTele {
    override fun getData() = this

    override fun isExplicit(): Boolean = lbrace == null

    override fun getReferableList(): List<Referable?> =
        typedExpr?.identifierOrUnknownList?.map { it.defIdentifier } ?: listOf(null)

    override fun getType(): Abstract.Expression? = typedExpr?.expr ?: literal ?: universeAtom
}

abstract class VcFieldTeleImplMixin(node: ASTNode): VcSourceNodeImpl(node), VcFieldTele {
    override fun getData() = this

    override fun isExplicit() = true

    override fun getReferableList(): List<Referable> = fieldDefIdentifierList

    override fun getType() = expr
}
