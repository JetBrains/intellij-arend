package org.vclang.lang.core.psi.ext

import com.intellij.lang.ASTNode
import org.vclang.lang.core.psi.VcStatCmd
import org.vclang.lang.core.resolve.*

abstract class VcStatCmdImplMixin(node: ASTNode) : VcCompositeElementImpl(node),
                                                   VcStatCmd {
    override val scope: Scope
        get() = nsCmdRoot?.let {
            FilteredScope(
                    NamespaceScope(it.namespace),
                    identifierList.map { it.text }.toSet(),
                    hidingKw != null
            )
        } ?: EmptyScope
}
