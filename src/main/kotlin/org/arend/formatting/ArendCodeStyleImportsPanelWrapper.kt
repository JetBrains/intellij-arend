package org.arend.formatting

import com.intellij.application.options.CodeStyleAbstractPanel
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.highlighter.EditorHighlighter
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.selected
import org.arend.ArendFileTypeInstance
import org.arend.settings.ArendCustomCodeStyleSettings
import org.arend.settings.ArendCustomCodeStyleSettings.OptimizeImportsPolicy
import org.arend.util.ArendBundle
import javax.swing.JComponent

class ArendCodeStyleImportsPanelWrapper(settings: CodeStyleSettings) : CodeStyleAbstractPanel(settings) {

    private var myOptimizeImportsPolicy: OptimizeImportsPolicy = settings.arendSettings().OPTIMIZE_IMPORTS_POLICY
    private var myLimitOfExplicitImports: Int = settings.arendSettings().EXPLICIT_IMPORTS_LIMIT

    override fun getRightMargin(): Int = 0

    override fun createHighlighter(scheme: EditorColorsScheme): EditorHighlighter? = null

    override fun getFileType(): FileType = ArendFileTypeInstance

    override fun getPreviewText(): String? = null

    private fun CodeStyleSettings.arendSettings(): ArendCustomCodeStyleSettings =
        getCustomSettings(ArendCustomCodeStyleSettings::class.java)

    override fun apply(settings: CodeStyleSettings) {
        myPanel.apply()
        val arendSettings = settings.arendSettings()
        arendSettings.OPTIMIZE_IMPORTS_POLICY = myOptimizeImportsPolicy
        arendSettings.EXPLICIT_IMPORTS_LIMIT = myLimitOfExplicitImports
    }

    override fun isModified(settings: CodeStyleSettings): Boolean {
        return myPanel.isModified() ||
                settings.arendSettings().OPTIMIZE_IMPORTS_POLICY != myOptimizeImportsPolicy ||
                settings.arendSettings().EXPLICIT_IMPORTS_LIMIT != myLimitOfExplicitImports
    }

    override fun resetImpl(settings: CodeStyleSettings) {
        val arendSettings = settings.arendSettings()
        myOptimizeImportsPolicy = arendSettings.OPTIMIZE_IMPORTS_POLICY
        myLimitOfExplicitImports = arendSettings.EXPLICIT_IMPORTS_LIMIT
        myPanel.reset()
    }

    override fun getPanel(): JComponent = myPanel

    private val myPanel = panel {
        group(ArendBundle.message("arend.code.style.settings.optimize.imports.policy")) {
            buttonsGroup {
                row {
                    radioButton(
                        ArendBundle.message("arend.code.style.settings.soft.optimize.imports")
                    ).bindSelected(
                        { myOptimizeImportsPolicy == OptimizeImportsPolicy.SOFT },
                        { myOptimizeImportsPolicy = OptimizeImportsPolicy.SOFT },
                    )
                }
                row {
                    radioButton(
                        ArendBundle.message("arend.code.style.settings.use.implicit.imports")
                    ).bindSelected(
                        { myOptimizeImportsPolicy == OptimizeImportsPolicy.ONLY_IMPLICIT },
                        { myOptimizeImportsPolicy = OptimizeImportsPolicy.ONLY_IMPLICIT },
                    )
                }
                panel {
                    lateinit var button: JBRadioButton
                    row {
                        button = radioButton(
                            ArendBundle.message("arend.code.style.settings.use.explicit.imports")
                        ).bindSelected(
                            { myOptimizeImportsPolicy == OptimizeImportsPolicy.ONLY_EXPLICIT },
                            { myOptimizeImportsPolicy = OptimizeImportsPolicy.ONLY_EXPLICIT },
                        ).component
                    }
                    indent { row {
                        intTextField(0..5000)
                            .label(ArendBundle.message("arend.code.style.settings.explicit.imports.limit"))
                            .enabledIf(button.selected)
                            .bindIntText(this@ArendCodeStyleImportsPanelWrapper::myLimitOfExplicitImports)
                    } }
                }
            }
        }
    }


    override fun getTabTitle(): String = ApplicationBundle.INSTANCE.getMessage("title.imports")
}

