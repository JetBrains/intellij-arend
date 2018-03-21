package org.vclang.resolving

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.jetbrains.jetpad.vclang.naming.reference.RedirectingReferable
import org.vclang.VcFileType
import org.vclang.VcIcons
import org.vclang.psi.*
import org.vclang.psi.ext.PsiModuleReferable
import org.vclang.psi.ext.PsiReferable
import org.vclang.psi.ext.VcCompositeElement
import org.vclang.psi.ext.VcReferenceElement
import org.vclang.refactoring.VcNamesValidator

interface VcReference : PsiReference {
    override fun getElement(): VcCompositeElement

    override fun resolve(): PsiElement?
}

open class VcDefReferenceImpl<T : VcReferenceElement>(element: T): PsiReferenceBase<T>(element, TextRange(0, element.textLength)), VcReference {
    override fun handleElementRename(newName: String): PsiElement {
        element.referenceNameElement?.let { doRename(it, newName) }
        return element
    }

    override fun getVariants(): Array<Any> = emptyArray()

    override fun resolve(): PsiElement = element.parent as? PsiReferable ?: element
}

open class VcPatternDefReferenceImpl<T : VcDefIdentifier>(element: T): VcReferenceImpl<T>(element) {
    override fun resolve(): PsiElement = super.resolve() ?: element
}

open class VcReferenceImpl<T : VcReferenceElement>(element: T): PsiReferenceBase<T>(element, TextRange(0, element.textLength)), VcReference {
    override fun handleElementRename(newName: String): PsiElement {
        element.referenceNameElement?.let { doRename(it, newName) }
        return element
    }

    override fun getVariants(): Array<Any> = element.scope.elements.map {
        val ref = (it as? RedirectingReferable)?.originalReferable ?: it
        when (ref) {
            is PsiNamedElement -> LookupElementBuilder.createWithIcon(ref)
            is PsiModuleReferable ->
                ref.modules.firstOrNull()?.let { if (it is VcFile)
                    LookupElementBuilder.create(it, it.textRepresentation()).withIcon(VcIcons.MODULE) else
                    LookupElementBuilder.createWithIcon(it) } ?:
                LookupElementBuilder.create(ref, ref.textRepresentation()).withIcon(VcIcons.DIRECTORY)
            else -> LookupElementBuilder.create(ref, ref.textRepresentation())
        }
    }.toTypedArray()

    override fun resolve(): PsiElement? {
        var ref = VcResolveCache.resolveCached( { element ->
            element.scope.resolveName(element.referenceName)
        }, this.element)

        if (ref is RedirectingReferable) ref = ref.originalReferable
        return when (ref) {
            is PsiElement -> ref
            is PsiModuleReferable -> ref.modules.firstOrNull()
            else -> null
        }
    }
}

private fun doRename(oldNameIdentifier: PsiElement, rawName: String) {
    val name = rawName.removeSuffix('.' + VcFileType.defaultExtension)
    if (!VcNamesValidator().isIdentifier(name, oldNameIdentifier.project)) return
    val factory = VcPsiFactory(oldNameIdentifier.project)
    val newNameIdentifier = when (oldNameIdentifier) {
        is VcDefIdentifier -> factory.createDefIdentifier(name)
        is VcRefIdentifier -> factory.createRefIdentifier(name)
        is VcInfixArgument -> factory.createInfixName(name)
        is VcPostfixArgument -> factory.createPostfixName(name)
        else -> error("Unsupported identifier type for `$name`")
    }
    oldNameIdentifier.replace(newNameIdentifier)
}

open class VcPolyReferenceImpl<T : VcReferenceElement>(element: T): VcReferenceImpl<T>(element), PsiPolyVariantReference {
    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        var ref = element.scope.resolveName(element.referenceName)
        if (ref is RedirectingReferable) ref = ref.originalReferable
        return when (ref) {
            is PsiElement -> arrayOf(PsiElementResolveResult(ref))
            is PsiModuleReferable -> ref.modules.map { PsiElementResolveResult(it) }.toTypedArray()
            else -> emptyArray()
        }
    }
}
