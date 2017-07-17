package org.vclang.lang.core.resolve

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import org.vclang.lang.core.psi.ext.VcCompositeElement
import org.vclang.lang.core.psi.ext.VcReferenceElement

interface VcReference : PsiReference {
    override fun getElement(): VcCompositeElement

    override fun resolve(): VcCompositeElement?
}

abstract class VcReferenceBase<T : VcReferenceElement>(element: T)
    : PsiReferenceBase<T>(element, TextRange(0, element.textLength)),
      VcReference
