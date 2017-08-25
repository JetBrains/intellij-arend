package org.vclang.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import org.vclang.lang.VcFileType
import org.vclang.lang.core.psi.VcFile
import org.vclang.lang.core.psi.VcModuleName
import org.vclang.lang.core.psi.VcNsCmdRoot
import org.vclang.lang.core.resolve.*

abstract class VcModulePathImplMixin(node: ASTNode) : VcCompositeElementImpl(node),
                                                      VcModuleName {
    override val namespace: Namespace
        get() = NamespaceProvider.forModulePath(parseModulePath(modulePath.text), project)

    override val referenceNameElement: VcCompositeElement
        get() = this

    override val referenceName: String
        get() = text

    override fun getName(): String = referenceName

    override fun getReference(): VcReference = object : VcReferenceBase<VcModuleName>(
            this@VcModulePathImplMixin
    ) {

        override fun resolve(): VcCompositeElement? {
            val path = parseModulePath(modulePath.text)
            val file = FilenameIndex.getVirtualFilesByName(
                    project,
                    "${path.last()}.${VcFileType.defaultExtension}",
                    GlobalSearchScope.allScope(project)
            ).first()
            return PsiManager.getInstance(project).findFile(file)?.firstChild as? VcCompositeElement
        }

        override fun getVariants(): Array<Any> {
            val project = this@VcModulePathImplMixin.project
            val virtualFiles = FilenameIndex.getAllFilesByExt(project, VcFileType.defaultExtension)
            val psiFiles = virtualFiles.map { PsiManager.getInstance(project).findFile(it) }
            val modules = psiFiles.filterIsInstance<VcFile>()
            return modules.map { it.modulePath }.map { it.toString() }.toTypedArray()
        }
    }

    private fun parseModulePath(rawModulePath: String): List<String> =
            rawModulePath.split("::").filter { it.isNotEmpty() }
}

abstract class VcNsCmdRootImplMixin(node: ASTNode) : VcCompositeElementImpl(node),
                                                     VcNsCmdRoot {
    override val namespace: Namespace
        get() = identifier?.let { NamespaceProvider.forModulePath(listOf(it.text), project) }
                ?: moduleName?.namespace
                ?: EmptyNamespace
}
