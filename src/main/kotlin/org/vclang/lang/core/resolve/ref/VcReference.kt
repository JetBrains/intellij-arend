package org.vclang.lang.core.resolve.ref

import com.intellij.psi.PsiPolyVariantReference
import org.vclang.lang.core.psi.ext.VcCompositeElement


interface VcReference : PsiPolyVariantReference {

    override fun getElement(): VcCompositeElement

    override fun resolve(): VcCompositeElement?
}
