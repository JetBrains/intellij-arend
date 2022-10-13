package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.term.abs.Abstract
import org.arend.term.abs.AbstractExpressionVisitor
import java.lang.IllegalStateException


open class ArendExpr(node: ASTNode) : ArendSourceNodeImpl(node), Abstract.Expression, Abstract.Parameter {
    override fun getData() = this

    override fun isExplicit() = true

    override fun getReferableList() = listOf(null)

    override fun getType() = this

    override fun isStrict() = false

    override fun isProperty() = false

    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
        throw IllegalStateException()
    }
}