package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.naming.reference.Referable
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import org.vclang.psi.VcTele


abstract class VcTeleImplMixin(node: ASTNode): VcCompositeElementImpl(node), VcTele, Abstract.Parameter {
    override fun getData(): VcTeleImplMixin = this

    override fun isExplicit(): Boolean = lbrace == null

    override fun getReferableList(): List<Referable?> {
        typedExpr?.identifierOrUnknownList?.map { it.defIdentifier }?.let { return it }
        literal?.longName?.let { return listOf(it.referent) }
        literal?.underscore?.let { return listOf(null) }
        return emptyList()
    }

    override fun getType(): Abstract.Expression? = typedExpr?.expr
}