package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.naming.reference.NamedUnresolvedReference
import com.jetbrains.jetpad.vclang.naming.reference.Referable
import com.jetbrains.jetpad.vclang.naming.scope.EmptyScope
import com.jetbrains.jetpad.vclang.naming.scope.Scope
import org.vclang.psi.*

abstract class VcFieldAccImplMixin(node: ASTNode) : VcCompositeElementImpl(node), VcFieldAcc {
    // TODO[abstract]: Check this
    override val scope: Scope
        get() = findRoot()?.scope ?: EmptyScope.INSTANCE

    override val referenceNameElement: VcCompositeElement?
        get() = refIdentifier

    override val referenceName: String?
        get() = referenceNameElement?.text

    override fun getName(): String? = referenceName

    private fun findRoot(): VcCompositeElement? {
        val prev = leftSiblings.firstOrNull {
            it is VcFieldAcc || it is VcAtom || it is VcLiteral || it is VcNsCmdRoot
        }
        val name = when (prev) {
            is VcLiteral -> prev.prefixName
            is VcAtom -> prev.atomModuleCall?.moduleName?.moduleNamePartList?.lastOrNull()
            is VcNsCmdRoot -> prev.moduleName?.moduleNamePartList?.lastOrNull() ?: prev.refIdentifier
            else -> prev
        }
        return name?.reference?.resolve() as? VcCompositeElement
    }

    override fun getData(): VcFieldAccImplMixin = this

    override fun getFieldReference(): Referable? = refIdentifier?.referenceName?.let { NamedUnresolvedReference(this, it) }

    override fun getProjIndex(): Int = number?.text?.toIntOrNull() ?: 0
}
