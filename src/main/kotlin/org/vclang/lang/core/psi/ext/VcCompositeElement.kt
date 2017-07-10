package org.vclang.lang.core.psi.ext

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.vclang.lang.core.resolve.ref.VcReference

interface VcCompositeElement : PsiElement {
    override fun getReference(): VcReference?
}

abstract class VcCompositeElementImpl(node: ASTNode)
    : ASTWrapperPsiElement(node), VcCompositeElement {
    override fun getReference(): VcReference? = null
}
