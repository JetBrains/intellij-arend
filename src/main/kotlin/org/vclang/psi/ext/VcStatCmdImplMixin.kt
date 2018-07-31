package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.naming.reference.NamedUnresolvedReference
import com.jetbrains.jetpad.vclang.naming.reference.Referable
import com.jetbrains.jetpad.vclang.term.ChildNamespaceCommand
import com.jetbrains.jetpad.vclang.term.NamespaceCommand
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import com.jetbrains.jetpad.vclang.term.group.ChildGroup
import org.vclang.psi.VcNsId
import org.vclang.psi.VcStatCmd
import org.vclang.psi.ancestors

abstract class VcStatCmdImplMixin(node: ASTNode) : VcSourceNodeImpl(node), VcStatCmd, ChildNamespaceCommand {
    override fun getKind(): NamespaceCommand.Kind {
        if (importKw != null) return NamespaceCommand.Kind.IMPORT
        if (openKw != null) return NamespaceCommand.Kind.OPEN
        error("Incorrect expression: namespace command")
    }

    override fun getPath(): List<String> = longName?.let { it.refIdentifierList.map { it.referenceName } } ?: emptyList()

    override fun isUsing(): Boolean {
        val using = nsUsing
        return using == null || using.usingKw != null
    }

    override fun getOpenedReferences(): List<VcNsId> = nsUsing?.nsIdList ?: emptyList()

    override fun getHiddenReferences(): List<Referable> = refIdentifierList.map { NamedUnresolvedReference(it, it.text) }

    override fun getParentGroup(): ChildGroup? = parent.ancestors.filterIsInstance<ChildGroup>().firstOrNull()

    override fun getOpenedReference(): Abstract.LongReference? = longName
}
