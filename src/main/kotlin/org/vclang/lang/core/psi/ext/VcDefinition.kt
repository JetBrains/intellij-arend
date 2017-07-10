package org.vclang.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.vclang.lang.core.psi.VcDefinition
import org.vclang.lang.core.psi.VcNamedElement
import org.vclang.lang.core.resolve.ref.VcDefinitionReferenceImpl
import org.vclang.lang.core.resolve.ref.VcReference

abstract class VcDefinitionImplMixin(node: ASTNode) : VcCompositeElementImpl(node), VcDefinition {
    override val referenceNameElement: PsiElement?
        get() = (firstChild as? VcNamedElement)?.nameIdentifier

    override val referenceName: String?
        get() = referenceNameElement?.text

    override fun getReference(): VcReference = VcDefinitionReferenceImpl(this)
}
