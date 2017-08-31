package org.vclang.lang.core.psi.ext

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.extapi.psi.StubBasedPsiElementBase
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import org.vclang.lang.core.resolve.*

interface VcCompositeElement : PsiElement {
    val namespace: Namespace
    val scope: Scope

    override fun getReference(): VcReference?
}

abstract class VcCompositeElementImpl(node: ASTNode) : ASTWrapperPsiElement(node),
                                                       VcCompositeElement {
    override val namespace: Namespace = EmptyNamespace

    override val scope: Scope
        get() {
            val parentScope = (parent as? VcCompositeElement)?.scope
            val namespaceScope = NamespaceScope(namespace)
            parentScope?.let { return OverridingScope(it, namespaceScope) }
            return namespaceScope
        }

    override fun getReference(): VcReference? = null
}

abstract class VcStubbedElementImpl<StubT : StubElement<*>> : StubBasedPsiElementBase<StubT>,
                                                              VcCompositeElement {
    override val namespace: Namespace = EmptyNamespace

    override val scope: Scope
        get() {
            val parentScope = (parent as? VcCompositeElement)?.scope
            val namespaceScope = NamespaceScope(namespace)
            parentScope?.let { return OverridingScope(it, namespaceScope) }
            return namespaceScope
        }

    constructor(node: ASTNode) : super(node)

    constructor(stub: StubT, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun toString(): String = "${javaClass.simpleName}($elementType)"

    override fun getReference(): VcReference? = null
}
