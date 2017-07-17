package org.vclang.lang.core.psi.ext

import com.intellij.lang.ASTNode
import org.vclang.lang.core.psi.VcPattern
import org.vclang.lang.core.resolve.Namespace
import org.vclang.lang.core.resolve.NamespaceProvider

abstract class VcPatternImplMixin(node: ASTNode) : VcCompositeElementImpl(node),
                                                   VcPattern {
    override val namespace: Namespace
        get() = NamespaceProvider.forPattern(this)
}
