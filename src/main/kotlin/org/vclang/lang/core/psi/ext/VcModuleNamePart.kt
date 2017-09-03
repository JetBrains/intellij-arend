package org.vclang.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import org.vclang.lang.VcFileType
import org.vclang.lang.core.getPsiDirectoryFor
import org.vclang.lang.core.getPsiFileFor
import org.vclang.lang.core.leftSiblings
import org.vclang.lang.core.psi.VcModuleNamePart
import org.vclang.lang.core.psi.VcNsCmdRoot
import org.vclang.lang.core.psi.contentRoot
import org.vclang.lang.core.psi.sourceRoot
import org.vclang.lang.core.resolve.EmptyNamespace
import org.vclang.lang.core.resolve.EmptyScope
import org.vclang.lang.core.resolve.Namespace
import org.vclang.lang.core.resolve.Scope
import org.vclang.lang.core.resolve.VcReference
import org.vclang.lang.core.resolve.VcReferenceBase
import org.vclang.lang.core.rightSiblings

abstract class VcModuleNamePartImplMixin(node: ASTNode) : VcCompositeElementImpl(node),
                                                          VcModuleNamePart {
    override val scope: Scope = EmptyScope

    override val referenceNameElement: VcCompositeElement
        get() = refIdentifier

    override val referenceName: String
        get() = referenceNameElement.text

    override fun getName(): String = referenceName

    override fun getReference(): VcReference = VcModuleNamePartReference()

    private inner class VcModuleNamePartReference
        : VcReferenceBase<VcModuleNamePart>(this@VcModuleNamePartImplMixin) {

        override fun resolve(): PsiElement? {
            val isDirectory = rightSiblings.any { it is VcModuleNamePart }
            val moduleName = element.text.let {
                if (isDirectory) it else "$it.${VcFileType.defaultExtension}"
            }
            val virtualFile = findRoot()?.findChild(moduleName)
            return if (isDirectory) {
                project.getPsiDirectoryFor(virtualFile)
            } else {
                project.getPsiFileFor(virtualFile)
            }
        }

        override fun getVariants(): Array<Any> {
            val root = findRoot()?: return emptyArray()
            return root.children.map { it.nameWithoutExtension }.toTypedArray()
        }

        private fun findRoot(): VirtualFile? {
            val prev = leftSiblings.firstOrNull { it is VcModuleNamePart }
            return if (prev is VcModuleNamePart) {
                val parent = prev.reference?.resolve()
                (parent as? PsiDirectory)?.virtualFile
            } else {
                sourceRoot ?: contentRoot
            }
        }
    }
}

abstract class VcNsCmdRootImplMixin(node: ASTNode) : VcCompositeElementImpl(node),
                                                     VcNsCmdRoot {
    override val namespace: Namespace
        get() {
            val name = moduleName?.moduleNamePartList?.lastOrNull() ?: refIdentifier
            val resolved = name?.reference?.resolve() as? VcCompositeElement
            resolved?.let { return it.namespace }
            return EmptyNamespace
        }
}
