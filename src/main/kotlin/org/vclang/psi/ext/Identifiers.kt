package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.naming.reference.NamedUnresolvedReference
import com.jetbrains.jetpad.vclang.naming.reference.Referable
import org.vclang.psi.VcAtomPatternOrPrefix
import org.vclang.psi.VcDefIdentifier
import org.vclang.psi.VcPatternConstructor
import org.vclang.psi.VcRefIdentifier
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
