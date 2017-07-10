package org.vclang.lang.core.psi

import com.intellij.lang.ASTNode
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import org.vclang.lang.core.psi.ext.VcCompositeElement
import org.vclang.lang.core.psi.ext.VcCompositeElementImpl

interface VcNamedElement : VcCompositeElement, PsiNameIdentifierOwner, NavigatablePsiElement

abstract class VcNamedElementImpl(node: ASTNode)
    : VcCompositeElementImpl(node), VcNamedElement {

    override fun getNameIdentifier(): PsiElement? = findChildByType(VcTypes.IDENTIFIER) ?: findChildByType(VcTypes.ID)

    override fun getName(): String? = nameIdentifier?.text

    override fun setName(name: String): PsiElement? = TODO()

    override fun getNavigationElement(): PsiElement = nameIdentifier ?: this

    override fun getTextOffset(): Int = nameIdentifier?.textOffset ?: super.getTextOffset()
}
