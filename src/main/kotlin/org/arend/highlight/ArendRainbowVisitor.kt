package org.arend.highlight

import com.intellij.codeInsight.daemon.RainbowVisitor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.collectDescendantsOfType
import org.arend.psi.*
import org.arend.resolving.ArendPatternDefReferenceImpl

class ArendRainbowVisitor : RainbowVisitor() {
    override fun suitableForFile(file: PsiFile) = file is ArendFile

    override fun clone() = ArendRainbowVisitor()

    override fun visit(function: PsiElement) {
        if (function !is ArendDefinition) return
        val defIdentifier = function.defIdentifier

        fun addInfo(ident: PsiElement?, colorTag: String) {
            if (ident != null) addInfo(getInfo(function, ident, colorTag, null))
        }

        val bindingToUniqueName: Map<ArendDefIdentifier, String> = run {
            val allBindings = function.collectDescendantsOfType<ArendDefIdentifier>(canGoInside = {
                it !is ArendWhere
            }).asSequence()
                .filter { it.name != null && it != defIdentifier }
                .filter {
                    val parent = it.parent ?: return@filter true
                    parent !is ArendConstructor && parent !is ArendClassField
                }
                .filter {
                    val reference = it.reference ?: return@filter true
                    if (reference is ArendPatternDefReferenceImpl<*>) {
                        reference.resolve() == it
                    } else true
                }.toList()
            val byName = allBindings.groupBy { it.name }
            allBindings
                .map { it to "${it.name}#${byName[it.name]?.indexOf(it)}" }
                .toMap()
        }

        for ((binding, name) in bindingToUniqueName) {
            addInfo(binding.referenceNameElement, name)
        }

        for (path in function.collectDescendantsOfType<ArendRefIdentifier>(canGoInside = {
            it !is ArendWhere
        })) {
            val reference = path.reference ?: continue
            val target = reference.resolve() as? ArendDefIdentifier ?: continue
            val colorTag = bindingToUniqueName[target] ?: return
            addInfo(path.referenceNameElement, colorTag)
        }
    }
}