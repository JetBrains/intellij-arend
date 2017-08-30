package org.vclang.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import org.vclang.lang.VcFileType
import org.vclang.lang.core.getPsiFor
import org.vclang.lang.core.psi.*
import org.vclang.lang.core.resolve.EmptyNamespace
import org.vclang.lang.core.resolve.Namespace
import org.vclang.lang.core.resolve.VcReference
import org.vclang.lang.core.resolve.VcReferenceBase

abstract class VcModuleNameImplMixin(node: ASTNode) : VcCompositeElementImpl(node),
                                                      VcModuleName {
    override val namespace: Namespace
        get() {
            val module = reference.resolve()
            return module?.namespace ?: EmptyNamespace
        }

    override val referenceNameElement: VcCompositeElement
        get() = this

    override val referenceName: String
        get() = text

    override fun getName(): String = referenceName

    override fun getReference(): VcReference = object : VcReferenceBase<VcModuleName>(
            this@VcModuleNameImplMixin
    ) {

        override fun resolve(): VcCompositeElement? {
            val path = parseModulePath(modulePath.text)
            var virtualFile = sourceRoot ?: contentRoot
            for (dir in path.dropLast(1)) {
                virtualFile = virtualFile?.findChild(dir)
            }
            virtualFile = virtualFile?.findChild("${path.last()}.${VcFileType.defaultExtension}")
            return project.getPsiFor(virtualFile) as? VcCompositeElement
        }

        override fun getVariants(): Array<Any> {
            val project = this@VcModuleNameImplMixin.project
            val virtualFiles = FilenameIndex.getAllFilesByExt(project, VcFileType.defaultExtension)
            val psiFiles = virtualFiles.map { PsiManager.getInstance(project).findFile(it) }
            val modules = psiFiles.filterIsInstance<VcFile>()
            return modules.map { it.relativeModulePath }.map { it.toString() }.toTypedArray()
        }
    }

    private fun parseModulePath(rawModulePath: String): List<String> =
            rawModulePath.split("::").filter { it.isNotEmpty() }
}

abstract class VcNsCmdRootImplMixin(node: ASTNode) : VcCompositeElementImpl(node),
                                                     VcNsCmdRoot {
    override val namespace: Namespace
        get() = moduleName?.namespace ?: EmptyNamespace
}
