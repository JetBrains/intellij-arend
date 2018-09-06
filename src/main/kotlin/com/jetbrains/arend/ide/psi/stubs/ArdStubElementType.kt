package com.jetbrains.arend.ide.psi.stubs

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.tree.IStubFileElementType
import com.jetbrains.arend.ide.psi.ext.ArdCompositeElement

abstract class ArdStubElementType<StubT : StubElement<*>, PsiT : ArdCompositeElement>(
        debugName: String
) : IStubElementType<StubT, PsiT>(debugName, com.jetbrains.arend.ide.ArdLanguage.INSTANCE) {

    final override fun getExternalId(): String = "vclang.${super.toString()}"

    protected fun createStubIfParentIsStub(node: ASTNode): Boolean {
        val parent = node.treeParent
        val parentType = parent.elementType
        return (parentType is IStubElementType<*, *> && parentType.shouldCreateStub(parent)) ||
                parentType is IStubFileElementType<*>
    }
}
