package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import org.vclang.psi.*
import org.vclang.resolving.*

abstract class VcDefIdentifierImplMixin(node: ASTNode) : PsiReferableImpl(node), VcDefIdentifier {
    override fun getName(): String = text

    override fun textRepresentation(): String = text
}

abstract class VcRefIdentifierImplMixin(node: ASTNode) : VcCompositeElementImpl(node), VcRefIdentifier {
    override val referenceNameElement: VcRefIdentifierImplMixin
        get() = this

    override val referenceName: String
        get() = text

    override fun getName(): String = referenceName

    override fun getReference(): VcReference = VcReferenceImpl<VcRefIdentifier>(this)
}

abstract class VcPrefixImplMixin(node: ASTNode) : VcCompositeElementImpl(node), VcPrefixName {
    override val referenceNameElement: VcCompositeElement
        get() = this

    override val referenceName: String
        get() = prefixInfix?.text?.removePrefix("`") ?: text

    override fun getName(): String = referenceName

    override fun textRepresentation(): String = referenceName

    override fun getReference(): VcReference = VcReferenceImpl<VcPrefixName>(this)
}

abstract class VcInfixImplMixin(node: ASTNode) : VcCompositeElementImpl(node), VcInfixName {
    override val referenceNameElement: VcCompositeElement
        get() = this

    override val referenceName: String
        get() = infixPrefix?.text?.removePrefix("`") ?: text

    override fun getName(): String = referenceName

    override fun getReference(): VcReference = VcReferenceImpl<VcInfixName>(this)
}

abstract class VcPostfixImplMixin(node: ASTNode) : VcCompositeElementImpl(node), VcPostfixName {
    override val referenceNameElement: VcCompositeElement
        get() = this

    override val referenceName: String
        get() = postfixInfix?.text?.removeSuffix("`") ?: postfixPrefix?.text?.removeSuffix("`") ?: text

    override fun getName(): String = referenceName

    override fun getReference(): VcReference = VcReferenceImpl<VcPostfixName>(this)
}
