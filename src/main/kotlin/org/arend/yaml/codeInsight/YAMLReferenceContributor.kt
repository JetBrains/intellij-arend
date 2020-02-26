package org.arend.yaml.codeInsight

import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.PlatformPatterns.psiFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.util.ProcessingContext
import org.jetbrains.yaml.YAMLTokenTypes
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.impl.YAMLPlainTextImpl

class YAMLReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(psiElement()
                .inFile(psiFile(YAMLFile::class.java)),
                YAMLReferenceProvider)
    }

    companion object YAMLReferenceProvider : PsiReferenceProvider() {
        fun isYamlToken(element: PsiElement) = element is LeafPsiElement && element.node.elementType == YAMLTokenTypes.TEXT

        override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<FileReference> = element
                .takeIf { it is YAMLPlainTextImpl }
                ?.takeIf { it.parent is YAMLKeyValue }
                ?.let(::FileReferenceSet)
                ?.allReferences
                ?: emptyArray()
    }
}
