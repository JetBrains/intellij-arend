package org.arend.refactoring.move

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.refactoring.HelpID
import com.intellij.refactoring.move.MoveDialogBase
import com.intellij.refactoring.move.moveMembers.MoveMembersImpl
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.ui.layout.panel
import org.arend.module.ModulePath
import org.arend.module.util.findArendFile
import org.arend.module.util.libraryConfig
import org.arend.psi.ArendDefinition
import org.arend.psi.ArendFile
import org.arend.psi.ext.fullName
import org.arend.psi.ext.impl.DefinitionAdapter
import org.arend.psi.findGroupByFullName
import org.arend.term.group.ChildGroup
import org.arend.term.group.Group
import javax.swing.*
import javax.swing.event.DocumentEvent

class ArendMoveMembersDialog(project: Project,
                             elements: List<ArendDefinition>,
                             container: ChildGroup,
                             private val enclosingModule: Module): MoveDialogBase(project, false) {
    private val targetFileTextField: JTextField
    private val targetModuleTextField: JTextField
    private val myPanel: JPanel
    private val elementPointers: List<SmartPsiElementPointer<ArendDefinition>>

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
            else -> {
                null
            }
        }

        elementPointers = elements.map { SmartPointerManager.createPointer(it) }

        val updater = {
            validateButtons()
        }

        val documentListener = object: javax.swing.event.DocumentListener {
            override fun changedUpdate(e: DocumentEvent?) = updater.invoke()

            override fun insertUpdate(e: DocumentEvent?) = updater.invoke()

            override fun removeUpdate(e: DocumentEvent?) = updater.invoke()
        }

        targetFileTextField = JTextField(names?.first?: "")
        targetFileTextField.document.addDocumentListener(documentListener)
        targetModuleTextField = JTextField(names?.second?: "")
        targetModuleTextField.document.addDocumentListener(documentListener)

        myPanel = panel {
            row("Target file: ") {
                targetFileTextField()
            }
            row("Target module: ") {
                targetModuleTextField()
            }
        }

        init()
    }

    override fun doAction() {
        if (elementPointers.any{ it.element == null}) {
            CommonRefactoringUtil.showErrorMessage(
                    MoveMembersImpl.REFACTORING_NAME,
                    "One of the PSI elements being moved was changed",
                    HelpID.MOVE_MEMBERS,
                    myProject)
            return
        }

        //TODO: Implement refactoring
    }

    private fun locateTargetGroup(): Group? {
        val f = enclosingModule.libraryConfig?.findArendFile(ModulePath.fromString(targetFileTextField.text))
        return f?.findGroupByFullName(targetModuleTextField.text.split("\\."))
    }

    override fun areButtonsValid(): Boolean = locateTargetGroup() != null

    override fun getPreferredFocusedComponent(): JComponent? = targetFileTextField

    override fun getCbTitle(): String = "Open moved members in editor"

    override fun getMovePropertySuffix(): String = "Arend static member"

    override fun createCenterPanel(): JComponent? = initOpenInEditorCb()

    override fun createNorthPanel(): JComponent? = myPanel

    override fun getDimensionServiceKey(): String? = "#org.arend.refactoring.move.ArendMoveMembersDialog"


}