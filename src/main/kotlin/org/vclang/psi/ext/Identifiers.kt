package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import com.jetbrains.jetpad.vclang.naming.reference.ClassReferable
import com.jetbrains.jetpad.vclang.naming.reference.NamedUnresolvedReference
import com.jetbrains.jetpad.vclang.naming.reference.Referable
import com.jetbrains.jetpad.vclang.term.abs.ReferableExtractVisitor
import org.vclang.psi.*
import org.vclang.psi.impl.VcAtomPatternOrPrefixImpl
import org.vclang.resolving.VcDefReferenceImpl
import org.vclang.resolving.VcPatternDefReferenceImpl
import org.vclang.resolving.VcReference
import org.vclang.resolving.VcReferenceImpl

abstract class VcDefIdentifierImplMixin(node: ASTNode) : PsiReferableImpl(node), VcDefIdentifier {
    override val referenceNameElement
        get() = this

    override val referenceName: String
        get() = text

    override fun getName(): String = referenceName

    override fun textRepresentation(): String = referenceName

    override fun getReference(): VcReference =
        when (parent) {
            is VcPatternConstructor, is VcAtomPatternOrPrefix -> VcPatternDefReferenceImpl<VcDefIdentifier>(this)
            else -> VcDefReferenceImpl<VcDefIdentifier>(this)
        }

    override fun resolveTypeClassReference(): ClassReferable? {
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

        return expr.accept(ReferableExtractVisitor(expr.scope), null)
    }

    override fun getUseScope(): SearchScope {
        if (parent != null && parent.parent is VcTypedExpr && parent.parent.parent is VcTypeTele) {
            return LocalSearchScope(parent.parent.parent.parent) //Pi expression
        } else if (parent != null && parent.parent is VcFieldTele) {
            return LocalSearchScope(parent.parent.parent)
        } else if (parent != null && parent.parent is VcNameTele) {
            return LocalSearchScope(parent.parent.parent)
        } else if (parent is VcAtomPatternOrPrefixImpl && parent.parent is VcPatternConstructor && parent.parent.parent != null) {
            return LocalSearchScope(parent.parent.parent.parent) // Pattern variables
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
