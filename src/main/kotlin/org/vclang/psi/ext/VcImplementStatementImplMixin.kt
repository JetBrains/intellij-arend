package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.naming.reference.Referable
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import org.vclang.psi.VcImplementStatement


abstract class VcImplementStatementImplMixin(node: ASTNode) : VcCompositeElementImpl(node), VcImplementStatement {
    override fun getData(): VcImplementStatementImplMixin = this

    override fun getImplementedField(): Referable = longName.referent

    override fun getImplementation(): Abstract.Expression? = expr
}