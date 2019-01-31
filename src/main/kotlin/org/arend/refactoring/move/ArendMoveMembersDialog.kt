package org.arend.refactoring.move

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
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
import org.arend.term.group.Group
import javax.swing.*
import javax.swing.event.DocumentEvent

class ArendMoveMembersDialog(project: Project,
                             elements: List<ArendDefinition>,
                             container: PsiElement,
                             private val enclosingModule: Module): MoveDialogBase(project, false) {
    private val targetFileTextField: JTextField
    private val targetModuleTextField: JTextField
    private val myPanel: JPanel
    private val elementPointers: List<SmartPsiElementPointer<ArendDefinition>>
    private val containerRef: SmartPsiElementPointer<PsiElement>
    init {
        title = "Move Arend static members"
        containerRef = SmartPointerManager.createPointer(container)

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
        validateButtons()
    }

    override fun doAction() {
        val targetGroup = locateTargetGroup()

        val errorMessage: String? =
                when {
                    elementPointers.any{ it.element == null} -> "Can't locate of the PSI elements being moved"
                    targetGroup !is PsiElement -> "Can't locate target PSI element"
                    else -> null
                }

        if (errorMessage != null) {
            CommonRefactoringUtil.showErrorMessage(
                    MoveMembersImpl.REFACTORING_NAME,
                    errorMessage,
                    HelpID.MOVE_MEMBERS,
                    myProject)
            return
        }

        invokeRefactoring(ArendStaticMemberRefactoringProcessor(project, {}, elementPointers, targetGroup as PsiElement))
    }

    private fun locateTargetGroup(): Group? {
        val f = enclosingModule.libraryConfig?.findArendFile(ModulePath.fromString(targetFileTextField.text))
        val t = targetModuleTextField.text
        val g = if (t.trim() == "") f else f?.findGroupByFullName(t.split("\\."))
        return if (g != containerRef.element) g else null
    }

    override fun areButtonsValid(): Boolean = locateTargetGroup() != null

    override fun getPreferredFocusedComponent(): JComponent? = targetFileTextField

    override fun getCbTitle(): String = "Open moved members in editor"

    override fun getMovePropertySuffix(): String = "Arend static member"

    override fun createCenterPanel(): JComponent? = initOpenInEditorCb()

    override fun createNorthPanel(): JComponent? = myPanel

    override fun getDimensionServiceKey(): String? = "#org.arend.refactoring.move.ArendMoveMembersDialog"


}