package com.jetbrains.arend.ide.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.arend.ide.psi.ArdNsId
import com.jetbrains.arend.ide.psi.ext.impl.ReferableAdapter
import com.jetbrains.jetpad.vclang.naming.reference.Referable
import com.jetbrains.jetpad.vclang.term.Precedence


abstract class ArdNsIdImplMixin(node: ASTNode) : ArdCompositeElementImpl(node), ArdNsId {
    override fun getOldReference(): Referable = refIdentifier.referent

    override fun getName() = defIdentifier?.referenceName

    override fun getPrecedence(): Precedence? = prec.let { ReferableAdapter.calcPrecedence(it) }
}