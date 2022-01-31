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
import org.arend.util.ArendBundle
import org.arend.util.labeledRow
import javax.swing.JComponent

class ArendCodeStyleImportsPanelWrapper(settings: CodeStyleSettings) : CodeStyleAbstractPanel(settings) {

    var myUseImplicitImports : Boolean = settings.arendSettings().USE_IMPLICIT_IMPORTS
    var myLimitOfExplicitImports : Int = settings.arendSettings().EXPLICIT_IMPORTS_LIMIT

    override fun getRightMargin(): Int = 0

    override fun createHighlighter(scheme: EditorColorsScheme?): EditorHighlighter? = null

    override fun getFileType(): FileType = ArendFileType

    override fun getPreviewText(): String? = null

    private fun CodeStyleSettings.arendSettings() = getCustomSettings(ArendCustomCodeStyleSettings::class.java)

    override fun apply(settings: CodeStyleSettings) {
        myPanel.apply()
        val arendSettings = settings.arendSettings()
        arendSettings.USE_IMPLICIT_IMPORTS = myUseImplicitImports
        arendSettings.EXPLICIT_IMPORTS_LIMIT = myLimitOfExplicitImports
    }

    override fun isModified(settings: CodeStyleSettings): Boolean {
        return myPanel.isModified() || (settings.arendSettings().USE_IMPLICIT_IMPORTS != myUseImplicitImports) || (settings.arendSettings().EXPLICIT_IMPORTS_LIMIT != myLimitOfExplicitImports)
    }

    override fun getPanel(): JComponent = myPanel

    private val myPanel = panel {
        row(ArendBundle.message("arend.code.style.settings.optimize.imports")) {
            buttonGroup {
                row {
                    radioButton(ArendBundle.message("arend.code.style.settings.use.implicit.imports"), this@ArendCodeStyleImportsPanelWrapper::myUseImplicitImports)
                }
                row {
                    val button = radioButton(ArendBundle.message("arend.code.style.settings.use.explicit.imports"), { !myUseImplicitImports }, { myUseImplicitImports = !it })
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


    override fun resetImpl(settings: CodeStyleSettings) {
        val arendSettings = settings.arendSettings()
        myUseImplicitImports = arendSettings.USE_IMPLICIT_IMPORTS
        myLimitOfExplicitImports = arendSettings.EXPLICIT_IMPORTS_LIMIT
        myPanel.reset()
    }

    override fun getTabTitle(): String = ApplicationBundle.INSTANCE.getMessage("title.imports")
}

