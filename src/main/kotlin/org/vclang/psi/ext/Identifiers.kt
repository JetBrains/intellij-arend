package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import com.jetbrains.jetpad.vclang.error.ErrorReporter
import com.jetbrains.jetpad.vclang.naming.reference.NamedUnresolvedReference
import com.jetbrains.jetpad.vclang.naming.reference.Referable
import com.jetbrains.jetpad.vclang.term.Precedence
import com.jetbrains.jetpad.vclang.term.concrete.Concrete
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

abstract class VcFieldDefIdentifierImplMixin(node: ASTNode) : PsiReferableImpl(node), VcFieldDefIdentifier {
    override val referenceNameElement
        get() = this

    override val referenceName: String
        get() = text

    override fun getName(): String = referenceName

    override fun textRepresentation(): String = referenceName

    override fun getReference(): VcReference = VcDefReferenceImpl<VcFieldDefIdentifier>(this)

    override fun getTypecheckable(): VcDefClass? = ancestors.filterIsInstance<VcDefClass>().firstOrNull()

    override fun computeConcrete(errorReporter: ErrorReporter): Concrete.ClassField? {
        val classDef = typecheckable?.computeConcrete(errorReporter) as? Concrete.ClassDefinition ?: return null
        return classDef.fields.firstOrNull { it.data === this }
    }

    override fun getPrecedence(): Precedence = Precedence.DEFAULT

    override fun getReferable() = this

    override fun isVisible() = false

    override fun getUseScope(): SearchScope {
        if (parent is VcFieldTele) {
            return LocalSearchScope(parent.parent)
        }
        return super.getUseScope()
    }
}

abstract class VcRefIdentifierImplMixin(node: ASTNode) : VcCompositeElementImpl(node), VcRefIdentifier {
    override val referenceNameElement
        get() = this

    override val referenceName: String
        get() = text

    override fun getName(): String = referenceName

    override fun getData() = this

    override fun getReferent(): Referable = NamedUnresolvedReference(this, referenceName)

    override fun getReference(): VcReference = VcReferenceImpl<VcRefIdentifier>(this)
}
