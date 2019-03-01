package org.arend.refactoring.move

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
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
        for (c in container.subgroups) if (c is ArendGroup) {
            val memberInfo = ArendMemberInfo(c)
            if (membersToMove.contains(c)) memberInfo.isChecked = true
            sink.add(memberInfo)
        }
    }

    override fun doAction() {
        val sourceGroup = containerRef.element
        val elementsToMove = memberSelectionPanel.table.selectedMemberInfos.map { it.member }

        val locateResult: Pair<Group?, String?> = when (sourceGroup) {
            !is ChildGroup -> Pair(null, "Source module has invalid type")
            else -> locateTargetGroupWithChecks(targetFileTextField.text, targetModuleTextField.text, enclosingModule, sourceGroup, elementsToMove)
        }

        if (locateResult.second != null)
            CommonRefactoringUtil.showErrorMessage(MoveMembersImpl.REFACTORING_NAME, locateResult.second, HelpID.MOVE_MEMBERS, myProject) else
            invokeRefactoring(ArendStaticMemberRefactoringProcessor(project, {}, elementsToMove, sourceGroup as ChildGroup, locateResult.first as PsiElement, isOpenInEditor))
    }

    override fun getPreferredFocusedComponent(): JComponent? = targetFileTextField

    override fun getCbTitle(): String = "Open moved members in editor"

    override fun getMovePropertySuffix(): String = "Arend static member"

    override fun createCenterPanel(): JComponent? = initOpenInEditorCb()

    override fun createNorthPanel(): JComponent? = northPanel

    override fun getDimensionServiceKey(): String? = "#org.arend.refactoring.move.ArendMoveMembersDialog"

    companion object {
        private const val canNotLocateMessage = "Can not locate target module"

        fun simpleLocate(fileName: String, moduleName: String, ideaModule: Module): Group? {
            val configService = ArendModuleConfigService.getInstance(ideaModule) ?: return null
            val targetFile = configService.findArendFile(ModulePath.fromString(fileName)) ?: return null

            return if (moduleName.trim() == "") targetFile else targetFile.findGroupByFullName(moduleName.split("."))
        }

        fun locateTargetGroupWithChecks(fileName: String, moduleName: String, ideaModule: Module,
                                        sourceModule: ChildGroup, elementsToMove: List<ArendGroup?>): Pair<Group?, String?> {

            val targetModule = simpleLocate(fileName, moduleName, ideaModule) ?: return Pair(null, canNotLocateMessage)
            var m = targetModule as? ChildGroup
            if (m == sourceModule) return Pair(null, "Target module cannot coincide with the source module")

            while (m != null) {
                for (elementP in elementsToMove) if (m == elementP)
                    return Pair(null, "Target module cannot be a submodule of the member being moved")

                m = m.parentGroup
            }

            return Pair(targetModule, if (targetModule !is PsiElement) canNotLocateMessage else null)
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