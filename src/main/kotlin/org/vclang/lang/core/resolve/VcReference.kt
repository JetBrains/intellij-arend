package org.vclang.lang.core.resolve

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import org.vclang.lang.core.psi.*
import org.vclang.lang.core.psi.ext.VcCompositeElement
import org.vclang.lang.core.psi.ext.VcReferenceElement
import org.vclang.lang.refactoring.VcNamesValidator

interface VcReference : PsiReference {

    override fun getElement(): VcCompositeElement

    override fun resolve(): VcCompositeElement?
}

abstract class VcReferenceBase<T : VcReferenceElement>(element: T)
    : PsiReferenceBase<T>(element, TextRange(0, element.textLength)),
      VcReference {

    override fun handleElementRename(newName: String): PsiElement {
        element.referenceNameElement?.let { doRename(it, newName) }
        return element
    }

    companion object {
        private fun doRename(name: PsiElement, newName: String) {
            if (!VcNamesValidator().isIdentifier(newName, name.project)) return
            val factory = VcPsiFactory(name.project)
            val newId = when (name) {
                is VcIdentifier -> factory.createIdentifier(newName.replace(".vc", ""))
                is VcPrefixName -> factory.createPrefixName(newName)
                is VcInfixName -> factory.createInfixName(newName)
                is VcPostfixName -> factory.createPostfixName(newName)
                else -> error("Unsupported identifier type for `$newName`")
            }
            name.replace(newId)
        }
    }
}
