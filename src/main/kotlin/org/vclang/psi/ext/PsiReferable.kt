package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import com.jetbrains.jetpad.vclang.module.ModulePath
import com.jetbrains.jetpad.vclang.naming.reference.ModuleReferable
import com.jetbrains.jetpad.vclang.naming.reference.Referable
import org.vclang.navigation.getPresentation
import org.vclang.psi.VcDefIdentifier
import org.vclang.psi.VcPsiFactory
import org.vclang.psi.childOfType
import org.vclang.psi.stubs.VcNamedStub

interface PsiReferable : VcCompositeElement, PsiNameIdentifierOwner, NavigatablePsiElement, Referable

class PsiModuleReferable(val modules: List<PsiFileSystemItem>, val modulePath: ModulePath): ModuleReferable(modulePath)

abstract class PsiReferableImpl(node: ASTNode) : VcCompositeElementImpl(node), PsiReferable {

    override fun getNameIdentifier(): VcCompositeElement? = childOfType()

    override fun getName(): String? = nameIdentifier?.text

    override fun textRepresentation(): String = name ?: "_"

    override fun setName(name: String): PsiElement? {
        nameIdentifier?.replace(VcPsiFactory(project).createDefIdentifier(name))
        return this
    }

    override fun getNavigationElement(): PsiElement = nameIdentifier ?: this

    override fun getTextOffset(): Int = nameIdentifier?.textOffset ?: super.getTextOffset()

    override fun getPresentation(): ItemPresentation = getPresentation(this)
}

abstract class PsiStubbedReferableImpl<StubT> : VcStubbedElementImpl<StubT>, PsiReferable
where StubT : VcNamedStub, StubT : StubElement<*> {

    constructor(node: ASTNode) : super(node)

    constructor(stub: StubT, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getNameIdentifier(): VcDefIdentifier? = childOfType()

    override fun getName(): String? = stub?.name ?: nameIdentifier?.referenceName

    override fun textRepresentation(): String = name ?: "_"

    override fun setName(name: String): PsiElement? {
        nameIdentifier?.replace(VcPsiFactory(project).createDefIdentifier(name))
        return this
    }

    override fun getNavigationElement(): PsiElement = nameIdentifier ?: this

    override fun getTextOffset(): Int = nameIdentifier?.textOffset ?: super.getTextOffset()

    override fun getPresentation(): ItemPresentation = getPresentation(this)
}
