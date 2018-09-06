package com.jetbrains.arend.ide.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import com.jetbrains.arend.ide.psi.*
import com.jetbrains.arend.ide.resolving.ArdDefReferenceImpl
import com.jetbrains.arend.ide.resolving.ArdPatternDefReferenceImpl
import com.jetbrains.arend.ide.resolving.ArdReference
import com.jetbrains.arend.ide.resolving.ArdReferenceImpl
import com.jetbrains.arend.ide.typing.ExpectedTypeVisitor
import com.jetbrains.arend.ide.typing.ReferableExtractVisitor
import com.jetbrains.jetpad.vclang.naming.reference.ClassReferable
import com.jetbrains.jetpad.vclang.naming.reference.NamedUnresolvedReference
import com.jetbrains.jetpad.vclang.naming.reference.Referable

abstract class ArdDefIdentifierImplMixin(node: ASTNode) : PsiReferableImpl(node), ArdDefIdentifier {
    override val referenceNameElement
        get() = this

    override val referenceName: String
        get() = text

    override fun getName(): String = referenceName

    override fun textRepresentation(): String = referenceName

    override fun getReference(): ArdReference {
        val parent = parent
        return when (parent) {
            is ArdPattern, is ArdAtomPatternOrPrefix -> ArdPatternDefReferenceImpl<ArdDefIdentifier>(this, parent is ArdPattern && !parent.atomPatternOrPrefixList.isEmpty())
            else -> ArdDefReferenceImpl<ArdDefIdentifier>(this)
        }
    }

    override fun getTypeClassReference(): ClassReferable? =
            typeOf?.let { ReferableExtractVisitor().findClassReferable(it) }

    override fun getParameterType(params: List<Boolean>): Any? = ExpectedTypeVisitor.getParameterType(typeOf, params, name)

    override fun getTypeOf(): ArdExpr? {
        val parent = parent
        return when (parent) {
            is ArdIdentifierOrUnknown -> {
                val pparent = parent.parent
                when (pparent) {
                    is ArdNameTele -> pparent.expr
                    is ArdTypedExpr -> pparent.expr
                    else -> null
                }
            }
            is ArdFieldDefIdentifier -> (parent.parent as? ArdFieldTele)?.expr
            is ArdLetClause -> parent.typeAnnotation?.expr
            else -> null
        }
    }

    override val psiElementType: PsiElement?
        get() {
            val parent = parent
            return when (parent) {
                is ArdLetClause -> parent.typeAnnotation?.expr
                is ArdIdentifierOrUnknown -> {
                    val pparent = parent.parent
                    when (pparent) {
                        is ArdNameTele -> pparent.expr
                        is ArdTypedExpr -> pparent.expr
                        else -> null
                    }
                }
                else -> null
            }
        }

    override fun getUseScope(): SearchScope {
        if (parent != null && parent.parent is ArdTypedExpr && parent.parent.parent is ArdTypeTele) {
            return LocalSearchScope(parent.parent.parent.parent) //Pi expression
        } else if (parent != null && parent.parent is ArdFieldTele) {
            return LocalSearchScope(parent.parent.parent)
        } else if (parent != null && parent.parent is ArdNameTele) {
            return LocalSearchScope(parent.parent.parent)
        } else if (parent is ArdAtomPatternOrPrefix && parent.parent != null) {
            var p = parent.parent.parent
            while (p != null && p !is ArdClause) p = p.parent
            if (p is ArdClause) return LocalSearchScope(p) // Pattern variables
        } else if (parent is ArdPattern) {
            if (parent.parent is ArdClause) return LocalSearchScope(parent.parent)
        }
        return super.getUseScope()
    }
}

abstract class ArdRefIdentifierImplMixin(node: ASTNode) : ArdSourceNodeImpl(node), ArdRefIdentifier {
    override val referenceNameElement
        get() = this

    override val referenceName: String
        get() = text

    override fun getName(): String = referenceName

    override fun getData() = this

    override fun getReferent(): Referable = NamedUnresolvedReference(this, referenceName)

    override fun getReference(): ArdReference = ArdReferenceImpl<ArdRefIdentifier>(this)
}
