package org.arend.yaml.codeInsight

import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.PlatformPatterns.psiFile
import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.util.ProcessingContext
import org.arend.yaml.BINARIES
import org.arend.yaml.EXTENSIONS
import org.arend.yaml.EXTENSION_MAIN
import org.arend.yaml.SOURCES
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
        private val classReferenceProvider = JavaClassReferenceProvider()
        init {
            classReferenceProvider.setOption(JavaClassReferenceProvider.NOT_ENUM, true)
            classReferenceProvider.setOption(JavaClassReferenceProvider.NOT_INTERFACE, true)
            classReferenceProvider.setOption(JavaClassReferenceProvider.ALLOW_WILDCARDS, false)
            classReferenceProvider.setAllowEmpty(false)
        }

        fun isYamlToken(element: PsiElement) = element is LeafPsiElement && element.node.elementType == YAMLTokenTypes.TEXT

        override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<out PsiReference> {
            val parent = element.parent ?: return emptyArray()
            return if (element is YAMLPlainTextImpl && parent is YAMLKeyValue) when (parent.keyText) {
                SOURCES, BINARIES, EXTENSIONS -> FileReferenceSet(element).allReferences
                EXTENSION_MAIN -> classReferenceProvider.getReferencesByElement(element, context)
                else -> emptyArray()
            }
            else emptyArray()
        }
    }
}
