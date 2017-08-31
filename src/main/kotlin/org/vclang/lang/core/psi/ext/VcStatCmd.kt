package org.vclang.lang.core.psi.ext

import com.intellij.lang.ASTNode
import org.vclang.lang.core.psi.VcStatCmd
import org.vclang.lang.core.psi.isHiding
import org.vclang.lang.core.resolve.*

abstract class VcStatCmdImplMixin(node: ASTNode) : VcCompositeElementImpl(node),
                                                   VcStatCmd {
    override val scope: Scope
        get() {
            val parentScope = (parent as? VcCompositeElement)?.scope
            val filteredScope = nsCmdRoot?.let {
                FilteredScope(
                        NamespaceScope(it.namespace),
                        identifierList.map { it.text }.toSet(),
                        isHiding
                )
            }
            return when {
                parentScope != null && filteredScope != null ->
                    OverridingScope(parentScope, filteredScope)
                parentScope != null -> parentScope
                filteredScope != null -> filteredScope
                else -> EmptyScope
            }
        }
}
