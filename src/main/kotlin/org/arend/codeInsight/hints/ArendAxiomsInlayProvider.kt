package org.arend.codeInsight.hints

import com.intellij.codeInsight.hints.*
import com.intellij.codeInsight.hints.presentation.MenuOnClickPresentation
import com.intellij.lang.Language
import com.intellij.openapi.editor.BlockInlayPriority
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.suggested.startOffset
import org.arend.ArendLanguage
import org.arend.naming.reference.TCDefReferable
import org.arend.psi.ext.ArendDefIdentifier
import org.arend.psi.ext.ArendDefinition
import javax.swing.JPanel

@Suppress("UnstableApiUsage")
class ArendAxiomsInlayProvider : InlayHintsProvider<NoSettings> {
    override val key: SettingsKey<NoSettings>
        get() = SettingsKey("arend.inlays.axioms")

    override val name: String
        get() = "Axioms"

    override val previewText: String
        get() = """
            \axiom axiom : 0 = 0
            
            \func foo => axiom
        """.trimIndent()

    override val description
        get() = "Shows axioms used by a definition"

    override fun isLanguageSupported(language: Language) = language == ArendLanguage.INSTANCE

    override fun createSettings() = NoSettings()

    override fun getCollectorFor(file: PsiFile, editor: Editor, settings: NoSettings, sink: InlayHintsSink): InlayHintsCollector {
        val project = file.project
        val document = PsiDocumentManager.getInstance(project).getDocument(file)
        return object : FactoryInlayHintsCollector(editor) {
            override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
                if (element !is ArendDefIdentifier) return true
                val arendDef = element.parent as? ArendDefinition<*> ?: return true
                val def = (arendDef.tcReferable as? TCDefReferable)?.typechecked ?: return true
                val axioms = def.axioms
                if (axioms.isEmpty() || axioms.size == 1 && axioms.contains(def)) return true

                val offset = arendDef.startOffset
                val inset = if (document == null) 0 else {
                    val width = EditorUtil.getPlainSpaceWidth(editor)
                    val line = document.getLineNumber(offset)
                    val startOffset = document.getLineStartOffset(line)
                    width * (offset - startOffset)
                }
                sink.addBlockElement(offset, relatesToPrecedingText = false, showAbove = true, BlockInlayPriority.DOC_RENDER, MenuOnClickPresentation(factory.inset(factory.smallText("Axioms: ${axioms.joinToString()}\n"), left = inset), project) {
                    val provider = this@ArendAxiomsInlayProvider
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