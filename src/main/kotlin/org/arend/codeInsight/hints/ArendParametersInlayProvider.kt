package org.arend.codeInsight.hints

import com.intellij.codeInsight.hints.*
import com.intellij.codeInsight.hints.presentation.MenuOnClickPresentation
import com.intellij.lang.Language
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.endOffset
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import org.arend.ArendLanguage
import org.arend.core.context.binding.LevelVariable
import org.arend.core.context.binding.ParamLevelVariable
import org.arend.core.context.param.DependentLink
import org.arend.core.definition.ClassDefinition
import org.arend.ext.prettyprinting.PrettyPrinterConfig
import org.arend.psi.ext.*
import org.arend.psi.ArendElementTypes.NO_CLASSIFYING_KW
import org.arend.psi.findNextSibling
import org.arend.term.concrete.Concrete
import org.arend.term.prettyprint.PrettyPrintVisitor
import org.arend.term.prettyprint.ToAbstractVisitor
import java.lang.Integer.min
import javax.swing.JPanel

@Suppress("UnstableApiUsage")
class ArendParametersInlayProvider : InlayHintsProvider<ArendParametersInlayProvider.Settings> {
    data class Settings(var showTypes: Boolean = false)

    override val key: SettingsKey<Settings>
        get() = SettingsKey("arend.inlays.parameters")

    override val name
        get() = "Parameters"

    override val previewText
        get() = """
            \class C \plevels p1 >= p2
            
            \func test (c : C) => c
            
            \func f (x : Nat) => x
              \where
                \func g => x
        """.trimIndent()

    override val description
        get() = "Shows inferred parameters"

    override fun isLanguageSupported(language: Language) = language == ArendLanguage.INSTANCE

    override fun createSettings() = Settings()

    override fun getCollectorFor(file: PsiFile, editor: Editor, settings: Settings, sink: InlayHintsSink): InlayHintsCollector {
        val project = file.project
        return object : FactoryInlayHintsCollector(editor) {
            override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
                if (element !is ArendDefIdentifier) return true
                val arendDef = element.parent as? ArendDefinition<*> ?: return true
                val def = arendDef.tcReferable?.typechecked ?: return true
                val levelParams = def.levelParameters
                if ((levelParams == null || levelParams.isEmpty()) && def.parametersOriginalDefinitions.isEmpty()) return true
                val builder = StringBuilder()

                if (levelParams != null && levelParams.isNotEmpty()) {
                    if (levelParams[0].type == LevelVariable.LvlType.PLVL && levelParams[0] is ParamLevelVariable && arendDef.pLevelParameters == null) {
                        builder.append(" ")
                        val ppv = PrettyPrintVisitor(builder, 0)
                        ppv.prettyPrintLevelParameters(ToAbstractVisitor.visitLevelParameters(levelParams.subList(0, def.numberOfPLevelParameters), true), true)
                    }
                    val lastVar = levelParams[levelParams.size - 1]
                    if (lastVar.type == LevelVariable.LvlType.HLVL && lastVar is ParamLevelVariable && arendDef.hLevelParameters == null) {
                        builder.append(" ")
                        val ppv = PrettyPrintVisitor(builder, 0)
                        ppv.prettyPrintLevelParameters(ToAbstractVisitor.visitLevelParameters(levelParams.subList(def.numberOfPLevelParameters, levelParams.size), false), false)
                    }
                }

                if (def.parametersOriginalDefinitions.isNotEmpty()) {
                    builder.append(" ")
                    if (settings.showTypes) {
                        val ppv = PrettyPrintVisitor(builder, 0)
                        val parameters = if (def is ClassDefinition) {
                            def.personalFields.subList(0, def.parametersOriginalDefinitions.size).map { Concrete.TelescopeParameter(null, it.referable.isExplicitField, listOf(it.referable), ToAbstractVisitor.convert(it.resultType, PrettyPrinterConfig.DEFAULT), it.isProperty) }
                        } else {
                            ToAbstractVisitor.convert(DependentLink.Helper.take(if (def.hasEnclosingClass()) def.parameters.next else def.parameters, def.parametersOriginalDefinitions.size), PrettyPrinterConfig.DEFAULT)
                        }
                        ppv.prettyPrintParameters(parameters)
                    } else {
                        val list = if (def is ClassDefinition) {
                            def.personalFields.subList(0, min(def.parametersOriginalDefinitions.size, def.personalFields.size)).map { Pair(it.referable.isExplicitField, it.name) }
                        } else {
                            DependentLink.Helper.toList(DependentLink.Helper.take(if (def.hasEnclosingClass()) def.parameters.next else def.parameters, def.parametersOriginalDefinitions.size)).map { Pair(it.isExplicit, it.name) }
                        }
                        builder.append(list.joinToString(" ") { if (it.first) it.second else "{${it.second}}" })
                    }
                }

                val str = builder.toString()
                if (str.isEmpty()) return true
                var lastElement = element
                val sibling1 = lastElement.findNextSibling()
                if (sibling1 is ArendAlias) lastElement = sibling1
                val sibling2 = lastElement.findNextSibling()
                if (sibling2 is LeafPsiElement && sibling2.elementType == NO_CLASSIFYING_KW) lastElement = sibling2
                sink.addInlineElement(lastElement.endOffset, true, MenuOnClickPresentation(factory.text(str), project) {
                    val provider = this@ArendParametersInlayProvider
                    listOf(InlayProviderDisablingAction(provider.name, file.language, project, provider.key))
                }, false)
                return true
            }
        }
    }

    override fun createConfigurable(settings: Settings) = object : ImmediateConfigurable {
        private var showTypesBox = JBCheckBox("Show types")

        override fun createComponent(listener: ChangeListener): JPanel {
            showTypesBox.addChangeListener {
                settings.showTypes = showTypesBox.isSelected
                listener.settingsChanged()
            }
            return panel {
                row { cell(showTypesBox) }
            }.apply {
                border = JBUI.Borders.empty(5)
            }
        }

        override fun reset() {
          showTypesBox.isSelected = settings.showTypes
        }
    }
}