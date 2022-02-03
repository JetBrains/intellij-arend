package org.arend.formatting

import com.intellij.application.options.CodeStyleAbstractPanel
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.highlighter.EditorHighlighter
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.ui.layout.GrowPolicy
import com.intellij.ui.layout.panel
import com.intellij.ui.layout.selected
import org.arend.ArendFileType
import org.arend.settings.ArendCustomCodeStyleSettings
import org.arend.settings.ArendCustomCodeStyleSettings.OptimizeImportsPolicy
import org.arend.util.ArendBundle
import javax.swing.JComponent

class ArendCodeStyleImportsPanelWrapper(settings: CodeStyleSettings) : CodeStyleAbstractPanel(settings) {

    var myOptimizeImportsPolicy: OptimizeImportsPolicy = settings.arendSettings().OPTIMIZE_IMPORTS_POLICY
    var myLimitOfExplicitImports: Int = settings.arendSettings().EXPLICIT_IMPORTS_LIMIT

    override fun getRightMargin(): Int = 0

    override fun createHighlighter(scheme: EditorColorsScheme?): EditorHighlighter? = null

    override fun getFileType(): FileType = ArendFileType

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
        row(ArendBundle.message("arend.code.style.settings.optimize.imports.policy")) {
            buttonGroup {
                row {
                    radioButton(
                        ArendBundle.message("arend.code.style.settings.soft.optimize.imports"),
                        { myOptimizeImportsPolicy == OptimizeImportsPolicy.SOFT },
                        { myOptimizeImportsPolicy = OptimizeImportsPolicy.SOFT },
                    )
                }
                row {
                    radioButton(
                        ArendBundle.message("arend.code.style.settings.use.implicit.imports"),
                        { myOptimizeImportsPolicy == OptimizeImportsPolicy.ONLY_IMPLICIT },
                        { myOptimizeImportsPolicy = OptimizeImportsPolicy.ONLY_EXPLICIT },
                    )
                }
                row {
                    val button = radioButton(
                        ArendBundle.message("arend.code.style.settings.use.explicit.imports"),
                        { myOptimizeImportsPolicy == OptimizeImportsPolicy.ONLY_EXPLICIT },
                        { myOptimizeImportsPolicy = OptimizeImportsPolicy.ONLY_EXPLICIT },
                    )
                    row {
                        label(ArendBundle.message("arend.code.style.settings.explicit.imports.limit"))
                        intTextField(this@ArendCodeStyleImportsPanelWrapper::myLimitOfExplicitImports, null, 0..5000)
                            .enableIf(button.selected)
                            .growPolicy(GrowPolicy.SHORT_TEXT)
                    }
                }
            }
        }
    }


    override fun getTabTitle(): String = ApplicationBundle.INSTANCE.getMessage("title.imports")
}

