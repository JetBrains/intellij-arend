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
import org.arend.term.abs.Abstract
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

    override fun getParameterType(params: List<Boolean>): Any? =
        ExpectedTypeVisitor.getParameterType((parent as? ArendLetClause)?.parameters ?: emptyList(), typeOf, params, name)

    override fun getTypeClassReference(): ClassReferable? {
        val parent = parent
        return if (parent is ArendLetClauseImplMixin) {
            parent.typeClassReference
        } else {
            (typeOf as? ArendExpr)?.let { ReferableExtractVisitor().findClassReferable(it) }
        }
    }

    override fun getTypeOf(): Abstract.Expression? {
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
            is ArendLetClause -> ExpectedTypeVisitor.getTypeOf(parent.parameters, parent.resultType)
            is ArendLetClausePattern -> parent.typeAnnotation?.expr
            is ArendPatternImplMixin -> parent.getExpr()
            else -> null
        }
    }

    override val psiElementType: PsiElement?
        get() {
            val parent = parent
            return when (parent) {
                is ArendLetClausePattern -> parent.typeAnnotation?.expr
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
        when {
            parent is ArendLetClausePattern -> parent.ancestorsUntilFile.filterIsInstance<ArendLetExpr>().firstOrNull()?.let { return LocalSearchScope(it) } // Let clause pattern
            parent is ArendLetClause -> return LocalSearchScope(parent.parent) // Let clause
            (parent?.parent as? ArendTypedExpr)?.parent is ArendTypeTele -> return LocalSearchScope(parent.parent.parent.parent) // Pi expression
            parent?.parent is ArendNameTele -> return LocalSearchScope(parent.parent.parent)
            (parent as? ArendAtomPatternOrPrefix)?.parent != null -> {
                var p = parent.parent.parent
                while (p != null && p !is ArendClause) p = p.parent
                if (p is ArendClause) return LocalSearchScope(p) // Pattern variables
            }
            (parent as? ArendPattern)?.parent is ArendClause -> return LocalSearchScope(parent.parent)
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

    override fun getReference(): ArendReference {
        val parent = parent as? ArendLongName
        val isImport = (parent?.parent as? ArendStatCmd)?.importKw != null
        val last = if (isImport) parent?.refIdentifierList?.lastOrNull() else null
        return ArendReferenceImpl<ArendRefIdentifier>(this, last != null && last != this)
    }
}
