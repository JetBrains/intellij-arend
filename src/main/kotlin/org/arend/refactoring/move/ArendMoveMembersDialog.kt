package org.arend.refactoring.move

import com.intellij.ide.actions.CreateFileFromTemplateAction.createFileFromTemplate
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.*
import com.intellij.refactoring.HelpID
import com.intellij.refactoring.classMembers.MemberInfoBase
import com.intellij.refactoring.move.MoveDialogBase
import com.intellij.refactoring.move.moveMembers.MoveMembersImpl
import com.intellij.refactoring.ui.AbstractMemberSelectionPanel
import com.intellij.refactoring.ui.AbstractMemberSelectionTable
import com.intellij.refactoring.ui.MemberSelectionTable
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.ui.*
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.Alarm
import org.arend.ArendFileTypeInstance
import org.arend.codeInsight.ArendCodeInsightUtils
import org.arend.ext.module.ModulePath
import org.arend.module.config.ArendModuleConfigService
import org.arend.naming.reference.Referable
import org.arend.naming.scope.Scope
import org.arend.psi.*
import org.arend.psi.ext.*
import org.arend.refactoring.move.ArendMoveRefactoringProcessor.Companion.getUsagesToPreprocess
import org.arend.util.ArendBundle
import org.arend.util.FullName
import org.arend.util.aligned
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Toolkit
import java.util.*
import java.util.function.Predicate
import javax.swing.Icon
import javax.swing.JPanel
import javax.swing.JRadioButton
import javax.swing.SwingUtilities
import kotlin.math.roundToInt
import kotlin.collections.ArrayList
import kotlin.math.min

class ArendMoveMembersDialog(project: Project,
                             elements: List<ArendGroup>,
                             container: PsiElement,
                             private val enclosingModule: Module) : MoveDialogBase(project, false, true) {
    private val targetFileField: EditorTextField
    private val targetModuleField: EditorTextField
    private val centerPanel: JPanel
    private val containerRef: SmartPsiElementPointer<PsiElement>
    private val memberSelectionPanel: ArendMemberSelectionPanel
    private val dynamicGroup: JRadioButton
    private val staticGroup: JRadioButton
    private val sourceIsDynamic: Boolean?
    private val myUpdateSizeAlarm: Alarm

    init {
        title = "Move Arend Members"
        containerRef = SmartPointerManager.createPointer(container)
        val memberInfos = ArrayList<ArendMemberInfo>()

        val fullName = if (container is ArendGroup) {
            initMemberInfo(container, elements, memberInfos)
            container.fullName
        } else {
            null
        }

        targetFileField = EditorTextField(PsiDocumentManager.getInstance(project).getDocument(ArendLongNameCodeFragment(project, fullName?.module?.toString() ?: "", null)), project, ArendFileTypeInstance)
        targetModuleField = EditorTextField(PsiDocumentManager.getInstance(project).getDocument(ArendLongNameCodeFragment(project, fullName?.longName?.toString() ?: "", null)), project, ArendFileTypeInstance)

        memberSelectionPanel = ArendMemberSelectionPanel("Members to move", memberInfos)
        staticGroup = JRadioButton("Static")
        dynamicGroup = JRadioButton("Dynamic")
        lateinit var classPartRow: Row
        centerPanel = panel {
            row { cell(ScrollPaneFactory.createScrollPane(memberSelectionPanel, true)).align(Align.FILL)
            }.resizableRow()
            aligned("Target file: ", targetFileField)
            aligned("Target module: ", targetModuleField)

            buttonsGroup {
                classPartRow = row("Target part of the class") {
                    cell(staticGroup)
                    cell(dynamicGroup)
                }
            }
        }

        if (container is ArendDefClass) {
            sourceIsDynamic = determineClassPart(elements)
            classPartRow.enabled(true)
            if (sourceIsDynamic != null) {
                if (sourceIsDynamic) {
                    dynamicGroup.isSelected = true
                } else {
                    staticGroup.isSelected = true
                }
            }
        } else {
            sourceIsDynamic = null
            classPartRow.enabled(false)
            staticGroup.isSelected = true
        }

        val updater: () -> Unit = {
            classPartRow.enabled(classPartShouldBeEnabled())
        }

        val documentListener = object: com.intellij.openapi.editor.event.DocumentListener {
            override fun beforeDocumentChange(event: com.intellij.openapi.editor.event.DocumentEvent) { }

            override fun documentChanged(event: com.intellij.openapi.editor.event.DocumentEvent) { updater() }

            override fun bulkUpdateStarting(document: Document) { }

            override fun bulkUpdateFinished(document: Document) { }
        }

        targetFileField.document.addDocumentListener(documentListener)
        targetModuleField.document.addDocumentListener(documentListener)

        myUpdateSizeAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, myDisposable)

        init()
    }

    private fun classPartShouldBeEnabled() : Boolean {
        val fileName = targetFileField.text
        val moduleName = targetModuleField.text
        val locateResult = simpleLocate(fileName, moduleName, enclosingModule)
        return locateResult.second == LocateResult.LOCATE_OK && locateResult.first is ArendDefClass
    }

    private fun updateSize() {
        val screenHeight = Toolkit.getDefaultToolkit().screenSize.getHeight().toInt()

        val rowHeight = memberSelectionPanel.table.rowHeight
        val additionalHeight = (ADDITIONAL_BASE_HEIGHT / rowHeight).roundToInt()
        val height = size.height - memberSelectionPanel.height + memberSelectionPanel.getTitledSeparator().height +
                (memberSelectionPanel.memberInfo.size + 1) * rowHeight + additionalHeight
        setSize(size.width, min(screenHeight, height))
    }

    override fun beforeShowCallback() {
        SwingUtilities.invokeLater {
            if (myUpdateSizeAlarm.isDisposed) return@invokeLater
            myUpdateSizeAlarm.cancelAllRequests()
            myUpdateSizeAlarm.addRequest({
                if (myProject.isDisposed) return@addRequest
                PsiDocumentManager.getInstance(myProject)
                    .performLaterWhenAllCommitted { updateSize() }
            }, 50)
        }
    }

    private fun initMemberInfo(container: ArendGroup, membersToMove: List<ArendGroup>, sink: MutableList<ArendMemberInfo>) {
        val addToSink = {c: ArendGroup ->
            if (isMoveableGroup(c)) {
                val memberInfo = ArendMemberInfo(c)
                if (membersToMove.contains(c)) memberInfo.isChecked = true
                sink.add(memberInfo)
            }
        }

        container.statements.forEach { c ->
            val group = c.group
            if (group != null) addToSink(group)
        }
        if (container is ArendDefClass) container.classStatList.forEach { c -> c.definition?.let{ addToSink(it) } }
    }

    override fun doAction() {
        val sourceGroup = containerRef.element
        val elementsToMove = memberSelectionPanel.table.selectedMemberInfos.map { it.member }
        val fileName = targetFileField.text
        val moduleName = targetModuleField.text
        var targetContainer: ArendGroup? = null
        val targetIsDynamic: Boolean? = if (!classPartShouldBeEnabled()) null else {
            val isDynamic = dynamicGroup.isSelected
            val isStatic = staticGroup.isSelected
            when {
                isDynamic && !isStatic -> true
                !isDynamic && isStatic -> false
                else -> false
            }
        }

        val locateResult: Pair<ArendGroup?, LocateResult> = when (sourceGroup) {
            !is ArendGroup -> Pair(null, LocateResult.OTHER_ERROR)
            else -> locateTargetGroupWithChecks(fileName, moduleName, enclosingModule, sourceGroup, elementsToMove, sourceIsDynamic, targetIsDynamic)
        }

        var showErrorMessage = true
        if (locateResult.second != LocateResult.LOCATE_OK) {
            if (locateResult.second == LocateResult.CANT_FIND_FILE && moduleName.trim() == "") {
                val dirData = locateParentDirectory(targetFileField.text, enclosingModule)
                val directory = dirData?.first
                val newFileName = dirData?.second
                if (dirData != null && directory != null) {
                    showErrorMessage = false
                    val answer = Messages.showYesNoDialog(
                            myProject,
                            "Target file $fileName does not exist.\n Do you want to create a file named $newFileName within the directory ${directory.name}?",
                            MoveMembersImpl.getRefactoringName(),
                            Messages.getQuestionIcon())
                    if (answer == Messages.YES) {
                        val template = FileTemplateManager.getInstance(myProject).getInternalTemplate("Arend File")
                        targetContainer = createFileFromTemplate(newFileName, template, directory, null, false) as? ArendGroup
                    }
                }
            }
        } else {
            targetContainer = locateResult.first
            showErrorMessage = false
        }

        //Validate that dynamically inferred implicit parameters of functions being moved are known
        val problematicGroups = LinkedHashSet<PsiLocatedReferable>()
        if (!showErrorMessage && targetContainer != null) {
            getUsagesToPreprocess(elementsToMove, dynamicGroup.isSelected, targetContainer, HashMap(), problematicGroups)

            for (def in elementsToMove) if (def is ArendDefinition<*>) {
                val list = ArendCodeInsightUtils.getExternalParameters(def)
                if (list == null) problematicGroups.add(def)
            }

            if (problematicGroups.isNotEmpty()) {
                val builder = StringBuilder()
                for (group in problematicGroups.withIndex()) {
                    builder.append("`${group.value.name}`")
                    builder.append(if (group.index < problematicGroups.size - 1) ", " else " ")
                }

                val message = ArendBundle.message(if (problematicGroups.size > 1) "arend.refactoring.notTypecheckedMessage.partI.multiple" else "arend.refactoring.notTypecheckedMessage.partI.single", builder) +
                ArendBundle.message("arend.refactoring.notTypecheckedMessage.partII", builder)

                val answer = Messages.showYesNoDialog(
                    myProject,
                    message,
                    MoveMembersImpl.getRefactoringName(),
                    Messages.getQuestionIcon())

                if (answer == Messages.YES) {
                    problematicGroups.clear()
                }
            }
        }

        if (targetContainer != null && problematicGroups.isEmpty())
            invokeRefactoring(ArendMoveRefactoringProcessor(project, {}, elementsToMove, sourceGroup as ArendGroup, targetContainer, dynamicGroup.isSelected, isOpenInEditor))
        else if (showErrorMessage) CommonRefactoringUtil.showErrorMessage(MoveMembersImpl.getRefactoringName(), getLocateErrorMessage(locateResult.second), HelpID.MOVE_MEMBERS, myProject)
    }

    override fun getPreferredFocusedComponent() = targetFileField

    override fun getRefactoringId() = "Move Arend member"

    override fun createCenterPanel() = centerPanel

    override fun getDimensionServiceKey() = "#org.arend.refactoring.move.ArendMoveMembersDialog"

    enum class LocateResult {
        LOCATE_OK, CANT_FIND_FILE, CANT_FIND_MODULE, TARGET_EQUALS_SOURCE, TARGET_IS_SUBMODULE_OF_SOURCE, CLASSPART_UNSPECIFIED, OTHER_ERROR
    }

    companion object {
        private const val ADDITIONAL_BASE_HEIGHT = 100.0
        class FilteringScope(val scope: Scope, val predicate: (Referable) -> Boolean) : Scope {
            override fun find(pred: Predicate<Referable>?): Referable? {
                val result = scope.find(pred)
                return if (result != null && predicate.invoke(result)) result else null
            }

            override fun getElements(context: Scope.ScopeContext?): MutableCollection<out Referable> {
                return scope.getElements(context).filter { predicate.invoke(it) }.toMutableList()
            }

            override fun resolveNamespace(name: String): Scope? {
                return scope.resolveNamespace(name)?.let { FilteringScope(it, predicate) }
            }
        }

        fun isMoveableGroup(a: ArendGroup) = !(a is ArendDefFunction && a.functionKind.isUse)

        fun determineClassPart(elements: List<ArendGroup>): Boolean? {
            var isDynamic = true
            var isStatic = true
            for (e in elements) {
                if (e.parent is ArendClassStat) {
                    isStatic = false
                } else {
                    isDynamic = false
                }
            }
            return when {
                isStatic && !isDynamic -> false
                !isStatic && isDynamic -> true
                else -> null
            }
        }

        fun getLocateErrorMessage(lr: LocateResult): String = when (lr) {
            LocateResult.LOCATE_OK -> "No error"
            LocateResult.TARGET_EQUALS_SOURCE -> ArendBundle.message("arend.refactoring.move.targetEqualsSource")
            LocateResult.TARGET_IS_SUBMODULE_OF_SOURCE -> ArendBundle.message("arend.refactoring.move.targetSubmoduleSource")
            LocateResult.CANT_FIND_FILE, LocateResult.CANT_FIND_MODULE -> ArendBundle.message("arend.refactoring.move.canNotLocate")
            LocateResult.CLASSPART_UNSPECIFIED -> ArendBundle.message("arend.refactoring.move.unspecifiedPartOfTheClass")
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
            val targetFile = configService.findArendFile(ModulePath.fromString(fileName), withAdditional = false, withTests = true)
                ?: return Pair(null, LocateResult.CANT_FIND_FILE)

            return if (moduleName.trim().isEmpty()) Pair(targetFile, LocateResult.LOCATE_OK) else {
                val module = targetFile.findGroupByFullName(moduleName.split("."))
                Pair(module, if (module != null) LocateResult.LOCATE_OK else LocateResult.CANT_FIND_MODULE)
            }

        }

        fun locateTargetGroupWithChecks(fileName: String, moduleName: String, ideaModule: Module,
                                        sourceModule: ArendGroup, elementsToMove: List<ArendGroup?>,
                                        sourceIsDynamic: Boolean?, targetIsDynamic: Boolean?): Pair<ArendGroup?, LocateResult> {
            val locateResult = simpleLocate(fileName, moduleName, ideaModule)
            if (locateResult.second != LocateResult.LOCATE_OK) return locateResult
            var group = locateResult.first
            if (group is ArendDefClass && targetIsDynamic == null) return Pair(null, LocateResult.CLASSPART_UNSPECIFIED)
            if (group == sourceModule && (sourceIsDynamic == null || sourceIsDynamic == targetIsDynamic)) return Pair(null, LocateResult.TARGET_EQUALS_SOURCE)

            while (group != null) {
                for (elementP in elementsToMove) if (group == elementP)
                    return Pair(null, LocateResult.TARGET_IS_SUBMODULE_OF_SOURCE)

                group = group.parentGroup
            }

            return locateResult
        }
    }

}

class ArendMemberSelectionPanel(title: String, val memberInfo: List<ArendMemberInfo>) :
    AbstractMemberSelectionPanel<ArendGroup, ArendMemberInfo>() {
    private val myTable: ArendMemberSelectionTable
    private val titledSeparator: TitledSeparator

    init {
        layout = BorderLayout()

        myTable = ArendMemberSelectionTable(memberInfo)
        val scrollPane = ScrollPaneFactory.createScrollPane(myTable)
        scrollPane.preferredSize = Dimension(100, 100)
        titledSeparator = SeparatorFactory.createSeparator(title, myTable)
        add(titledSeparator, BorderLayout.NORTH)
        add(scrollPane, BorderLayout.CENTER)
    }

    override fun getTable(): AbstractMemberSelectionTable<ArendGroup, ArendMemberInfo> = myTable

    fun getTitledSeparator(): TitledSeparator = titledSeparator
}

class ArendMemberSelectionTable(memberInfos: Collection<ArendMemberInfo>) :
        AbstractMemberSelectionTable<ArendGroup, ArendMemberInfo>(memberInfos, null, null) {
    override fun getOverrideIcon(memberInfo: ArendMemberInfo?): Icon = MemberSelectionTable.EMPTY_OVERRIDE_ICON

    override fun setVisibilityIcon(memberInfo: ArendMemberInfo?, icon: RowIcon?) {}

    override fun getAbstractColumnValue(memberInfo: ArendMemberInfo?) = false

    override fun isAbstractColumnEditable(rowIndex: Int) = true
}

class ArendMemberInfo(member: ArendGroup) : MemberInfoBase<ArendGroup>(member) {
    override fun getDisplayName() = member.name ?: "???"
}