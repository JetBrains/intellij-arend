package org.vclang.lang.core.psi.ext

import com.intellij.psi.PsiElement

interface VcReferenceElement : VcCompositeElement {
    val referenceNameElement: PsiElement?
    val referenceName: String?
}
