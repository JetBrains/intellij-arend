package org.vclang.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import org.vclang.ide.presentation.getPresentation
import org.vclang.lang.core.psi.VcPsiFactory
import org.vclang.lang.core.psi.VcTypes
import org.vclang.lang.core.stubs.VcNamedStub

interface VcNamedElement : VcCompositeElement, PsiNameIdentifierOwner, NavigatablePsiElement

abstract class VcNamedElementImpl(node: ASTNode): VcCompositeElementImpl(node),
                                                  VcNamedElement {

    override fun getNameIdentifier(): VcCompositeElement? = findChildByType(VcTypes.IDENTIFIER)

    override fun getName(): String? = nameIdentifier?.text

    override fun setName(name: String): PsiElement? {
        nameIdentifier?.replace(VcPsiFactory(project).createIdentifier(name))
        return this
    }

    override fun getNavigationElement(): PsiElement = nameIdentifier ?: this

    override fun getTextOffset(): Int = nameIdentifier?.textOffset ?: super.getTextOffset()
}

abstract class VcStubbedNamedElementImpl<StubT> : VcStubbedElementImpl<StubT>,
                                                  VcNamedElement
where StubT : VcNamedStub, StubT : StubElement<*> {

    constructor(node: ASTNode) : super(node)

    constructor(stub: StubT, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getNameIdentifier(): VcCompositeElement? = findChildByType(VcTypes.IDENTIFIER)

    override fun getName(): String? = stub?.name ?: nameIdentifier?.text

    override fun setName(name: String): PsiElement? {
        nameIdentifier?.replace(VcPsiFactory(project).createIdentifier(name))
        return this
    }

    override fun getTextOffset(): Int = nameIdentifier?.textOffset ?: super.getTextOffset()

    override fun getPresentation(): ItemPresentation = getPresentation(this)
}
