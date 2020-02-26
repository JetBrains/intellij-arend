package org.arend.yaml.codeInsight

import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.PlatformPatterns.psiFile
import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.util.ProcessingContext
import org.arend.yaml.*
import org.jetbrains.yaml.YAMLTokenTypes
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLSequenceItem
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
            classReferenceProvider.setOption(JavaClassReferenceProvider.ADVANCED_RESOLVE, true)
            classReferenceProvider.setOption(JavaClassReferenceProvider.ALLOW_DOLLAR_NAMES, false)
            classReferenceProvider.setAllowEmpty(false)
            classReferenceProvider.isSoft = false
        }

        fun isYamlToken(element: PsiElement) = element is LeafPsiElement && element.node.elementType == YAMLTokenTypes.TEXT

        override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<out PsiReference> {
            val parent = element.parent ?: return emptyArray()
            if (element is YAMLPlainTextImpl) {
                if (parent is YAMLKeyValue) when (parent.keyText) {
                    SOURCES, BINARIES, EXTENSIONS -> return FileReferenceSet(element).allReferences
                    EXTENSION_MAIN -> return classReferenceProvider
                            .getReferencesByElement(element, context)
                }
                if (parent is YAMLSequenceItem) {
                    val kv = parent.parent.parent
                    if (kv is YAMLKeyValue && kv.keyText == MODULES)
                        return classReferenceProvider
                                .getReferencesByElement(element, context)
                }
            }
            return emptyArray()
        }
    }
}
