package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.naming.reference.NamedUnresolvedReference
import org.arend.naming.reference.Referable
import org.arend.psi.*
import org.arend.resolving.ArendReferenceImpl
import org.arend.term.Fixity
import org.arend.term.abs.AbstractExpressionVisitor


abstract class ArendImplicitArgumentImplMixin(node: ASTNode) : ArendSourceNodeImpl(node), ArendImplicitArgument {
    override fun isExplicit() = false

    override fun getFixity() = Fixity.NONFIX

    override fun getExpression() = expr
}

abstract class ArendInfixArgumentImplMixin(node: ASTNode) : ArendExprImplMixin(node), ArendInfixArgument, ArendReferenceElement {
    override fun getFixity() = Fixity.INFIX

    override fun getExpression() = this

    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
        visitor.visitReference(this, NamedUnresolvedReference(this, referenceName), null, null, if (visitor.visitErrors()) getErrorData(this) else null, params)

    override val referenceNameElement
        get() = this

    override val referenceName: String
        get() = infix.text.removeSurrounding("`")

    override fun getReference() = ArendReferenceImpl(this)
}

abstract class ArendPostfixArgumentImplMixin(node: ASTNode) : ArendExprImplMixin(node), ArendPostfixArgument, ArendReferenceElement {
    override fun getFixity() = Fixity.POSTFIX

    override fun getExpression() = this

    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
        visitor.visitReference(this, referent, null, null, if (visitor.visitErrors()) getErrorData(this) else null, params)

    val referent: Referable
        get() = NamedUnresolvedReference(this, referenceName)

    override val referenceNameElement
        get() = this

    override val referenceName: String
        get() = postfix.text.removePrefix("`")

    override fun getReference() = ArendReferenceImpl(this)
}

abstract class ArendAtomArgumentImplMixin(node: ASTNode) : ArendSourceNodeImpl(node), ArendAtomArgument {
    override fun isExplicit() = true

    override fun getFixity(): Fixity {
        val atomFieldsAcc = atomFieldsAcc
        return if (atomFieldsAcc.fieldAccList.isNotEmpty() || atomFieldsAcc.atom.literal?.longName == null) Fixity.NONFIX else Fixity.UNKNOWN
    }

    override fun getExpression() = atomFieldsAcc
}

abstract class ArendArgumentImplMixin(node: ASTNode) : ArendSourceNodeImpl(node), ArendArgument {
    override fun isExplicit() = true

    override fun getFixity() = Fixity.UNKNOWN

    override fun getExpression(): ArendExpr? = null
}