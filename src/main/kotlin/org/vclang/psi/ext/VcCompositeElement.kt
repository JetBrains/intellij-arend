package org.vclang.psi.ext

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.extapi.psi.StubBasedPsiElementBase
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import com.jetbrains.jetpad.vclang.error.SourceInfo
import com.jetbrains.jetpad.vclang.naming.scope.EmptyScope
import com.jetbrains.jetpad.vclang.naming.scope.Scope
import org.vclang.psi.VcFile
import org.vclang.resolving.*

interface VcCompositeElement : PsiElement, SourceInfo {
    val scope: Scope
    override fun getReference(): VcReference?
}

private fun VcCompositeElement.moduleTextRepresentationImpl(): String? = (containingFile as? VcFile)?.name

private fun VcCompositeElement.positionTextRepresentationImpl(): String? {
    val document = PsiDocumentManager.getInstance(project).getDocument(containingFile ?: return null) ?: return null
    val offset = textOffset
    val line = document.getLineNumber(offset)
    val column = offset - document.getLineStartOffset(line)
    return (line + 1).toString() + ":" + (column + 1).toString()
}

abstract class VcCompositeElementImpl(node: ASTNode) : ASTWrapperPsiElement(node), VcCompositeElement  {
    override val scope: Scope
        get() = (parent as? VcCompositeElement)?.scope ?: EmptyScope.INSTANCE

    override fun getReference(): VcReference? = null

    override fun moduleTextRepresentation(): String? = moduleTextRepresentationImpl()

    override fun positionTextRepresentation(): String? = positionTextRepresentationImpl()
}

abstract class VcStubbedElementImpl<StubT : StubElement<*>> : StubBasedPsiElementBase<StubT>, VcCompositeElement {
    constructor(node: ASTNode) : super(node)

    constructor(stub: StubT, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override val scope: Scope
        get() = (parent as? VcCompositeElement)?.scope ?: EmptyScope.INSTANCE

    override fun getReference(): VcReference? = null

    override fun toString(): String = "${javaClass.simpleName}($elementType)"

    override fun moduleTextRepresentation(): String? = moduleTextRepresentationImpl()

    override fun positionTextRepresentation(): String? = positionTextRepresentationImpl()
}
