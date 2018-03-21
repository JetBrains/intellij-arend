package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.naming.reference.Referable
import org.vclang.psi.VcCoClause
import org.vclang.psi.VcExpr
import org.vclang.psi.VcNameTele


abstract class VcCoClauseImplMixin(node: ASTNode) : VcSourceNodeImpl(node), VcCoClause {
    override fun getData() = this

    override fun getImplementedField(): Referable = refIdentifier.referent

    override fun getParameters(): List<VcNameTele> = nameTeleList

    override fun getImplementation(): VcExpr = expr
}