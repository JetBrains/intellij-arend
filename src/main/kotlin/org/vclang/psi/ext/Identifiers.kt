package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import com.jetbrains.jetpad.vclang.naming.reference.ClassReferable
import com.jetbrains.jetpad.vclang.naming.reference.NamedUnresolvedReference
import com.jetbrains.jetpad.vclang.naming.reference.Referable
import org.vclang.psi.*
import org.vclang.resolving.VcDefReferenceImpl
import org.vclang.resolving.VcPatternDefReferenceImpl
import org.vclang.resolving.VcReference
import org.vclang.resolving.VcReferenceImpl
import org.vclang.typing.ReferableExtractVisitor

abstract class VcDefIdentifierImplMixin(node: ASTNode) : PsiReferableImpl(node), VcDefIdentifier {
    override val referenceNameElement
        get() = this

    override val referenceName: String
        get() = text

    override fun getName(): String = referenceName

    override fun textRepresentation(): String = referenceName

    override fun getReference(): VcReference {
        val parent = parent
        return when (parent) {
            is VcPattern, is VcAtomPatternOrPrefix -> VcPatternDefReferenceImpl<VcDefIdentifier>(this, parent is VcPattern && !parent.atomPatternOrPrefixList.isEmpty())
            else -> VcDefReferenceImpl<VcDefIdentifier>(this)
        }
    }

    override fun getTypeClassReference(): ClassReferable? {
        val parent = parent
        val expr = when (parent) {
            is VcIdentifierOrUnknown -> {
                val pparent = parent.parent
                when (pparent) {
                    is VcNameTele -> pparent.expr
                    is VcTypedExpr -> pparent.expr
                    else -> null
                }
            }
            is VcFieldDefIdentifier -> (parent.parent as? VcFieldTele)?.expr
            is VcLetClause -> parent.typeAnnotation?.expr
            else -> null
        } ?: return null

        return ReferableExtractVisitor(expr.scope).findClassReferable(expr)
    }

    override fun getUseScope(): SearchScope {
        if (parent != null && parent.parent is VcTypedExpr && parent.parent.parent is VcTypeTele) {
            return LocalSearchScope(parent.parent.parent.parent) //Pi expression
        } else if (parent != null && parent.parent is VcFieldTele) {
            return LocalSearchScope(parent.parent.parent)
        } else if (parent != null && parent.parent is VcNameTele) {
            return LocalSearchScope(parent.parent.parent)
        } else if (parent is VcAtomPatternOrPrefix && parent.parent != null) {
            return LocalSearchScope(parent.parent.parent) // Pattern variables
        }
        return super.getUseScope()
    }
}

abstract class VcRefIdentifierImplMixin(node: ASTNode) : VcSourceNodeImpl(node), VcRefIdentifier {
    override val referenceNameElement
        get() = this

    override val referenceName: String
        get() = text

    override fun getName(): String = referenceName

    override fun getData() = this

    override fun getReferent(): Referable = NamedUnresolvedReference(this, referenceName)

    override fun getReference(): VcReference = VcReferenceImpl<VcRefIdentifier>(this)
}
