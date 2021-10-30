package org.arend.highlight.hints

import com.intellij.codeInsight.hints.*
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import com.intellij.util.castSafelyTo
import org.arend.core.definition.FunctionDefinition
import org.arend.psi.ArendDefFunction
import javax.swing.JComponent
import javax.swing.JPanel

class ArendFunctionCodomainInlayHint : InlayHintsProvider<NoSettings> {
    override val name: String
        get() = "Function codomain"
    override val key: SettingsKey<NoSettings>
        get() = SettingsKey("arend.codomain.hint")
    override val previewText: String
        get() = """
            \func f => 1
        """.trimIndent()

    override fun createConfigurable(settings: NoSettings): ImmediateConfigurable {
        return object : ImmediateConfigurable {
            override fun createComponent(listener: ChangeListener): JComponent {
                return JPanel()
            }
        }
    }

    override fun createSettings(): NoSettings = NoSettings()

    override fun getCollectorFor(
        file: PsiFile,
        editor: Editor,
        settings: NoSettings,
        sink: InlayHintsSink
    ): InlayHintsCollector {
        return object : FactoryInlayHintsCollector(editor) {
            override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
                if (element is ArendDefFunction && element.resultType == null && element.functionBody != null) {
                    val coreDefinition = element.tcReferable?.typechecked?.castSafelyTo<FunctionDefinition>()
                    val coreType = coreDefinition?.resultType
                    if (coreType != null) {
                        val offset =
                            ((element.parameters.lastOrNull() ?: element.defIdentifier) as? PsiElement)?.endOffset
                        if (offset != null) {
                            sink.addInlineElement(offset, true, factory.roundWithBackground(factory.text(" : $coreType")), false)
                        }
                    }
                }
                return true
            }
        }
    }
}