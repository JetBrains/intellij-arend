package org.arend.codeInsight.hints

import com.intellij.codeInsight.hints.*
import com.intellij.codeInsight.hints.presentation.MenuOnClickPresentation
import com.intellij.lang.Language
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.BlockInlayPriority
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.startOffset
import org.arend.ArendLanguage
import org.arend.core.definition.Definition
import org.arend.psi.ext.ArendDefIdentifier
import org.arend.psi.ext.ArendDefinition
import org.arend.server.ArendServerService
import javax.swing.JPanel

@Suppress("UnstableApiUsage")
abstract class ArendDefinitionInlayProvider : InlayHintsProvider<NoSettings> {
    abstract fun getText(definition: Definition): String?

    override fun isLanguageSupported(language: Language) = language == ArendLanguage.INSTANCE

    override fun createSettings() = NoSettings()

    override fun getCollectorFor(file: PsiFile, editor: Editor, settings: NoSettings, sink: InlayHintsSink): InlayHintsCollector {
        val project = file.project
        val document = PsiDocumentManager.getInstance(project).getDocument(file)
        return object : FactoryInlayHintsCollector(editor) {
            override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
                if (element !is ArendDefIdentifier) return true
                val arendDef = element.parent as? ArendDefinition<*> ?: return true
                val def = project.service<ArendServerService>().server.getTCReferable(arendDef)?.typechecked ?: return true
                val text = getText(def) ?: return true

                val offset = (arendDef.parent ?: arendDef).startOffset
                val inset = if (document == null) 0 else {
                    val width = EditorUtil.getPlainSpaceWidth(editor)
                    val line = document.getLineNumber(offset)
                    val startOffset = document.getLineStartOffset(line)
                    width * (offset - startOffset)
                }
                sink.addBlockElement(offset, relatesToPrecedingText = false, showAbove = true, BlockInlayPriority.DOC_RENDER, MenuOnClickPresentation(factory.inset(factory.smallText(text + "\n"), left = inset), project) {
                    val provider = this@ArendDefinitionInlayProvider
                    listOf(InlayProviderDisablingAction(provider.name, file.language, project, provider.key))
                })
                return true
            }
        }
    }

    override fun createConfigurable(settings: NoSettings) = object : ImmediateConfigurable {
        override fun createComponent(listener: ChangeListener) = JPanel()
    }
}