package org.arend.codeInsight.hints

import com.intellij.codeInsight.hints.*
import com.intellij.codeInsight.hints.presentation.MenuOnClickPresentation
import com.intellij.lang.Language
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.suggested.endOffset
import org.arend.ArendLanguage
import org.arend.core.context.binding.LevelVariable
import org.arend.core.context.binding.ParamLevelVariable
import org.arend.naming.reference.TCDefReferable
import org.arend.psi.ArendDefIdentifier
import org.arend.psi.ArendDefinition
import org.arend.psi.ArendHLevelParams
import org.arend.psi.ArendPLevelParams
import org.arend.term.prettyprint.PrettyPrintVisitor
import org.arend.term.prettyprint.ToAbstractVisitor
import javax.swing.JPanel

@Suppress("UnstableApiUsage")
class ArendLevelParametersInlayProvider : InlayHintsProvider<NoSettings> {
    override val key: SettingsKey<NoSettings>
        get() = SettingsKey("arend.inlays")

    override val name
        get() = "Level parameters"

    override val group
        get() = InlayGroup.TYPES_GROUP

    override val previewText
        get() = "\\class C \\plevels p1 >= p2\n\n\\func test (c : C) => c"

    override val description
        get() = "Shows inferred level parameters"

    override fun isLanguageSupported(language: Language) = language == ArendLanguage.INSTANCE

    override fun createSettings() = NoSettings()

    override fun getCollectorFor(file: PsiFile, editor: Editor, settings: NoSettings, sink: InlayHintsSink): InlayHintsCollector {
        val project = file.project
        return object : FactoryInlayHintsCollector(editor) {
            override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
                if (element !is ArendDefIdentifier) return true
                val arendDef = element.parent as? ArendDefinition ?: return true
                val def = (arendDef.tcReferable as? TCDefReferable)?.typechecked ?: return true
                if (def.levelParameters == null || def.levelParameters.isEmpty()) return true

                val builder = StringBuilder()
                val levelParams = def.levelParameters
                if (levelParams[0].type == LevelVariable.LvlType.PLVL && levelParams[0] is ParamLevelVariable && PsiTreeUtil.getChildOfType(arendDef, ArendPLevelParams::class.java) == null) {
                    builder.append(" ")
                    val ppv = PrettyPrintVisitor(builder, 0)
                    ppv.prettyPrintLevelParameters(ToAbstractVisitor.visitLevelParameters(levelParams.subList(0, def.numberOfPLevelParameters)), true)
                }
                val lastVar = levelParams[levelParams.size - 1]
                if (lastVar.type == LevelVariable.LvlType.HLVL && lastVar is ParamLevelVariable && PsiTreeUtil.getChildOfType(arendDef, ArendHLevelParams::class.java) == null) {
                    builder.append(" ")
                    val ppv = PrettyPrintVisitor(builder, 0)
                    ppv.prettyPrintLevelParameters(ToAbstractVisitor.visitLevelParameters(levelParams.subList(def.numberOfPLevelParameters, levelParams.size)), false)
                }

                val str = builder.toString()
                if (str.isEmpty()) return true
                sink.addInlineElement(element.endOffset, true, MenuOnClickPresentation(factory.text(str), project) {
                    val provider = this@ArendLevelParametersInlayProvider
                    listOf(InlayProviderDisablingAction(provider.name, file.language, project, provider.key))
                }, false)
                return true
            }
        }
    }

    override fun createConfigurable(settings: NoSettings) = object : ImmediateConfigurable {
        override fun createComponent(listener: ChangeListener) = JPanel()
    }
}