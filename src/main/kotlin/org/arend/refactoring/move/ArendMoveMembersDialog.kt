package org.arend.refactoring.move

import com.intellij.ide.actions.CreateFileFromTemplateAction.createFileFromTemplate
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.refactoring.HelpID
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.classMembers.MemberInfoBase
import com.intellij.refactoring.move.MoveDialogBase
import com.intellij.refactoring.move.moveMembers.MoveMembersImpl
import com.intellij.refactoring.ui.AbstractMemberSelectionPanel
import com.intellij.refactoring.ui.AbstractMemberSelectionTable
import com.intellij.refactoring.ui.MemberSelectionTable
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.ui.RowIcon
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SeparatorFactory
import com.intellij.ui.layout.panel
import org.arend.module.ModulePath
import org.arend.module.config.ArendModuleConfigService
import org.arend.psi.ArendDefFunction
import org.arend.psi.ArendFile
import org.arend.psi.ext.fullName
import org.arend.psi.ext.impl.ArendGroup
import org.arend.psi.findGroupByFullName
import org.arend.term.group.ChildGroup
import org.arend.term.group.Group
import java.awt.BorderLayout
import javax.swing.*

class ArendMoveMembersDialog(project: Project,
                             elements: List<ArendGroup>,
                             container: PsiElement,
                             private val enclosingModule: Module) : MoveDialogBase(project, false) {
    private val targetFileTextField: JTextField
    private val targetModuleTextField: JTextField
    private val northPanel: JPanel
    private val containerRef: SmartPsiElementPointer<PsiElement>
    private val memberSelectionPanel: ArendMemberSelectionPanel

    init {
        title = "Move Arend static members"
        containerRef = SmartPointerManager.createPointer(container)
        val memberInfos = ArrayList<ArendMemberInfo>()

        val names = when (container) {
            is ArendFile -> {
                initMemberInfo(container, elements, memberInfos)
                Pair(container.fullName, "")
            }
            is ArendGroup -> {
                initMemberInfo(container, elements, memberInfos)
                val file = container.getContainingFile() as? ArendFile
                Pair(file?.modulePath.toString(), container.fullName)
            }
            else -> {
                null
            }
        }

        targetFileTextField = JTextField(names?.first ?: "")
        targetModuleTextField = JTextField(names?.second ?: "")
        memberSelectionPanel = ArendMemberSelectionPanel("Members to move", memberInfos)

        northPanel = panel {
            row {
                memberSelectionPanel()
            }
            row("Target file: ") {
                targetFileTextField()
            }
            row("Target module: ") {
                targetModuleTextField()
            }
        }

        init()
    }

    private fun initMemberInfo(container: ChildGroup, membersToMove: List<ArendGroup>, sink: MutableList<ArendMemberInfo>) {
        for (c in container.subgroups) if (c is ArendGroup && isMovable(c)) {
            val memberInfo = ArendMemberInfo(c)
            if (membersToMove.contains(c)) memberInfo.isChecked = true
            sink.add(memberInfo)
        }
    }

    override fun doAction() {
        val sourceGroup = containerRef.element
        val elementsToMove = memberSelectionPanel.table.selectedMemberInfos.map { it.member }
        val fileName = targetFileTextField.text
        val moduleName = targetModuleTextField.text

        val locateResult: Pair<Group?, LocateResult> = when (sourceGroup) {
            !is ChildGroup -> Pair(null, LocateResult.OTHER_ERROR)
            else -> locateTargetGroupWithChecks(fileName, moduleName, enclosingModule, sourceGroup, elementsToMove)
        }

        if (locateResult.second != LocateResult.LOCATE_OK) {
            var success = false
            if (locateResult.second == LocateResult.CANT_FIND_FILE && moduleName.trim() == "") {
                val dirData = locateParentDirectory(targetFileTextField.text, enclosingModule)
                val directory = dirData?.first
                if (dirData != null && directory != null) {
                    val answer = Messages.showYesNoDialog(
                            myProject,
                            RefactoringBundle.message("class.0.does.not.exist", fileName),
                            MoveMembersImpl.REFACTORING_NAME,
                            Messages.getQuestionIcon())
                    if (answer == Messages.YES) {
                        val template = FileTemplateManager.getInstance(myProject).getInternalTemplate("Arend File")
                        val targetFile = createFileFromTemplate(dirData.second, template, directory, null, false)
                        if (targetFile != null) {
                            invokeRefactoring(ArendStaticMemberRefactoringProcessor(project, {}, elementsToMove, sourceGroup as ChildGroup, targetFile, isOpenInEditor))
                            success = true
                        }
                    }
                }

            }
            if (!success) CommonRefactoringUtil.showErrorMessage(MoveMembersImpl.REFACTORING_NAME, getLocateErrorMessage(locateResult.second), HelpID.MOVE_MEMBERS, myProject)
        }  else
            invokeRefactoring(ArendStaticMemberRefactoringProcessor(project, {}, elementsToMove, sourceGroup as ChildGroup, locateResult.first as PsiElement, isOpenInEditor))
    }

    override fun getPreferredFocusedComponent(): JComponent? = targetFileTextField

    override fun getCbTitle(): String = "Open moved members in editor"

    override fun getMovePropertySuffix(): String = "Arend static member"

    override fun createCenterPanel(): JComponent? = initOpenInEditorCb()

    override fun createNorthPanel(): JComponent? = northPanel

    override fun getDimensionServiceKey(): String? = "#org.arend.refactoring.move.ArendMoveMembersDialog"

    enum class LocateResult {
        LOCATE_OK, CANT_FIND_FILE, CANT_FIND_MODULE, TARGET_EQUALS_SOURCE, TARGET_IS_SUBMODULE_OF_SOURCE, OTHER_ERROR
    }

    companion object {
        private const val canNotLocateMessage = "Can not locate target module"
        private const val targetEqualsSource = "Target module cannot coincide with the source module"
        private const val targetSubmoduleSource = "Target module cannot be a submodule of the member being moved"

        fun isMovable(a : ArendGroup) = (a !is ArendDefFunction || a.useKw == null)

        fun getLocateErrorMessage(lr : LocateResult): String = when (lr) {
            LocateResult.LOCATE_OK -> "No error"
            LocateResult.TARGET_EQUALS_SOURCE -> targetEqualsSource
            LocateResult.TARGET_IS_SUBMODULE_OF_SOURCE -> targetSubmoduleSource
            LocateResult.CANT_FIND_FILE, LocateResult.CANT_FIND_MODULE -> canNotLocateMessage
            else -> "Other locate error"
        }

        fun locateParentDirectory(fileName: String, ideaModule: Module): Pair<PsiDirectory?, String>? {
            val configService = ArendModuleConfigService.getInstance(ideaModule) ?: return null
            val modulePath = ModulePath.fromString(fileName)
            val list = modulePath.toList()
            if (list.size == 0) return null
            val parentDir = list.subList(0, list.size-1)
            val parentPath = ModulePath(parentDir)
            val dir = configService.findArendFilesAndDirectories(parentPath).filterIsInstance<PsiDirectory>().lastOrNull()
            return Pair(dir, list.last())
        }

        fun simpleLocate(fileName: String, moduleName: String, ideaModule: Module): Pair<Group?, LocateResult> {
            val configService = ArendModuleConfigService.getInstance(ideaModule) ?: return Pair(null, LocateResult.OTHER_ERROR)
            val targetFile = configService.findArendFile(ModulePath.fromString(fileName)) ?: return Pair(null,LocateResult.CANT_FIND_FILE)

            return if (moduleName.trim() == "") Pair(targetFile, LocateResult.LOCATE_OK) else {
                val module = targetFile.findGroupByFullName(moduleName.split("."))
                Pair(module, if (module != null) LocateResult.LOCATE_OK else LocateResult.CANT_FIND_MODULE)
            }

        }

        fun locateTargetGroupWithChecks(fileName: String, moduleName: String, ideaModule: Module,
                                        sourceModule: ChildGroup, elementsToMove: List<ArendGroup?>): Pair<Group?, LocateResult> {
            val locateResult = simpleLocate(fileName, moduleName, ideaModule)
            if (locateResult.second != LocateResult.LOCATE_OK) return locateResult

            var m = locateResult as? ChildGroup
            if (m == sourceModule) return Pair(null, LocateResult.TARGET_EQUALS_SOURCE)

            while (m != null) {
                for (elementP in elementsToMove) if (m == elementP)
                    return Pair(null, LocateResult.TARGET_IS_SUBMODULE_OF_SOURCE)

                m = m.parentGroup
            }

            return locateResult
        }
    }

}

class ArendMemberSelectionPanel(title: String, memberInfo: List<ArendMemberInfo>):
        AbstractMemberSelectionPanel<ArendGroup, ArendMemberInfo>() {
    private val myTable: ArendMemberSelectionTable

    init {
        layout = BorderLayout()

        myTable = ArendMemberSelectionTable(memberInfo)
        val scrollPane = ScrollPaneFactory.createScrollPane(myTable)
        add(SeparatorFactory.createSeparator(title, myTable), BorderLayout.NORTH)
        add(scrollPane, BorderLayout.CENTER)
    }

    override fun getTable(): AbstractMemberSelectionTable<ArendGroup, ArendMemberInfo> = myTable
}

class ArendMemberSelectionTable(memberInfos: Collection<ArendMemberInfo>):
        AbstractMemberSelectionTable<ArendGroup, ArendMemberInfo>(memberInfos, null, null) {
    override fun getOverrideIcon(memberInfo: ArendMemberInfo?): Icon = MemberSelectionTable.EMPTY_OVERRIDE_ICON

    override fun setVisibilityIcon(memberInfo: ArendMemberInfo?, icon: RowIcon?) {}

    override fun getAbstractColumnValue(memberInfo: ArendMemberInfo?): Any? = false

    override fun isAbstractColumnEditable(rowIndex: Int): Boolean = true
}

class ArendMemberInfo(member: ArendGroup): MemberInfoBase<ArendGroup>(member) {
    override fun getDisplayName(): String {
        return member.name?: "???"
    }
}