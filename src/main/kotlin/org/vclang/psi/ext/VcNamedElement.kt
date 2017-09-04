package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import org.vclang.navigation.getPresentation
import org.vclang.psi.VcDefIdentifier
import org.vclang.psi.VcPsiFactory
import org.vclang.psi.childOfType
import org.vclang.psi.stubs.VcNamedStub

interface VcNamedElement : VcCompositeElement, PsiNameIdentifierOwner, NavigatablePsiElement

abstract class VcNamedElementImpl(node: ASTNode): VcCompositeElementImpl(node),
    VcNamedElement {

    override fun getNameIdentifier(): VcCompositeElement? = childOfType()

    override fun getName(): String? = nameIdentifier?.text

    override fun setName(name: String): PsiElement? {
        nameIdentifier?.replace(VcPsiFactory(project).createDefIdentifier(name))
        return this
    }

    override fun getNavigationElement(): PsiElement = nameIdentifier ?: this

    override fun getTextOffset(): Int = nameIdentifier?.textOffset ?: super.getTextOffset()

    override fun getPresentation(): ItemPresentation = getPresentation(this)
}

abstract class VcStubbedNamedElementImpl<StubT> : VcStubbedElementImpl<StubT>,
    VcNamedElement
where StubT : VcNamedStub, StubT : StubElement<*> {

    constructor(node: ASTNode) : super(node)

    constructor(stub: StubT, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getNameIdentifier(): VcDefIdentifier? = childOfType()

    override fun getName(): String? = nameIdentifier?.text

    override fun setName(name: String): PsiElement? {
        nameIdentifier?.replace(VcPsiFactory(project).createDefIdentifier(name))
        return this
    }

    override fun getNavigationElement(): PsiElement = nameIdentifier ?: this

    override fun getTextOffset(): Int = nameIdentifier?.textOffset ?: super.getTextOffset()

    override fun getPresentation(): ItemPresentation = getPresentation(this)
}
