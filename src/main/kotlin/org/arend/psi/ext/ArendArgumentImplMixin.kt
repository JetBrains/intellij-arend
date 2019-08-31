package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.naming.reference.LongUnresolvedReference
import org.arend.naming.reference.NamedUnresolvedReference
import org.arend.naming.reference.Referable
import org.arend.naming.scope.EmptyScope
import org.arend.naming.scope.Scope
import org.arend.psi.*
import org.arend.resolving.ArendReference
import org.arend.resolving.ArendReferenceImpl
import org.arend.term.Fixity
import org.arend.term.abs.Abstract
import org.arend.term.abs.AbstractExpressionVisitor


abstract class ArendImplicitArgumentImplMixin(node: ASTNode) : ArendSourceNodeImpl(node), ArendImplicitArgument {
    override fun isExplicit() = false

    override fun getFixity() = Fixity.NONFIX

    override fun getExpression() = expr
}

abstract class ArendFixityArgumentImplMixin(node: ASTNode) : ArendExprImplMixin(node), ArendReferenceElement, Abstract.Argument, Abstract.BinOpSequenceElem {
    override fun getExpression() = this

    val referent: Referable
        get() {
            val longName = (parent as? ArendLiteral)?.longName
            return if (longName == null) {
                NamedUnresolvedReference(this, referenceName)
            } else {
                LongUnresolvedReference.make(this, longName.refIdentifierList.map { it.referenceName } + listOf(referenceName))
            }
        }

    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
        visitor.visitReference(this, referent, fixity, null, null, if (visitor.visitErrors()) getErrorData(this) else null, params)

    override val referenceNameElement
        get() = this

    override fun getReference(): ArendReference = ArendReferenceImpl(this)

    override val scope: Scope
        get() {
            val longName = (parent as? ArendLiteral)?.longName ?: return super.scope
            val parentScope = parentSourceNode?.scope ?: (containingFile as? ArendFile)?.scope ?: EmptyScope.INSTANCE
            return LongUnresolvedReference(this, longName.refIdentifierList.map { it.referenceName }).resolveNamespaceWithArgument(parentScope)
        }
}

abstract class ArendInfixArgumentImplMixin(node: ASTNode) : ArendFixityArgumentImplMixin(node), ArendInfixArgument {
    override fun getFixity() = Fixity.INFIX

    override val referenceName: String
        get() = infix.text.removeSurrounding("`")
}

abstract class ArendPostfixArgumentImplMixin(node: ASTNode) : ArendFixityArgumentImplMixin(node), ArendPostfixArgument {
    override fun getFixity() = Fixity.POSTFIX

    override val referenceName: String
        get() = postfix.text.removePrefix("`")
}

abstract class ArendAtomArgumentImplMixin(node: ASTNode) : ArendSourceNodeImpl(node), ArendAtomArgument {
    override fun isExplicit() = true

    override fun getFixity(): Fixity {
        val atomFieldsAcc = atomFieldsAcc
        if (atomFieldsAcc.fieldAccList.isNotEmpty()) {
            return Fixity.NONFIX
        }

        val literal = atomFieldsAcc.atom.literal ?: return Fixity.NONFIX
        return literal.argument?.fixity ?: if (literal.longName == null) Fixity.NONFIX else Fixity.UNKNOWN
    }

    override fun getExpression() = atomFieldsAcc
}

abstract class ArendArgumentImplMixin(node: ASTNode) : ArendSourceNodeImpl(node), ArendArgument {
    override fun isExplicit() = true

    override fun getFixity() = Fixity.UNKNOWN

    override fun getExpression(): ArendExpr? = null
}