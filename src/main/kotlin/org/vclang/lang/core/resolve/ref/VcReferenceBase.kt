package org.vclang.lang.core.resolve.ref

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.ResolveResult
import org.vclang.ide.icons.VcIcons
import org.vclang.lang.core.psi.ext.VcCompositeElement
import org.vclang.lang.core.psi.ext.VcReferenceElement
import org.vclang.lang.core.psi.findDefinitions

abstract class VcReferenceBase<T : VcReferenceElement>(element: T)
    : PsiPolyVariantReferenceBase<T>(element, TextRange(0, element.textLength)), VcReference {

    override fun resolve(): VcCompositeElement? =
            multiResolve(false).firstOrNull()?.element as? VcCompositeElement

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        val definitions = myElement.project.findDefinitions(element.text)
        return definitions.map { PsiElementResolveResult(it) }.toTypedArray()
    }

    override fun getVariants(): Array<Any> {
        val definitions = myElement.project.findDefinitions()
        return definitions.map {
            LookupElementBuilder.create(it)
                    .withIcon(VcIcons.FILE)
                    .withTypeText(it.containingFile.name)
        }.toTypedArray()
    }
}
