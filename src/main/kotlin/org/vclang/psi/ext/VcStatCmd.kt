package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import org.vclang.psi.VcStatCmd
import org.vclang.psi.isHiding
import org.vclang.resolve.EmptyScope
import org.vclang.resolve.FilteredScope
import org.vclang.resolve.NamespaceScope
import org.vclang.resolve.Scope

abstract class VcStatCmdImplMixin(node: ASTNode) : VcCompositeElementImpl(node),
                                                   VcStatCmd {
    override val scope: Scope
        get() = nsCmdRoot?.let {
            FilteredScope(
                NamespaceScope(it.namespace),
                refIdentifierList.mapNotNull { it.referenceName }.toSet(),
                isHiding
            )
        } ?: EmptyScope
}
