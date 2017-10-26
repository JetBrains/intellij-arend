package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.module.ModulePath
import com.jetbrains.jetpad.vclang.naming.reference.NamedUnresolvedReference
import com.jetbrains.jetpad.vclang.naming.reference.Referable
import com.jetbrains.jetpad.vclang.term.ChildGroup
import com.jetbrains.jetpad.vclang.term.NamespaceCommand
import org.vclang.VcFileType
import org.vclang.module.util.sourceRoots
import org.vclang.psi.*

abstract class VcStatCmdImplMixin(node: ASTNode) : VcCompositeElementImpl(node), VcStatCmd {
    override fun getKind(): NamespaceCommand.Kind {
        val cmd = nsCmd
        if (cmd.importKw != null) return NamespaceCommand.Kind.IMPORT
        if (cmd.openKw != null) return NamespaceCommand.Kind.OPEN
        if (cmd.exportKw != null) return NamespaceCommand.Kind.EXPORT
        error("Incorrect expression: namespace command")
    }

    override fun getPath(): List<String> = longName?.let { listOf(it.prefixName.referenceName) + it.refIdentifierList.map { it.referenceName } } ?: emptyList()

    override fun getImportedPath(): List<PsiModuleReferable> {
        val path = path
        return if (nsCmd.importKw != null) {
            var dirs = module?.sourceRoots ?: emptyList()
            path.withIndex().map { (i, name) ->
                val modulePath = ModulePath(path.subList(0, i + 1))
                if (i < path.size - 1) {
                    dirs = dirs.mapNotNull { it.findSubdirectory(name) }
                    PsiModuleReferable(dirs, modulePath)
                } else {
                    PsiModuleReferable(dirs.mapNotNull { it.findFile(name + "." + VcFileType.defaultExtension) as? VcFile }, modulePath)
                }
            }
        } else {
            emptyList()
        }
    }

    override fun isUsing(): Boolean {
        val using = nsUsing
        return using == null || using.usingKw != null
    }

    override fun getOpenedReferences(): List<VcNsId> = nsUsing?.nsIdList ?: emptyList()

    override fun getHiddenReferences(): List<Referable> = refIdentifierList.map { NamedUnresolvedReference(it, it.text) }

    override fun getParentGroup(): ChildGroup? = parent.ancestors.filterIsInstance<ChildGroup>().firstOrNull()
}
