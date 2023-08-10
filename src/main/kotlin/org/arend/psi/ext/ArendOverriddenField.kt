package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.psi.childOfType
import org.arend.psi.getChildrenOfType
import org.arend.term.abs.Abstract

class ArendOverriddenField(node: ASTNode) : ArendSourceNodeImpl(node), Abstract.OverriddenField {
    val returnExpr: ArendReturnExpr?
        get() = childOfType()

    override fun getData() = this

    override fun getOverriddenField(): ArendLongName? = childOfType()

    override fun getParameters(): List<ArendTypeTele> = getChildrenOfType()

    override fun getResultType(): ArendExpr? = returnExpr?.type

    override fun getResultTypeLevel(): ArendExpr? = returnExpr?.typeLevel
}