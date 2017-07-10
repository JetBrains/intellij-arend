package org.vclang.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.vclang.lang.core.psi.VcIdentifier
import org.vclang.lang.core.resolve.ref.VcIdentifierReferenceImpl
import org.vclang.lang.core.resolve.ref.VcReference

abstract class VcIdentifierImplMixin(node: ASTNode) : VcCompositeElementImpl(node), VcIdentifier {
    override val referenceNameElement: PsiElement
        get() = this

    override val referenceName: String
        get() = text

    override fun getReference(): VcReference = VcIdentifierReferenceImpl(this)
}
