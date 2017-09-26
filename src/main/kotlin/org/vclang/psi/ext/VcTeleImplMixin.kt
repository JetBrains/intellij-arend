package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import org.vclang.psi.VcDefIdentifier
import org.vclang.psi.VcTele


abstract class VcTeleImplMixin(node: ASTNode): VcCompositeElementImpl(node), VcTele, Abstract.Parameter {
    override fun getData(): VcTeleImplMixin = this

    override fun isExplicit(): Boolean = lbrace == null

    override fun getReferableList(): List<VcDefIdentifier?> = typedExpr?.identifierOrUnknownList?.map { it.defIdentifier } ?: emptyList()

    override fun getType(): Abstract.Expression? = typedExpr?.expr
}