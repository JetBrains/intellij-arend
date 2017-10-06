package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.naming.reference.NamedUnresolvedReference
import com.jetbrains.jetpad.vclang.naming.reference.Referable
import com.jetbrains.jetpad.vclang.naming.scope.EmptyScope
import com.jetbrains.jetpad.vclang.naming.scope.Scope
import org.vclang.psi.*

abstract class VcFieldAccImplMixin(node: ASTNode) : VcCompositeElementImpl(node), VcFieldAcc {
    override val referenceNameElement: VcCompositeElement?
        get() = refIdentifier

    override val referenceName: String?
        get() = refIdentifier?.text

    override fun getName(): String? = referenceName

    override fun getData(): VcFieldAccImplMixin = this

    override fun getFieldReference(): Referable? = refIdentifier?.referenceName?.let { NamedUnresolvedReference(this, it) }

    override fun getProjIndex(): Int = number?.text?.toIntOrNull() ?: 0
}
