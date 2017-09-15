package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import org.vclang.psi.VcStatCmd
import org.vclang.psi.isHiding

abstract class VcStatCmdImplMixin(node: ASTNode) : VcCompositeElementImpl(node),
                                                   VcStatCmd {
    /* TODO[abstract]
    override val scope: Scope
        get() = nsCmdRoot?.let {
            FilteredScope(
                    NamespaceScope(it.namespace),
                    refIdentifierList.mapNotNull { it.referenceName }.toSet(),
                    isHiding
            )
        } ?: EmptyScope
    */
}
