package com.jetbrains.arend.ide.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import com.jetbrains.arend.ide.navigation.getPresentation
import com.jetbrains.arend.ide.psi.ArdDefIdentifier
import com.jetbrains.arend.ide.psi.ArdPsiFactory
import com.jetbrains.arend.ide.psi.childOfType
import com.jetbrains.arend.ide.psi.stubs.ArdNamedStub
import com.jetbrains.jetpad.vclang.module.ModulePath
import com.jetbrains.jetpad.vclang.naming.reference.ModuleReferable
import com.jetbrains.jetpad.vclang.naming.reference.TypedReferable

interface PsiReferable : ArdCompositeElement, PsiNameIdentifierOwner, NavigatablePsiElement, TypedReferable {
    val psiElementType: PsiElement?
        get() = null
}

class PsiModuleReferable(val modules: List<PsiFileSystemItem>, val modulePath: ModulePath) : ModuleReferable(modulePath)

abstract class PsiReferableImpl(node: ASTNode) : ArdCompositeElementImpl(node), PsiReferable {

    override fun getNameIdentifier(): ArdCompositeElement? = childOfType()

    override fun getName(): String? = nameIdentifier?.text

    override fun textRepresentation(): String = name ?: "_"

    override fun setName(name: String): PsiElement? {
        nameIdentifier?.replace(ArdPsiFactory(project).createDefIdentifier(name))
        return this
    }

    override fun getNavigationElement(): PsiElement = nameIdentifier ?: this

    override fun getTextOffset(): Int = nameIdentifier?.textOffset ?: super.getTextOffset()

    override fun getPresentation(): ItemPresentation = getPresentation(this)
}

abstract class PsiStubbedReferableImpl<StubT> : ArdStubbedElementImpl<StubT>, PsiReferable
        where StubT : ArdNamedStub, StubT : StubElement<*> {

    constructor(node: ASTNode) : super(node)

    constructor(stub: StubT, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getNameIdentifier(): ArdDefIdentifier? = childOfType()

    override fun getName(): String? = stub?.name ?: nameIdentifier?.referenceName

    override fun textRepresentation(): String = name ?: "_"

    override fun setName(name: String): PsiElement? {
        nameIdentifier?.replace(ArdPsiFactory(project).createDefIdentifier(name))
        return this
    }

    override fun getNavigationElement(): PsiElement = nameIdentifier ?: this

    override fun getTextOffset(): Int = nameIdentifier?.textOffset ?: super.getTextOffset()

    override fun getPresentation(): ItemPresentation = getPresentation(this)
}
