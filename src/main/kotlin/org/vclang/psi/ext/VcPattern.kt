package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import org.vclang.psi.VcPattern
import org.vclang.resolve.Namespace
import org.vclang.resolve.NamespaceProvider

abstract class VcPatternImplMixin(node: ASTNode) : VcCompositeElementImpl(node),
                                                   VcPattern {
    override val namespace: Namespace
        get() = NamespaceProvider.forPattern(this)
}
