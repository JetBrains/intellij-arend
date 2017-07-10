package org.vclang.lang.core.psi.ext

import com.intellij.psi.PsiElement
import org.vclang.lang.core.resolve.ref.VcReference

interface VcReferenceElement : VcCompositeElement {
    val referenceNameElement: PsiElement?
    val referenceName: String?

    override fun getReference(): VcReference
}
