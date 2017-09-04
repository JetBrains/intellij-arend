package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import org.vclang.VcFileType
import org.vclang.getPsiDirectoryFor
import org.vclang.getPsiFileFor
import org.vclang.psi.VcModuleNamePart
import org.vclang.psi.VcNsCmdRoot
import org.vclang.psi.contentRoot
import org.vclang.psi.leftSiblings
import org.vclang.psi.rightSiblings
import org.vclang.psi.sourceRoot
import org.vclang.resolve.EmptyNamespace
import org.vclang.resolve.EmptyScope
import org.vclang.resolve.Namespace
import org.vclang.resolve.Scope
import org.vclang.resolve.VcReference
import org.vclang.resolve.VcReferenceBase

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
