package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.naming.reference.NamedUnresolvedReference
import org.arend.psi.*
import org.arend.term.Fixity
import org.arend.term.abs.Abstract
import org.arend.term.abs.AbstractExpressionVisitor
import org.arend.resolving.ArendReference
import org.arend.resolving.ArendReferenceImpl


abstract class ArendImplicitArgumentImplMixin(node: ASTNode) : ArendSourceNodeImpl(node), ArendImplicitArgument {
    override fun isExplicit(): Boolean = false

    override fun getFixity(): Fixity = Fixity.NONFIX

    override fun getExpression(): Abstract.Expression = expr
}

abstract class ArendInfixArgumentImplMixin(node: ASTNode) : ArendExprImplMixin(node), ArendInfixArgument, ArendReferenceElement {
    override fun isExplicit(): Boolean = true

    override fun getData() = this

    override fun getFixity(): Fixity = Fixity.INFIX

    override fun getExpression(): ArendExpr = this

    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
        visitor.visitReference(this, NamedUnresolvedReference(this, referenceName), null, null, if (visitor.visitErrors()) org.arend.psi.ext.getErrorData(this) else null, params)

    override val referenceNameElement
        get() = this

    override val referenceName: String
        get() = infix.text.removeSurrounding("`")

    override fun getReference(): ArendReference = ArendReferenceImpl(this)
}

abstract class ArendPostfixArgumentImplMixin(node: ASTNode) : ArendExprImplMixin(node), ArendPostfixArgument, ArendReferenceElement {
    override fun isExplicit(): Boolean = true

    override fun getData() = this

    override fun getFixity(): Fixity = Fixity.POSTFIX

    override fun getExpression(): ArendExpr = this

    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
        visitor.visitReference(this, NamedUnresolvedReference(this, referenceName), null, null, if (visitor.visitErrors()) org.arend.psi.ext.getErrorData(this) else null, params)

    override val referenceNameElement
        get() = this

    override val referenceName: String
        get() = postfix.text.removePrefix("`")

    override fun getReference(): ArendReference = ArendReferenceImpl(this)
}

abstract class ArendNewArgImplMixin(node: ASTNode) : ArendNewExprImplMixin(node), ArendNewArg, Abstract.ClassReferenceHolder {
    override fun getFixity(): Fixity = Fixity.NONFIX

    override fun getExpression(): ArendExpr = this
}

abstract class ArendAtomArgumentImplMixin(node: ASTNode) : ArendSourceNodeImpl(node), ArendAtomArgument {
    override fun isExplicit(): Boolean = true

    override fun getFixity(): Fixity {
        val atomFieldsAcc = atomFieldsAcc
        if (!atomFieldsAcc.fieldAccList.isEmpty()) return Fixity.NONFIX
        val literal = atomFieldsAcc.atom.literal ?: return Fixity.NONFIX
        return if (literal.longName == null) Fixity.NONFIX else Fixity.UNKNOWN
    }

    override fun getExpression(): ArendExpr = atomFieldsAcc
}

abstract class ArendArgumentImplMixin(node: ASTNode) : ArendSourceNodeImpl(node), ArendArgument {
    override fun isExplicit(): Boolean = true

    override fun getFixity(): Fixity = Fixity.UNKNOWN

    override fun getExpression(): ArendExpr? = null
}