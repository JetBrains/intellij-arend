package org.vclang.resolve

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import org.vclang.VcFileType
import org.vclang.psi.*
import org.vclang.psi.ext.VcCompositeElement
import org.vclang.psi.ext.VcReferenceElement
import org.vclang.refactoring.VcNamesValidator

interface VcReference : PsiReference {

    override fun getElement(): VcCompositeElement

    override fun resolve(): PsiElement?
}

abstract class VcReferenceBase<T : VcReferenceElement>(element: T)
    : PsiReferenceBase<T>(element, TextRange(0, element.textLength)),
      VcReference {

    override fun handleElementRename(newName: String): PsiElement {
        element.referenceNameElement?.let { doRename(it, newName) }
        return element
    }

    companion object {
        private fun doRename(oldNameIdentifier: PsiElement, rawName: String) {
            val name = rawName.removeSuffix('.' + VcFileType.defaultExtension)
            if (!VcNamesValidator().isIdentifier(name, oldNameIdentifier.project)) return
            val factory = VcPsiFactory(oldNameIdentifier.project)
            val newNameIdentifier = when (oldNameIdentifier) {
                is VcDefIdentifier -> factory.createDefIdentifier(name)
                is VcRefIdentifier -> factory.createRefIdentifier(name)
                is VcPrefixName -> factory.createPrefixName(name)
                is VcInfixName -> factory.createInfixName(name)
                is VcPostfixName -> factory.createPostfixName(name)
                else -> error("Unsupported identifier type for `$name`")
            }
            oldNameIdentifier.replace(newNameIdentifier)
        }
    }
}
