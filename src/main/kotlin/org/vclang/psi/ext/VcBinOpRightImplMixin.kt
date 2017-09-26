package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.naming.reference.NamedUnresolvedReference
import com.jetbrains.jetpad.vclang.naming.reference.Referable
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import org.vclang.psi.VcBinOpRight


abstract class VcBinOpRightImplMixin(node: ASTNode) : VcCompositeElementImpl(node), VcBinOpRight {
    override fun getBinOpReference(): Referable {
        infixName?.let { return NamedUnresolvedReference(it, it.referenceName ?: text) }
        postfixName?.let { return NamedUnresolvedReference(it, it.referenceName ?: text) }
        return NamedUnresolvedReference(this, text)
    }

    override fun getArgument(): Abstract.Expression? = newExpr
}