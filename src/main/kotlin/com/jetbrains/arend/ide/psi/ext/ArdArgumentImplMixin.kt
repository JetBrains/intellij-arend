package com.jetbrains.arend.ide.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.arend.ide.psi.*
import com.jetbrains.arend.ide.resolving.ArdReference
import com.jetbrains.arend.ide.resolving.ArdReferenceImpl
import com.jetbrains.jetpad.vclang.naming.reference.NamedUnresolvedReference
import com.jetbrains.jetpad.vclang.term.Fixity
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import com.jetbrains.jetpad.vclang.term.abs.AbstractExpressionVisitor


abstract class ArdImplicitArgumentImplMixin(node: ASTNode) : ArdSourceNodeImpl(node), ArdImplicitArgument {
    override fun isExplicit(): Boolean = false

    override fun getFixity(): Fixity = Fixity.NONFIX

    override fun getExpression(): Abstract.Expression = expr
}

abstract class ArdInfixArgumentImplMixin(node: ASTNode) : ArdExprImplMixin(node), ArdInfixArgument, ArdReferenceElement {
    override fun isExplicit(): Boolean = true

    override fun getData() = this

    override fun getFixity(): Fixity = Fixity.INFIX

    override fun getExpression(): ArdExpr = this

    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
            visitor.visitReference(this, NamedUnresolvedReference(this, referenceName), null, null, if (visitor.visitErrors()) com.jetbrains.arend.ide.psi.ext.getErrorData(this) else null, params)

    override val referenceNameElement
        get() = this

    override val referenceName: String
        get() = infix.text.removeSurrounding("`")

    override fun getReference(): ArdReference = ArdReferenceImpl(this)
}

abstract class ArdPostfixArgumentImplMixin(node: ASTNode) : ArdExprImplMixin(node), ArdPostfixArgument, ArdReferenceElement {
    override fun isExplicit(): Boolean = true

    override fun getData() = this

    override fun getFixity(): Fixity = Fixity.POSTFIX

    override fun getExpression(): ArdExpr = this

    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
            visitor.visitReference(this, NamedUnresolvedReference(this, referenceName), null, null, if (visitor.visitErrors()) com.jetbrains.arend.ide.psi.ext.getErrorData(this) else null, params)

    override val referenceNameElement
        get() = this

    override val referenceName: String
        get() = postfix.text.removePrefix("`")

    override fun getReference(): ArdReference = ArdReferenceImpl(this)
}

abstract class ArdNewArgImplMixin(node: ASTNode) : ArdNewExprImplMixin(node), ArdNewArg, Abstract.ClassReferenceHolder {
    override fun getFixity(): Fixity = Fixity.NONFIX

    override fun getExpression(): ArdExpr = this
}

abstract class ArdAtomArgumentImplMixin(node: ASTNode) : ArdSourceNodeImpl(node), ArdAtomArgument {
    override fun isExplicit(): Boolean = true

    override fun getFixity(): Fixity {
        val atomFieldsAcc = atomFieldsAcc
        if (!atomFieldsAcc.fieldAccList.isEmpty()) return Fixity.NONFIX
        val literal = atomFieldsAcc.atom.literal ?: return Fixity.NONFIX
        return if (literal.longName == null) Fixity.NONFIX else Fixity.UNKNOWN
    }

    override fun getExpression(): ArdExpr = atomFieldsAcc
}

abstract class ArdArgumentImplMixin(node: ASTNode) : ArdSourceNodeImpl(node), ArdArgument {
    override fun isExplicit(): Boolean = true

    override fun getFixity(): Fixity = Fixity.UNKNOWN

    override fun getExpression(): ArdExpr? = null
}