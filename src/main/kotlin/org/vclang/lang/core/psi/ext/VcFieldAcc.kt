package org.vclang.lang.core.psi.ext

import com.intellij.lang.ASTNode
import org.vclang.lang.core.leftSiblings
import org.vclang.lang.core.psi.VcAtom
import org.vclang.lang.core.psi.VcFieldAcc
import org.vclang.lang.core.psi.VcNsCmdRoot
import org.vclang.lang.core.resolve.EmptyNamespace
import org.vclang.lang.core.resolve.Namespace
import org.vclang.lang.core.resolve.NamespaceScope
import org.vclang.lang.core.resolve.Scope

abstract class VcFieldAccImplMixin(node: ASTNode) : VcCompositeElementImpl(node),
                                                    VcFieldAcc {
    override val namespace: Namespace
        get() = findRoot()?.namespace ?: EmptyNamespace

    override val scope: Scope
        get() = NamespaceScope(namespace)

    override val referenceNameElement: VcCompositeElement?
        get() = refIdentifier

    override val referenceName: String?
        get() = referenceNameElement?.text

    override fun getName(): String? = referenceName

    private fun findRoot(): VcCompositeElement? {
        val prev = leftSiblings.firstOrNull {
            it is VcFieldAcc || it is VcAtom || it is VcNsCmdRoot
        }
        val name = when (prev) {
            is VcFieldAcc -> prev
            is VcAtom -> {
                prev.atomModuleCall?.moduleName?.moduleNamePartList?.lastOrNull()
                    ?: prev.literal?.prefixName
            }
            is VcNsCmdRoot -> {
                prev.moduleName?.moduleNamePartList?.lastOrNull()
                    ?: prev.refIdentifier
            }
            else -> null
        }
        return name?.reference?.resolve() as? VcCompositeElement
    }
}
