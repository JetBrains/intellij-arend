package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.naming.reference.Referable
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import org.vclang.psi.VcCoClause


abstract class VcCoClauseImplMixin(node: ASTNode) : VcCompositeElementImpl(node), VcCoClause {
    override fun getData() = this

    override fun getImplementedField(): Referable = refIdentifier.referent

    override fun getImplementation(): Abstract.Expression? = expr
}