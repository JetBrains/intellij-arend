package org.vclang.resolving

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.jetbrains.jetpad.vclang.naming.reference.ModuleReferable
import org.vclang.VcFileType
import org.vclang.VcIcons
import org.vclang.psi.*
import org.vclang.psi.ext.PsiModuleReferable
import org.vclang.psi.ext.VcCompositeElement
import org.vclang.psi.ext.VcReferenceElement
import org.vclang.refactoring.VcNamesValidator

interface VcReference : PsiReference {
    override fun getElement(): VcCompositeElement

    override fun resolve(): PsiElement?
}

open class VcReferenceImpl<T : VcReferenceElement>(element: T): PsiReferenceBase<T>(element, TextRange(0, element.textLength)), VcReference {
    override fun handleElementRename(newName: String): PsiElement {
        element.referenceNameElement?.let { doRename(it, newName) }
        return element
    }

    override fun getVariants(): Array<Any> = element.scope.elements.map {
        when (it) {
            is PsiNamedElement -> LookupElementBuilder.createWithIcon(it)
            is PsiModuleReferable ->
                it.modules.firstOrNull()?.let { if (it is VcFile)
                    LookupElementBuilder.create(it, it.textRepresentation()).withIcon(VcIcons.MODULE) else
                    LookupElementBuilder.createWithIcon(it) } ?:
                LookupElementBuilder.create(it, it.textRepresentation()).withIcon(VcIcons.DIRECTORY)
            else -> LookupElementBuilder.create(it, it.textRepresentation())
        }
    }.toTypedArray()

    override fun resolve(): PsiElement? {
        val ref = element.scope.resolveName(element.referenceName)
        return when (ref) {
            is PsiElement -> ref
            is PsiModuleReferable -> ref.modules.firstOrNull()
            else -> null
        }
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

open class VcPolyReferenceImpl<T : VcReferenceElement>(element: T): VcReferenceImpl<T>(element), PsiPolyVariantReference {
    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        val ref = element.scope.resolveName(element.referenceName)
        return when (ref) {
            is PsiElement -> arrayOf(PsiElementResolveResult(ref))
            is PsiModuleReferable -> ref.modules.map { PsiElementResolveResult(it) }.toTypedArray()
            else -> emptyArray()
        }
    }
}
