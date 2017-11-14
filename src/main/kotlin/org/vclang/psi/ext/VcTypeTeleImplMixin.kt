package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.naming.reference.Referable
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import org.vclang.psi.VcTypeTele


abstract class VcTypeTeleImplMixin(node: ASTNode): VcCompositeElementImpl(node), VcTypeTele, Abstract.Parameter {
    override fun getData() = this

    override fun isExplicit(): Boolean = lbrace == null

    override fun getReferableList(): List<Referable?> =
        typedExpr?.identifierOrUnknownList?.map { it.defIdentifier } ?: listOf(null)

    override fun getType(): Abstract.Expression? = typedExpr?.expr ?: literal ?: universeAtom
}
