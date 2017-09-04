package org.vclang.psi.stubs

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.tree.IStubFileElementType
import org.vclang.VcLanguage
import org.vclang.psi.ext.VcCompositeElement

abstract class VcStubElementType<StubT : StubElement<*>, PsiT : VcCompositeElement>(
        debugName: String
) : IStubElementType<StubT, PsiT>(debugName, VcLanguage) {

    final override fun getExternalId(): String = "vclang.${super.toString()}"

    protected fun createStubIfParentIsStub(node: ASTNode): Boolean {
        val parent = node.treeParent
        val parentType = parent.elementType
        return (parentType is IStubElementType<*, *> && parentType.shouldCreateStub(parent)) ||
                parentType is IStubFileElementType<*>
    }
}
