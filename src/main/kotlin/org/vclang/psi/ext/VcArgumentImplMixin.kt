package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.naming.reference.ClassReferable
import com.jetbrains.jetpad.vclang.naming.reference.NamedUnresolvedReference
import com.jetbrains.jetpad.vclang.term.Fixity
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import com.jetbrains.jetpad.vclang.term.abs.AbstractExpressionVisitor
import org.vclang.psi.*
import org.vclang.resolving.VcReference
import org.vclang.resolving.VcReferenceImpl


abstract class VcImplicitArgumentImplMixin(node: ASTNode) : VcSourceNodeImpl(node), VcImplicitArgument {
    override fun isExplicit(): Boolean = false

    override fun getFixity(): Fixity = Fixity.NONFIX

    override fun getExpression(): Abstract.Expression = expr
}

abstract class VcInfixArgumentImplMixin(node: ASTNode) : VcExprImplMixin(node), VcInfixArgument, VcReferenceElement {
    override fun isExplicit(): Boolean = true

    override fun getData() = this

    override fun getFixity(): Fixity = Fixity.INFIX

    override fun getExpression(): VcExpr = this

    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
        visitor.visitReference(this, NamedUnresolvedReference(this, referenceName), null, null, params)

    override val referenceNameElement
        get() = this

    override val referenceName: String
        get() = infix.text.removeSurrounding("`")

    override fun getReference(): VcReference = VcReferenceImpl(this)
}

abstract class VcPostfixArgumentImplMixin(node: ASTNode) : VcExprImplMixin(node), VcPostfixArgument, VcReferenceElement {
    override fun isExplicit(): Boolean = true

    override fun getData() = this

    override fun getFixity(): Fixity = Fixity.POSTFIX

    override fun getExpression(): VcExpr = this

    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
        visitor.visitReference(this, NamedUnresolvedReference(this, referenceName), null, null, params)

    override val referenceNameElement
        get() = this

    override val referenceName: String
        get() = postfix.text.removePrefix("`")

    override fun getReference(): VcReference = VcReferenceImpl(this)
}

abstract class VcNewArgImplMixin(node: ASTNode) : VcExprImplMixin(node), VcNewArg, Abstract.ClassReferenceHolder {
    override fun isExplicit(): Boolean = true

    override fun getData() = this

    override fun getFixity(): Fixity = Fixity.NONFIX

    override fun getExpression(): VcExpr = this

    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
        visitor.visitClassExt(this, true, appExpr, if (lbrace == null) null else coClauseList, emptyList(), params)

    override fun getClassReference(): ClassReferable? = VcNewExprImplMixin.getClassReference(appExpr)
}

abstract class VcAtomArgumentImplMixin(node: ASTNode) : VcSourceNodeImpl(node), VcAtomArgument {
    override fun isExplicit(): Boolean = true

    override fun getFixity(): Fixity {
        val atomFieldsAcc = atomFieldsAcc
        if (!atomFieldsAcc.fieldAccList.isEmpty()) return Fixity.NONFIX
        val literal = atomFieldsAcc.atom.literal ?: return Fixity.NONFIX
        return if (literal.longName == null) Fixity.NONFIX else Fixity.UNKNOWN
    }

    override fun getExpression(): VcExpr = atomFieldsAcc
}

