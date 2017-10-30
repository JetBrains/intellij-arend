package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.naming.reference.Referable
import com.jetbrains.jetpad.vclang.term.Precedence
import org.vclang.psi.VcNsId
import org.vclang.psi.ext.impl.ReferableAdapter


abstract class VcNsIdImplMixin(node: ASTNode) : VcCompositeElementImpl(node), VcNsId {
    override fun getOldReference(): Referable = refIdentifier.referent

    override fun getNewReferable(): Referable? = defIdentifier

    override fun getPrecedence(): Precedence? = prec.let { ReferableAdapter.calcPrecedence(it) }
}