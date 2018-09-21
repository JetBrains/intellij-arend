package org.arend.psi.stubs

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.tree.IStubFileElementType
import org.arend.ArendLanguage
import org.arend.psi.ext.ArendCompositeElement

abstract class ArendStubElementType<StubT : StubElement<*>, PsiT : ArendCompositeElement>(
        debugName: String
) : IStubElementType<StubT, PsiT>(debugName, ArendLanguage.INSTANCE) {

    final override fun getExternalId(): String = "arend.${super.toString()}"

    protected fun createStubIfParentIsStub(node: ASTNode): Boolean {
        val parent = node.treeParent
        val parentType = parent.elementType
        return (parentType is IStubElementType<*, *> && parentType.shouldCreateStub(parent)) ||
                parentType is IStubFileElementType<*>
    }
}
