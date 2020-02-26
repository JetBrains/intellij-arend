package org.arend.yaml.codeInsight

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import org.arend.ArendIcons
import org.arend.prelude.Prelude
import org.arend.yaml.*
import org.jetbrains.yaml.psi.YAMLDocument
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.impl.YAMLPlainTextImpl


/**
 * @see [com.jetbrains.jsonSchema.impl.JsonSchemaCompletionContributor]
 */
class YAMLCompletionContributor : CompletionContributor() {
    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        if (!parameters.originalFile.isYAMLConfig) {
            return
        }

        val element = parameters.position
        if (YAMLReferenceContributor.isYamlToken(element)) {
            val textImpl = element.parent as? YAMLPlainTextImpl ?: return
            when (val parent = textImpl.parent) {
                is YAMLMapping -> mapping(parent, result)
                is YAMLKeyValue -> keyValue(parent, parameters, result)
            }
        }
    }

    private val filePathContributor by lazy(::FilePathCompletionContributor)
    private val javaClassContributor by lazy (::JavaClassNameCompletionContributor)

    private fun keyValue(
            parent: YAMLKeyValue,
            parameters: CompletionParameters,
            result: CompletionResultSet
    ) = when (parent.keyText) {
        LANG_VERSION -> result.addElement(LookupElementBuilder
                .create(Prelude.VERSION.toString())
                .withIcon(ArendIcons.AREND))
        SOURCES, BINARIES, EXTENSIONS -> filePathContributor.fillCompletionVariants(parameters, result)
        EXTENSION_MAIN -> javaClassContributor.fillCompletionVariants(parameters, result)
        else -> {
        }
    }

    private fun mapping(mapping: YAMLMapping, result: CompletionResultSet) {
        if (mapping.parent !is YAMLDocument) return

        if (mapping.keyValues.none { it.keyText == LANG_VERSION })
            result.addElement(LookupElementBuilder
                    .create("$LANG_VERSION: ${Prelude.VERSION}")
                    .withIcon(ArendIcons.AREND))
    }
}