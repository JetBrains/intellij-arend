package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.naming.reference.NamedUnresolvedReference
import com.jetbrains.jetpad.vclang.naming.reference.Referable
import com.jetbrains.jetpad.vclang.term.ChildGroup
import com.jetbrains.jetpad.vclang.term.NamespaceCommand
import org.vclang.psi.VcNsId
import org.vclang.psi.VcStatCmd
import org.vclang.psi.ancestors

abstract class VcStatCmdImplMixin(node: ASTNode) : VcCompositeElementImpl(node), VcStatCmd {
    override fun getKind(): NamespaceCommand.Kind =
            if (nsCmd.exportKw != null) NamespaceCommand.Kind.EXPORT else NamespaceCommand.Kind.OPEN

    override fun getGroupReference(): Referable? = longName?.referent

    override fun isUsing(): Boolean {
        val using = nsUsing
        return using == null || using.usingKw != null
    }

    override fun getOpenedReferences(): List<VcNsId> = nsUsing?.nsIdList ?: emptyList()

    override fun getHiddenReferences(): List<Referable> = refIdentifierList.map { NamedUnresolvedReference(it, it.text) }

    override fun getParentGroup(): ChildGroup? = parent.ancestors.filterIsInstance<ChildGroup>().firstOrNull()
}
