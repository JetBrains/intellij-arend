package org.vclang.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import org.vclang.lang.VcFileType
import org.vclang.lang.core.psi.VcModulePath
import org.vclang.lang.core.psi.VcNsCmdRoot
import org.vclang.lang.core.resolve.*

abstract class VcModulePathImplMixin(node: ASTNode) : VcCompositeElementImpl(node),
                                                      VcModulePath {
    override val namespace: Namespace
        get() {
            val path = pathPartList.map { it.text }
            return NamespaceProvider.forModulePath(path, project)
        }

    override val referenceNameElement: PsiElement
        get() = this

    override val referenceName: String
        get() = text

    override fun getName(): String = text

    override fun getReference(): VcReference = object : VcReferenceBase<VcModulePath>(this) {

        override fun resolve(): VcCompositeElement? {
            val path = pathPartList.map { it.text }
            val file = FilenameIndex.getVirtualFilesByName(
                    project,
                    "${path.last()}.${VcFileType.defaultExtension}",
                    GlobalSearchScope.allScope(project)
            ).first()
            return PsiManager.getInstance(project).findFile(file)?.firstChild as? VcCompositeElement
        }

        override fun getVariants(): Array<Any> = arrayOf()
    }
}

abstract class VcNsCmdRootImplMixin(node: ASTNode) : VcCompositeElementImpl(node),
                                                     VcNsCmdRoot {
    override val namespace: Namespace
        get() = identifier?.let { NamespaceProvider.forModulePath(listOf(it.text), project) }
                ?: modulePath?.namespace
                ?: EmptyNamespace
}
