package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.psi.ArendExpr
import org.arend.psi.ArendOverriddenField
import org.arend.psi.ArendTypeTele

abstract class ArendOverriddenFieldImplMixin(node: ASTNode) : ArendSourceNodeImpl(node), ArendOverriddenField {
    override fun getData() = this

    override fun getOverriddenField() = longName

    override fun getParameters(): List<ArendTypeTele> = typeTeleList

    override fun getResultType(): ArendExpr? = returnExpr?.let { it.expr ?: it.atomFieldsAccList.firstOrNull() }

    override fun getResultTypeLevel(): ArendExpr? = returnExpr?.atomFieldsAccList?.getOrNull(1)
}