package org.arend.refactoring.move

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.refactoring.move.MoveDialogBase
import com.intellij.ui.layout.panel
import org.arend.psi.ArendFile
import org.arend.psi.ext.fullName
import org.arend.psi.ext.impl.DefinitionAdapter
import org.arend.term.group.ChildGroup
import javax.swing.*

class ArendMoveMembersDialog(project: Project, elements: List<PsiElement>, container: ChildGroup): MoveDialogBase(project, false) {
    private val targetModuleTextField: JTextField = JTextField("")
    private val targetNamespaceTextField: JTextField = JTextField("")
    private val myPanel: JPanel

    init {
        title = "Move Arend static members"

        val names = when (container) {
            is ArendFile -> {
                Pair(container.fullName, "")
            }
            is DefinitionAdapter<*> -> {
                val file = container.getContainingFile() as? ArendFile
                Pair(file?.modulePath.toString(), container.fullName)
            }
            else -> null
        }

        myPanel = panel {

            row("Source module: ") {
                val sourceModuleName = JTextField(names?.first?: "???")
                sourceModuleName.isEditable = false
                sourceModuleName()
            }

            row("Source namespace: ") {
                val sourceNamespace = JTextField(names?.second?: "???")
                sourceNamespace.isEditable = false
                sourceNamespace()
            }

            row("Target module: ") {
                targetModuleTextField()
            }
            row("Target namespace: ") {
                targetNamespaceTextField()
            }
        }

        init()
    }

    override fun doAction() {
        //TODO: Implement refactoring
    }

    override fun getPreferredFocusedComponent(): JComponent? = targetModuleTextField

    override fun getCbTitle(): String = "Open moved members in editor"

    override fun getMovePropertySuffix(): String = "Arend static member"

    override fun createCenterPanel(): JComponent? = initOpenInEditorCb()

    override fun createNorthPanel(): JComponent? = myPanel

    override fun getDimensionServiceKey(): String? = "#org.arend.refactoring.move.ArendMoveMembersDialog"


}