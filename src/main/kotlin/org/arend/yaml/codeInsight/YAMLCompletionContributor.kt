package org.arend.yaml.codeInsight

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.arend.ArendIcons
import org.arend.yaml.KEYS
import org.arend.yaml.isYAMLConfig
import org.jetbrains.yaml.YAMLTokenTypes
import org.jetbrains.yaml.psi.YAMLDocument
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.impl.YAMLPlainTextImpl


class YAMLCompletionContributor : CompletionContributor() {
    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        if (!parameters.originalFile.isYAMLConfig) {
            return
        }

        val element = parameters.position
        if (element is LeafPsiElement && element.node.elementType == YAMLTokenTypes.TEXT) {
            val mapping = (element.parent as? YAMLPlainTextImpl)?.parent as? YAMLMapping ?: return
            if (mapping.parent !is YAMLDocument) {
                return
            }

            val keys = mapping.keyValues.mapTo(HashSet()) { it.keyText }
            for (key in KEYS) {
                if (!keys.contains(key)) {
                    result.addElement(LookupElementBuilder.create(key).withIcon(ArendIcons.YAML_KEY))
                }
            }
        }
    }
}