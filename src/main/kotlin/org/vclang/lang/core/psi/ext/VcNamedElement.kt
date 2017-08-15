package org.vclang.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import org.vclang.lang.core.psi.VcIdentifier
import org.vclang.lang.core.psi.VcTypes

interface VcNamedElement : VcCompositeElement, PsiNameIdentifierOwner, NavigatablePsiElement

abstract class VcNamedElementImpl(node: ASTNode): VcCompositeElementImpl(node),
                                                  VcNamedElement {

    override fun getNameIdentifier(): VcIdentifier? = findChildByType(VcTypes.IDENTIFIER)

    override fun getName(): String? = nameIdentifier?.text

    override fun setName(name: String): PsiElement? = TODO()

    override fun getNavigationElement(): PsiElement = nameIdentifier ?: this

    override fun getTextOffset(): Int = nameIdentifier?.textOffset ?: super.getTextOffset()
}
