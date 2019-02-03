package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import org.arend.naming.reference.ClassReferable
import org.arend.naming.reference.NamedUnresolvedReference
import org.arend.naming.reference.Referable
import org.arend.psi.*
import org.arend.resolving.ArendDefReferenceImpl
import org.arend.resolving.ArendPatternDefReferenceImpl
import org.arend.resolving.ArendReference
import org.arend.resolving.ArendReferenceImpl
import org.arend.typing.ExpectedTypeVisitor
import org.arend.typing.ReferableExtractVisitor

abstract class ArendDefIdentifierImplMixin(node: ASTNode) : PsiReferableImpl(node), ArendDefIdentifier {
    override val referenceNameElement
        get() = this

    override val referenceName: String
        get() = text

    override fun getName(): String = referenceName

    override fun textRepresentation(): String = referenceName

    override fun getReference(): ArendReference {
        val parent = parent
        return when (parent) {
            is ArendPattern, is ArendAtomPatternOrPrefix -> ArendPatternDefReferenceImpl<ArendDefIdentifier>(this, parent is ArendPattern && !parent.atomPatternOrPrefixList.isEmpty())
            else -> ArendDefReferenceImpl<ArendDefIdentifier>(this)
        }
    }

    override fun getTypeClassReference(): ClassReferable? =
        typeOf?.let { ReferableExtractVisitor().findClassReferable(it) }

    override fun getParameterType(params: List<Boolean>): Any? = ExpectedTypeVisitor.getParameterType(typeOf, params, name)

    override fun getTypeOf(): ArendExpr? {
        val parent = parent
        return when (parent) {
            is ArendIdentifierOrUnknown -> {
                val pparent = parent.parent
                when (pparent) {
                    is ArendNameTele -> pparent.expr
                    is ArendTypedExpr -> pparent.expr
                    else -> null
                }
            }
            is ArendFieldDefIdentifier -> (parent.parent as? ArendFieldTele)?.expr
            is ArendLetClause -> parent.typeAnnotation?.expr
            is ArendPatternImplMixin -> parent.getExpr()
            else -> null
        }
    }

    override val psiElementType: PsiElement?
        get() {
            val parent = parent
            return when (parent) {
                is ArendLetClause -> parent.typeAnnotation?.expr
                is ArendIdentifierOrUnknown -> {
                    val pparent = parent.parent
                    when (pparent) {
                        is ArendNameTele -> pparent.expr
                        is ArendTypedExpr -> pparent.expr
                        else -> null
                    }
                }
                else -> null
            }
        }

    override fun getUseScope(): SearchScope {
        val parent = parent
        if (parent != null) when {
            parent.parent is ArendTypedExpr && parent.parent.parent is ArendTypeTele -> return LocalSearchScope(parent.parent.parent.parent) // Pi expression
            parent.parent is ArendNameTele -> return LocalSearchScope(parent.parent.parent)
            parent is ArendAtomPatternOrPrefix && parent.parent != null -> {
                var p = parent.parent.parent
                while (p != null && p !is ArendClause) p = p.parent
                if (p is ArendClause) return LocalSearchScope(p) // Pattern variables
            }
            parent is ArendPattern && parent.parent is ArendClause -> return LocalSearchScope(parent.parent)
        }
        return super.getUseScope()
    }
}

abstract class ArendRefIdentifierImplMixin(node: ASTNode) : ArendSourceNodeImpl(node), ArendRefIdentifier {
    override val referenceNameElement
        get() = this

    override val referenceName: String
        get() = text

    override fun getName(): String = referenceName

    override fun getData() = this

    override fun getReferent(): Referable = NamedUnresolvedReference(this, referenceName)

    override fun getReference(): ArendReference = ArendReferenceImpl<ArendRefIdentifier>(this)
}
