package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.module.ModulePath
import com.jetbrains.jetpad.vclang.naming.reference.LongUnresolvedReference
import com.jetbrains.jetpad.vclang.naming.reference.ModuleUnresolvedReference
import com.jetbrains.jetpad.vclang.naming.reference.NamedUnresolvedReference
import com.jetbrains.jetpad.vclang.naming.reference.Referable
import com.jetbrains.jetpad.vclang.term.NamespaceCommand
import org.vclang.psi.VcStatCmd

abstract class VcStatCmdImplMixin(node: ASTNode) : VcCompositeElementImpl(node), VcStatCmd {
    override fun getKind(): NamespaceCommand.Kind =
            if (nsCmd.exportKw != null) NamespaceCommand.Kind.EXPORT else NamespaceCommand.Kind.OPEN

    override fun getGroupReference(): Referable {
        val moduleName = nsCmdRoot.moduleName
        val path = refIdentifierList.map { it.text }
        return if (moduleName != null)
            ModuleUnresolvedReference(nsCmdRoot, ModulePath(moduleName.moduleNamePartList.map { it.refIdentifier.text }), path) else
            LongUnresolvedReference.make(nsCmdRoot, nsCmdRoot.refIdentifier!!.text, path)
    }

    override fun isHiding(): Boolean = hidingKw != null

    override fun getSubgroupReferences(): List<Referable> = refIdentifierList.map { NamedUnresolvedReference(it, it.text) }

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
