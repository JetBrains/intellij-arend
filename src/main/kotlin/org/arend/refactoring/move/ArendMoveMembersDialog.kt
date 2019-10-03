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
import org.arend.psi.ArendDefClass
import org.arend.psi.ArendDefData
import org.arend.psi.ArendDefFunction
import org.arend.psi.ext.ArendFunctionalDefinition
import org.arend.psi.ext.impl.ArendGroup
import org.arend.psi.findGroupByFullName
import org.arend.term.abs.Abstract
import org.arend.util.FullName
import java.awt.BorderLayout
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextField

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

        val fullName = if (container is ArendGroup) {
            initMemberInfo(container, elements, memberInfos)
            FullName(container)
        } else {
            null
        }

        targetFileTextField = JTextField(fullName?.modulePath?.toString() ?: "")
        targetModuleTextField = JTextField(fullName?.longName?.toString() ?: "")
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

    private fun initMemberInfo(container: ArendGroup, membersToMove: List<ArendGroup>, sink: MutableList<ArendMemberInfo>) {
        val addToSink = {c: ArendGroup ->
            if (isMovable(c)) {
                val memberInfo = ArendMemberInfo(c)
                if (membersToMove.contains(c)) memberInfo.isChecked = true
                sink.add(memberInfo)
            }
        }

        container.subgroups.forEach { c -> addToSink(c) }
        if (container is ArendDefClass) container.classStatList.forEach { c -> c.definition?.let{ addToSink(it) } }
    }

    override fun doAction() {
        val sourceGroup = containerRef.element
        val elementsToMove = memberSelectionPanel.table.selectedMemberInfos.map { it.member }
        val fileName = targetFileTextField.text
        val moduleName = targetModuleTextField.text
        var targetContainer: ArendGroup? = null

        val locateResult: Pair<ArendGroup?, LocateResult> = when (sourceGroup) {
            !is ArendGroup -> Pair(null, LocateResult.OTHER_ERROR)
            else -> locateTargetGroupWithChecks(fileName, moduleName, enclosingModule, sourceGroup, elementsToMove)
        }

        var showErrorMessage = true
        if (locateResult.second != LocateResult.LOCATE_OK) {
            if (locateResult.second == LocateResult.CANT_FIND_FILE && moduleName.trim() == "") {
                val dirData = locateParentDirectory(targetFileTextField.text, enclosingModule)
                val directory = dirData?.first
                val newFileName = dirData?.second
                if (dirData != null && directory != null) {
                    showErrorMessage = false
                    val answer = Messages.showYesNoDialog(
                            myProject,
                            "Target file $fileName does not exist.\n Do you want to create the file named $newFileName within the directory ${directory.name}?",
                            MoveMembersImpl.REFACTORING_NAME,
                            Messages.getQuestionIcon())
                    if (answer == Messages.YES) {
                        val template = FileTemplateManager.getInstance(myProject).getInternalTemplate("Arend File")
                        targetContainer = createFileFromTemplate(newFileName, template, directory, null, false) as? ArendGroup
                    }
                }
            }
        } else {
            targetContainer = locateResult.first
        }

        if (targetContainer != null)
            invokeRefactoring(ArendStaticMemberRefactoringProcessor(project, {}, elementsToMove, sourceGroup as ArendGroup, targetContainer, isOpenInEditor)) else
            if (showErrorMessage) CommonRefactoringUtil.showErrorMessage(MoveMembersImpl.REFACTORING_NAME, getLocateErrorMessage(locateResult.second), HelpID.MOVE_MEMBERS, myProject)
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

        fun isMovable(a: ArendGroup) = (a !is ArendDefFunction || a.functionKw.useKw == null) &&
                (a !is Abstract.Definition || a.enclosingClass == null || a is ArendFunctionalDefinition || a is ArendDefData)

        fun getLocateErrorMessage(lr: LocateResult): String = when (lr) {
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
            val parentDir = list.subList(0, list.size - 1)
            val parentPath = ModulePath(parentDir)
            val dir = configService.findArendDirectory(parentPath)
            return Pair(dir, list.last())
        }

        fun simpleLocate(fileName: String, moduleName: String, ideaModule: Module): Pair<ArendGroup?, LocateResult> {
            val configService = ArendModuleConfigService.getInstance(ideaModule)
                    ?: return Pair(null, LocateResult.OTHER_ERROR)
            val targetFile = configService.findArendFile(ModulePath.fromString(fileName))
                    ?: return Pair(null, LocateResult.CANT_FIND_FILE)

            return if (moduleName.trim().isEmpty()) Pair(targetFile, LocateResult.LOCATE_OK) else {
                val module = targetFile.findGroupByFullName(moduleName.split("."))
                Pair(module, if (module != null) LocateResult.LOCATE_OK else LocateResult.CANT_FIND_MODULE)
            }

        }

        fun locateTargetGroupWithChecks(fileName: String, moduleName: String, ideaModule: Module,
                                        sourceModule: ArendGroup, elementsToMove: List<ArendGroup?>): Pair<ArendGroup?, LocateResult> {
            val locateResult = simpleLocate(fileName, moduleName, ideaModule)
            if (locateResult.second != LocateResult.LOCATE_OK) return locateResult

            var group = locateResult.first
            if (group == sourceModule) return Pair(null, LocateResult.TARGET_EQUALS_SOURCE)

            while (group != null) {
                for (elementP in elementsToMove) if (group == elementP)
                    return Pair(null, LocateResult.TARGET_IS_SUBMODULE_OF_SOURCE)

                group = group.parentGroup
            }

            return locateResult
        }
    }

}

class ArendMemberSelectionPanel(title: String, memberInfo: List<ArendMemberInfo>) :
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

class ArendMemberSelectionTable(memberInfos: Collection<ArendMemberInfo>) :
        AbstractMemberSelectionTable<ArendGroup, ArendMemberInfo>(memberInfos, null, null) {
    override fun getOverrideIcon(memberInfo: ArendMemberInfo?): Icon = MemberSelectionTable.EMPTY_OVERRIDE_ICON

    override fun setVisibilityIcon(memberInfo: ArendMemberInfo?, icon: RowIcon?) {}

    override fun getAbstractColumnValue(memberInfo: ArendMemberInfo?): Any? = false

    override fun isAbstractColumnEditable(rowIndex: Int): Boolean = true
}

class ArendMemberInfo(member: ArendGroup) : MemberInfoBase<ArendGroup>(member) {
    override fun getDisplayName() = member.name ?: "???"
}