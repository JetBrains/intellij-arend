package org.vclang.lang.core.psi.ext

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.vclang.lang.core.resolve.*

interface VcCompositeElement : PsiElement {
    val namespace: Namespace
    val scope: Scope

    override fun getReference(): VcReference?
}

abstract class VcCompositeElementImpl(node: ASTNode) : ASTWrapperPsiElement(node),
                                                       VcCompositeElement {
    override val namespace: Namespace
        get() = EmptyNamespace

    override val scope: Scope
        get() {
            val parentScope = (parent as? VcCompositeElement)?.scope
            return if (parentScope != null) {
                OverridingScope(parentScope, NamespaceScope(namespace))
            } else {
                NamespaceScope(namespace)
            }
        }

    override fun getReference(): VcReference? = null
}
