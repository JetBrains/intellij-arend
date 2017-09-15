package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.naming.scope.EmptyScope
import com.jetbrains.jetpad.vclang.naming.scope.Scope
import org.vclang.psi.VcAtom
import org.vclang.psi.VcFieldAcc
import org.vclang.psi.VcNsCmdRoot
import org.vclang.psi.leftSiblings

abstract class VcFieldAccImplMixin(node: ASTNode) : VcCompositeElementImpl(node),
                                                    VcFieldAcc {
    override val scope: Scope
        get() = findRoot()?.scope ?: EmptyScope.INSTANCE

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
