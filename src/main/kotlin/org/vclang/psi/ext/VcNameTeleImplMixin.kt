package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.naming.reference.Referable
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import org.vclang.psi.VcNameTele


abstract class VcNameTeleImplMixin(node: ASTNode): VcCompositeElementImpl(node), VcNameTele, Abstract.Parameter {
    override fun getData() = this

    override fun isExplicit(): Boolean = lbrace == null

    override fun getReferableList(): List<Referable?> =
        defIdentifier?.let { listOf(it) } ?: underscore?.let { listOf(null) } ?: identifierOrUnknownList.map { it.defIdentifier }

    override fun getType() = expr
}