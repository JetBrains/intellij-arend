package org.arend.resolving.util

import org.arend.naming.reference.Referable
import org.arend.term.abs.Abstract
import org.arend.term.abs.AbstractExpressionVisitor


open class ParameterImpl(private val isExplicit: Boolean, private val referables: List<Referable?>, private val type: Abstract.Expression?) : Abstract.SourceNodeImpl(), Abstract.Parameter {
    override fun getData() = this

    override fun isExplicit() = isExplicit

    override fun getReferableList() = referables

    override fun getType() = type

    override fun isStrict() = false

    override fun isProperty() = false
}

class ReferenceImpl(private val referable: Referable) : Abstract.SourceNodeImpl(), Abstract.Expression {
    override fun getData() = this

    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
        visitor.visitReference(this, referable, null, null, null, params)

    override fun toString() = referable.textRepresentation()
}

class PiImpl(private val parameters: Collection<Abstract.Parameter>, private val codomain: Abstract.Expression?) : Abstract.SourceNodeImpl(), Abstract.Expression {
    override fun getData() = this

    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
        visitor.visitPi(this, parameters, codomain, params)

    override fun toString() = "a pi type"
}

object Universe : Abstract.SourceNodeImpl(), Abstract.Expression {
    override fun getData() = this

    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
        visitor.visitUniverse(this, null, null, null, null, params)

    override fun toString() = "a universe"
}

fun getTypeOf(parameters: Collection<Abstract.Parameter>, expr: Abstract.Expression?): Abstract.Expression? =
    if (parameters.isEmpty()) expr else PiImpl(parameters, expr)