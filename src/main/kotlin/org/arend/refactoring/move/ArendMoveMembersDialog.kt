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
import org.arend.psi.ArendFile
import org.arend.psi.ext.fullName
import org.arend.psi.ext.impl.ArendGroup
import org.arend.psi.findGroupByFullName
import org.arend.term.group.ChildGroup
import org.arend.term.group.Group
import javax.swing.*

class ArendMoveMembersDialog(project: Project,
                             elements: List<ArendGroup>,
                             container: PsiElement,
                             private val enclosingModule: Module) : MoveDialogBase(project, false) {
    private val targetFileTextField: JTextField
    private val targetModuleTextField: JTextField
    private val myPanel: JPanel
    private val elementPointers: List<SmartPsiElementPointer<ArendGroup>>
    private val containerRef: SmartPsiElementPointer<PsiElement>

    init {
        title = "Move Arend static members"
        containerRef = SmartPointerManager.createPointer(container)

        val names = when (container) {
            is ArendFile -> {
                Pair(container.fullName, "")
            }
            is ArendGroup -> {
                val file = container.getContainingFile() as? ArendFile
                Pair(file?.modulePath.toString(), container.fullName)
            }
            else -> {
                null
            }
        }

        elementPointers = elements.map { SmartPointerManager.createPointer(it) }

        targetFileTextField = JTextField(names?.first ?: "")
        targetModuleTextField = JTextField(names?.second ?: "")

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
        val sourceGroup = containerRef.element
        val elementsToMove = elementPointers.mapNotNull { it.element }

        val locateResult: Pair<Group?, String?> = when {
            sourceGroup !is ChildGroup -> Pair(null, "Source module has invalid type")
            elementsToMove.size != elementPointers.size -> Pair(null, "Can't locate some of the elements to be moved")
            else -> locateTargetGroupWithChecks(targetFileTextField.text, targetModuleTextField.text, enclosingModule, sourceGroup, elementPointers.map { it.element })
        }

        if (locateResult.second != null)
            CommonRefactoringUtil.showErrorMessage(MoveMembersImpl.REFACTORING_NAME, locateResult.second, HelpID.MOVE_MEMBERS, myProject) else
            invokeRefactoring(ArendStaticMemberRefactoringProcessor(project, {}, elementsToMove, sourceGroup as ChildGroup, locateResult.first as PsiElement))
    }

    override fun getPreferredFocusedComponent(): JComponent? = targetFileTextField

    override fun getCbTitle(): String = "Open moved members in editor"

    override fun getMovePropertySuffix(): String = "Arend static member"

    override fun createCenterPanel(): JComponent? = initOpenInEditorCb()

    override fun createNorthPanel(): JComponent? = myPanel

    override fun getDimensionServiceKey(): String? = "#org.arend.refactoring.move.ArendMoveMembersDialog"

    companion object {
        fun locateTargetGroupWithChecks(fileName: String, moduleName: String, ideaModule: Module,
                                        sourceModule: ChildGroup, elementsToMove: List<ArendGroup?>): Pair<Group?, String?> {
            val targetFile = ideaModule.libraryConfig?.findArendFile(ModulePath.fromString(fileName))
                    ?: return Pair(null, "Can't locate target file")
            val targetModule = if (moduleName.trim() == "") targetFile else targetFile.findGroupByFullName(moduleName.split("."))

            var m = targetModule as? ChildGroup
            if (m == sourceModule) return Pair(null, "Target module cannot coincide with the source module")

            while (m != null) {
                for (elementP in elementsToMove) if (m == elementP)
                    return Pair(null, "Target module cannot be a submodule of the member being moved")

                m = m.parentGroup
            }

            return Pair(targetModule, if (targetModule !is PsiElement) "Can't locate target module" else null)
        }
    }

}