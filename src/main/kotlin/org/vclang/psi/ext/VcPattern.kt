package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import org.vclang.psi.VcPattern
import org.vclang.resolving.NamespaceProvider

abstract class VcPatternImplMixin(node: ASTNode) : VcCompositeElementImpl(node),
                                                   VcPattern {
    /* TODO[abstract]
    override val namespace: Namespace
        get() = NamespaceProvider.forPattern(this)
    */
}
