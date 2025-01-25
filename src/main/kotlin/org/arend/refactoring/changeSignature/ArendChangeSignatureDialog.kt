package org.arend.refactoring.changeSignature

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.concurrency.resetThreadContext
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.*
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiCodeFragment
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.descendants
import com.intellij.psi.util.descendantsOfType
import com.intellij.psi.util.elementType
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.changeSignature.ChangeSignatureDialogBase
import com.intellij.refactoring.ui.CodeFragmentTableCellEditorBase
import com.intellij.refactoring.ui.ComboBoxVisibilityPanel
import com.intellij.refactoring.ui.StringTableCellEditor
import com.intellij.ui.EditorTextField
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.table.TableView
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.Consumer
import org.arend.ArendFileTypeInstance
import org.arend.ext.module.LongName
import org.arend.naming.reference.LocatedReferable
import org.arend.naming.scope.MergeScope
import org.arend.naming.scope.Scope
import org.arend.naming.scope.local.ListScope
import org.arend.psi.ArendCodeFragmentController
import org.arend.psi.ArendElementTypes
import org.arend.psi.ArendExpressionCodeFragment
import org.arend.psi.ext.ArendFunctionDefinition
import org.arend.psi.ext.ArendReferenceElement
import org.arend.psi.listener.ArendPsiChangeService
import org.arend.psi.oneLineText
import org.arend.refactoring.NsCmdRefactoringAction
import org.arend.resolving.ArendResolveCache
import org.arend.util.FileUtils.isCorrectDefinitionName
import java.awt.Component
import java.util.Collections.singletonList
import javax.swing.DefaultListSelectionModel
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.event.ChangeEvent
import javax.swing.event.TableModelEvent
import javax.swing.table.TableCellEditor
import kotlin.math.roundToInt

class ArendChangeSignatureDialog(project: Project,
                                 val descriptor: ArendChangeSignatureDescriptor):
    ChangeSignatureDialogBase<ArendTextualParameter, PsiElement, String, ArendChangeSignatureDescriptor, ArendChangeSignatureDialogParameterTableModelItem, ArendParameterTableModel>(project, descriptor, false, descriptor.method.context),
    ArendCodeFragmentController {
    private lateinit var parametersPanel: JPanel
    private lateinit var parameterToUsages: MutableMap<ArendChangeSignatureDialogParameterTableModelItem, MutableMap<ArendExpressionCodeFragment, MutableSet<TextRange>>>
    private lateinit var parameterToDependencies: MutableMap<ArendExpressionCodeFragment, MutableSet<ArendChangeSignatureDialogParameterTableModelItem>>
    lateinit var commonTypeFragmentListener: ArendChangeSignatureCustomDocumentListener
    private lateinit var deferredNsCmds : MutableList<NsCmdRefactoringAction>

    private fun deleteFragmentDependencyData(fragment: ArendExpressionCodeFragment, includingDependencies: Boolean = false) {
        if (includingDependencies) {
            val obsoleteItem = parameterToUsages.keys.firstOrNull { it.typeCodeFragment == fragment }
            if (obsoleteItem != null) parameterToUsages.remove(obsoleteItem)
            for (dependencyEntry in parameterToDependencies) dependencyEntry.value.removeAll {it.typeCodeFragment == fragment}
        }

        for (usageEntry in parameterToUsages) usageEntry.value.remove(fragment)
        parameterToDependencies.remove(fragment)
    }

    override fun expressionFragmentResolved(codeFragment: ArendExpressionCodeFragment) {
        val resolveCache = project.service<ArendResolveCache>()
        val referableToItem = HashMap<ArendChangeSignatureDialogParameter, ArendChangeSignatureDialogParameterTableModelItem>()
        for (item in myParametersTable.items) referableToItem[item.associatedReferable] = item

        deleteFragmentDependencyData(codeFragment)

        val newDependencies = HashSet<ArendChangeSignatureDialogParameterTableModelItem>()
        parameterToDependencies[codeFragment] = newDependencies
        val refs = codeFragment.descendantsOfType<ArendReferenceElement>().toList()

        for (ref in refs) {
            val target = resolveCache.getCached(ref)
            val item = referableToItem[target]
            if (item != null) {
                newDependencies.add(item)
                var p = parameterToUsages[item]
                if (p == null) {p = HashMap(); parameterToUsages[item] = p }
                var s = p[codeFragment]
                if (s == null) {s = HashSet(); p[codeFragment] = s}
                s.add(ref.textRange)
            }
        }
    }

    override fun scopeModified(deferredNsCmd: NsCmdRefactoringAction) {
        deferredNsCmds.add(deferredNsCmd)
    }

    override fun getFragmentScope(codeFragment: ArendExpressionCodeFragment): Scope {
        val items = this.myParametersTableModel.items
        val limit = items.indexOfFirst { it.typeCodeFragment == codeFragment }.let { if (it == -1) items.size else it }
        val params = items.take(limit).map { it.associatedReferable }
        val localScope = ListScope(params)
        return if (deferredNsCmds.isEmpty()) localScope else MergeScope(singletonList(localScope) + deferredNsCmds.map { it.getAmendedScope() })
    }

    override fun updatePropagateButtons() {
        super.updatePropagateButtons()
        updateToolbarButtons()
    }

    override fun customizeParametersTable(table: TableView<ArendChangeSignatureDialogParameterTableModelItem>?) {
        super.customizeParametersTable(table)
    }

    override fun getFileType() = ArendFileTypeInstance

    override fun createParametersInfoModel(descriptor: ArendChangeSignatureDescriptor) =
        ArendParameterTableModel( descriptor, this, myDefaultValueContext)

    override fun createRefactoringProcessor(): BaseRefactoringProcessor =
        ArendChangeSignatureProcessor(project, evaluateChangeInfo(myParametersTableModel), isPreviewUsages)

    override fun createReturnTypeCodeFragment(): PsiCodeFragment {
        val referable = myMethod.method
        val expr = ArendChangeInfo.getReturnExpr(referable)?.copy()
        return ArendExpressionCodeFragment(myProject, expr?.oneLineText ?: "", referable, this)
    }

    override fun createCallerChooser(title: String?, treeToReuse: Tree?, callback: Consumer<in MutableSet<PsiElement>>?) = null

    override fun validateAndCommitData(): String? {
        val builder = StringBuilder()
        val documentManager = PsiDocumentManager.getInstance(myProject)
        val rawErrors = ArrayList<HighlightInfo>()
        val isElim = (myMethod.method as? ArendFunctionDefinition<*>)?.body?.elim != null

        val newNameCorrect = isCorrectDefinitionName(LongName(singletonList(this.methodName)))
        if (!newNameCorrect) return RefactoringBundle.message("text.identifier.invalid", this.methodName)

        for (item in myParametersTableModel.items) {
            if (item.parameter.name.let { it.isEmpty() || isElim && it == "_" }) //We may need to transform "with-body" to "elim-body" so "_" may turn into a problem
                return RefactoringBundle.message("refactoring.introduce.parameter.invalid.name", item.parameter.name)
        }

        val allPsiTargets = HashSet<LocatedReferable>()

        fun processFragment(fragment: ArendExpressionCodeFragment) {
            val refElements = fragment.descendantsOfType<ArendReferenceElement>()
            for (ref in refElements) {
                val t = ref.resolve
                if (t is LocatedReferable)
                    allPsiTargets.add(t)
            }
        }
        (myReturnTypeCodeFragment as? ArendExpressionCodeFragment)?.let { processFragment(it) }
        for (item in myParametersTable.items) (item.typeCodeFragment as? ArendExpressionCodeFragment)?.let{processFragment(it)}

        /* Validate namespace commands to be invoked upon refactoring start; purge unused namespace commands */
        val unusedNsCmds = HashSet<NsCmdRefactoringAction>()
        for (nsCmd in this.deferredNsCmds) if (nsCmd.getAmendedScope().elements.intersect(allPsiTargets).isEmpty()) unusedNsCmds.add(nsCmd)
        this.deferredNsCmds.removeAll(unusedNsCmds)

        /* Validate that code fragments for parameter types do not contain resolving errors */
        var hasErrors = false
        fun checkFragment(fragment: ArendExpressionCodeFragment, locationDescription: String) {
            val document = documentManager.getDocument(fragment)!!
            DaemonCodeAnalyzerEx.processHighlights(document, myProject, HighlightSeverity.ERROR, 0, document.textLength) {
                hasErrors = true
                builder.append("${it.description} $locationDescription at ${TextRange.create(it)}\n")
                rawErrors.add(it); false
            }
        }

        for (item in myParametersTable.items) checkFragment((item.typeCodeFragment as ArendExpressionCodeFragment), "in the type expression for \"${item.parameter.name}\"")
        if (myReturnTypeCodeFragment?.text?.isNotEmpty() == true) checkFragment(myReturnTypeCodeFragment as ArendExpressionCodeFragment, "in the return expression")

        if (!hasErrors) return null

        val result = Messages.showOkCancelDialog(myProject,
            "There are errors: \n${builder}\n Do you wish to continue?",
            RefactoringBundle.message("changeSignature.refactoring.name"),
            Messages.getOkButton(),
            Messages.getCancelButton(),
            Messages.getWarningIcon())

        return if (result == Messages.OK) null else EXIT_SILENTLY
    }

    private fun updateSize() {
        val rowHeight = myParametersTable.rowHeight
        val additionalHeight = (ADDITIONAL_BASE_HEIGHT / rowHeight).roundToInt()
        val height = size.height - parametersPanel.height + (myParametersTable.items.size + 2) * rowHeight + additionalHeight
        setSize(size.width, height)
    }

    override fun beforeShowCallback() {
        SwingUtilities.invokeLater { updateSize() }
    }

    private fun evaluateChangeInfo(parametersModel: ArendParameterTableModel): ArendChangeInfo {
        return ArendChangeInfo(ArendParametersInfo(myMethod.method, parametersModel.items.map {  it.parameter }.toMutableList()), myReturnTypeCodeFragment?.text, myNameField.text, myMethod.method, deferredNsCmds)
    }

    override fun calculateSignature(): String =
        evaluateChangeInfo(myParametersTableModel).getSignature()

    override fun createVisibilityControl() = object : ComboBoxVisibilityPanel<String>("", arrayOf()) {}

    override fun createParametersPanel(hasTabsInDialog: Boolean): JPanel {
        commonTypeFragmentListener = ArendChangeSignatureCustomDocumentListener(this)
        deferredNsCmds = ArrayList()
        myParametersTable = object : TableView<ArendChangeSignatureDialogParameterTableModelItem?>(myParametersTableModel) {
            override fun removeEditor() {
                clearEditorListeners()
                super.removeEditor()
            }

            override fun editingStopped(e: ChangeEvent) {
                super.editingStopped(e)
                when (val src = e.source) {
                    is StringTableCellEditor -> {
                        val i = myParametersTable.selectionModel.selectedIndices.firstOrNull()
                        val newName = if (i != null) myParametersTableModel.items.getOrNull(i)?.parameter?.name else null
                        if (i != null) invokeLater {
                            resetThreadContext().use { _ ->
                                val deps = HashSet<Int>()
                                if (newName == null || myReturnTypeCodeFragment?.text?.contains(newName) == true) deps.add(-1)
                                for (j in i+1 until myParametersTable.items.size) {
                                    val psi = myParametersTableModel.items.getOrNull(j)?.typeCodeFragment
                                    if (psi != null) {
                                        val children = psi.descendants().filter { it.elementType == ArendElementTypes.ID }.map { it.text }.toSet()
                                        if (newName == null || children.contains(newName))
                                            deps.add(j)
                                    }
                                }
                                invalidateIndices(deps)
                            }
                        }
                    }
                    is CodeFragmentTableCellEditorBase -> {
                        val fragment = (src.cellEditorValue as? ArendExpressionCodeFragment)
                        val item = myParametersTable.items.firstOrNull { it.typeCodeFragment == fragment }

                        val codeAnalyzer = DaemonCodeAnalyzer.getInstance(project) as? DaemonCodeAnalyzerImpl
                        if (codeAnalyzer != null && fragment != null && item != null)
                            invokeLater {
                                invalidateIndices(calculateUsagesOf(singletonList(item)))
                            }
                    }
                }
            }

            private fun clearEditorListeners() {
                val editor = getCellEditor()
                if (editor is StringTableCellEditor) {
                    editor.clearListeners()
                } else if (editor is CodeFragmentTableCellEditorBase) {
                    editor.clearListeners()
                }
            }

            override fun prepareEditor(editor: TableCellEditor, row: Int, column: Int): Component {
                if (editor is StringTableCellEditor ) {
                    editor.addDocumentListener(object: DocumentListener {
                        override fun documentChanged(event: DocumentEvent) {
                            myParametersTableModel.setValueAtWithoutUpdate(myParametersTable.cellEditor.cellEditorValue, row, column)
                            updateSignature()
                        }
                    })
                } else if (editor is CodeFragmentTableCellEditorBase) {
                    editor.addDocumentListener(commonTypeFragmentListener)
                }
                return super.prepareEditor(editor, row, column)
            }
        }

        myParametersTable.setShowGrid(false)
        myParametersTable.cellSelectionEnabled = true
        myParametersTable.selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        myParametersTable.selectionModel.setSelectionInterval(0, 0)
        myParametersTable.surrendersFocusOnKeystroke = true

        parametersPanel = ToolbarDecorator.createDecorator(tableComponent).createPanel()
        myPropagateParamChangesButton.isEnabled = false
        myPropagateParamChangesButton.isVisible = false
        myParametersTableModel.addTableModelListener(mySignatureUpdater)
        customizeParametersTable(myParametersTable)

        parameterToUsages = HashMap(); parameterToDependencies = HashMap()
        for (i in 0 until myParametersTable.items.size) invokeNameResolverHighlighting(i)

        val selectionModel = (this.myParametersTable.selectionModel as DefaultListSelectionModel)
        val oldSelectionListeners = selectionModel.listSelectionListeners
        val oldModelListeners = this.myParametersTableModel.tableModelListeners

        for (l in oldSelectionListeners) selectionModel.removeListSelectionListener(l)
        for (l in oldModelListeners) this.myParametersTableModel.removeTableModelListener(l)

        this.myParametersTable.selectionModel.addListSelectionListener { ev ->
            for (l in oldSelectionListeners) l.valueChanged(ev)
            updateToolbarButtons()
        }

        this.myParametersTableModel.addTableModelListener { ev ->
            resetThreadContext().use {
                for (l in oldModelListeners) l.tableChanged(ev)
                if (ev.type == TableModelEvent.UPDATE && ev.lastRow - ev.firstRow == 1) { //Row swap
                    invokeNameResolverHighlighting(ev.lastRow)
                } else if (ev.type == TableModelEvent.DELETE) { //Row deletion
                    val obsoleteTableItems = parameterToUsages.filter { !myParametersTableModel.items.contains(it.key) }
                    invokeLater { invalidateIndices(calculateUsagesOf(obsoleteTableItems.map { it.key })) }
                    for (item in obsoleteTableItems) (item.key.typeCodeFragment as? ArendExpressionCodeFragment)?.let {
                        deleteFragmentDependencyData(it, true)
                    }
                }
                updateToolbarButtons()
            }
        }
        return parametersPanel
    }

    fun calculateUsagesOf(items: List<ArendChangeSignatureDialogParameterTableModelItem>): Set<Int> {
        val result = HashSet<Int>()
        for (item in items) {
            val indices = parameterToUsages[item]?.keys?.map { f -> myParametersTable.items.indexOfFirst { it.typeCodeFragment == f } }
            if (indices != null) result.addAll(indices)
            val index = myParametersTable.items.indexOf(item)
            if (index != -1) result.add(index)
        }
        return result
    }
    fun invalidateIndices(depIndices: Set<Int>) {
        for (i in depIndices.toSortedSet().reversed()) {
            service<ArendPsiChangeService>().definitionModificationTracker.incModificationCount()
            invokeNameResolverHighlighting(i)
        }
    }

    fun validateUsages(item: ArendChangeSignatureDialogParameterTableModelItem, oldName: String): Boolean {
        var hasValidUsage = false
        val fragments = parameterToUsages[item]?.keys ?: return false
        for (fragment in fragments) {
            val rangesToRename = parameterToUsages[item]!![fragment] //safe
            val text = fragment.text
            val invalidRanges = ArrayList<TextRange>()
            if (rangesToRename != null) for (range in rangesToRename) {
                if (range.endOffset <= text.length && text.substring(range.startOffset, range.endOffset) == oldName) {
                    hasValidUsage = true
                } else {
                    invalidRanges.add(range)
                }
            }
            rangesToRename?.removeAll(invalidRanges.toSet())
        }
        return hasValidUsage
    }
    fun refactorParameterNames(item: ArendChangeSignatureDialogParameterTableModelItem, newName: String) {
        val dataToWrite = HashMap<Document, Pair<Int, String>>()

         runReadAction {
             val affectedFragments = parameterToUsages[item]?.keys
             if (affectedFragments != null) {
                 var text: String? = null
                 var delta = 0

                 open class UpdateTextRangeTask(val mapToUpdate: MutableSet<TextRange>, val range: TextRange): RefactoringTask {
                     override fun getStartOffset(): Int = range.startOffset
                     override fun execute() {
                         mapToUpdate.remove(range)
                         mapToUpdate.add(TextRange(range.startOffset + delta, range.endOffset + delta))
                     }
                 }
                 class UpdateNameTask(mapToUpdate: MutableSet<TextRange>, range: TextRange): UpdateTextRangeTask(mapToUpdate, range)  {
                     override fun execute() {
                         val epsilon = newName.length - range.length
                         mapToUpdate.remove(range)
                         mapToUpdate.add(TextRange(range.startOffset + delta, range.endOffset + delta + epsilon))
                         text = text?.replaceRange(IntRange(range.startOffset + delta, range.endOffset - 1 + delta), newName)
                         delta += epsilon
                     }
                 }

                 for (codeFragment in affectedFragments) {
                     val textFile = PsiDocumentManager.getInstance(project).getDocument(codeFragment)
                     val rangesToRename = parameterToUsages[item]!![codeFragment] //safe
                     val tasks = parameterToDependencies[codeFragment]?.map { parameterToUsages[it]?.get(codeFragment)?.let { m -> m.map { r ->
                         if (rangesToRename?.contains(r) == true) UpdateNameTask(m, r) else UpdateTextRangeTask(m, r)
                     } } ?: emptyList() }?.flatten()?: emptyList()

                     val itemToModify = myParametersTable.items.firstOrNull { it.typeCodeFragment == codeFragment }
                     val itemIndex = itemToModify?.let { myParametersTable.items.indexOf(it) } ?: -1

                     if (textFile != null) {
                         text = textFile.text
                         delta = 0
                         for (task in tasks.sortedBy { it.getStartOffset() }) task.execute()
                         dataToWrite[textFile] = Pair(itemIndex, text!!)
                     }
                 }
             }
        }

        ApplicationManager.getApplication().invokeAndWait({
            executeCommand {
                runWriteAction {
                    val docManager = PsiDocumentManager.getInstance(project)
                    for (e in dataToWrite) {
                        val textFile = e.key
                        val (itemIndex, text) = e.value
                        textFile.replaceString(0, textFile.text.length, text)
                        docManager.commitDocument(textFile)
                        val updatedPsi = docManager.getPsiFile(textFile)
                        if (itemIndex != -1 )
                            myParametersTableModel.setValueAtWithoutUpdate(updatedPsi, itemIndex, 1)
                    }
                }
            }

            myParametersTable.repaint()
            myReturnTypeField.repaint()
            updateSignature()
        }, ModalityState.defaultModalityState())
    }

    fun getParameterTableItems(): MutableList<ArendChangeSignatureDialogParameterTableModelItem> = myParametersTable.items

    private fun getTypeTextField(index: Int) = (this.myParametersTable.getCellEditor(index, 1) as? CodeFragmentTableCellEditorBase?)?.getTableCellEditorComponent(myParametersTable, myParametersTableModel.items[index].typeCodeFragment, false, 0, 0) as? EditorTextField

    private fun invokeNameResolverHighlighting(index: Int) {
        val fragment = if (index == -1) this.myReturnTypeCodeFragment else if (index >= myParametersTableModel.items.size) return else myParametersTableModel.items[index].typeCodeFragment
        val editorTextField = if (index == -1) this.myReturnTypeField else getTypeTextField(index)
        val codeAnalyzer = DaemonCodeAnalyzer.getInstance(project) as? DaemonCodeAnalyzerImpl
        val document = fragment?.let{ PsiDocumentManager.getInstance(project).getDocument(it) }
        if (fragment is ArendExpressionCodeFragment && codeAnalyzer != null && editorTextField != null && document != null) {
            editorTextField.addNotify()
            codeAnalyzer.restart(fragment)
            val textEditor = editorTextField.editor?.let{ TextEditorProvider.getInstance().getTextEditor(it) }
            if (textEditor != null) codeAnalyzer.runPasses(fragment, document, textEditor, IntArray(0), true, null)
        }
    }

    private fun updateToolbarButtons() {
        val downButton = ToolbarDecorator.findDownButton(parametersPanel) ?: return
        val upButton = ToolbarDecorator.findUpButton(parametersPanel) ?: return

        val selectedIndices = this.myParametersTable.selectionModel.selectedIndices
        if (selectedIndices.size == 1 && this.myParametersTableModel.items.size > 0) {
            val selectedIndex = selectedIndices.first()
            val currentItem = this.myParametersTableModel.items.getOrNull(selectedIndex) ?: return

            val dependencyChecker = { pI: ArendChangeSignatureDialogParameterTableModelItem, cI: ArendChangeSignatureDialogParameterTableModelItem ->
                !(parameterToDependencies[cI.typeCodeFragment]?.contains(pI) ?: false)
            }
            if (selectedIndex > 0) {
                val prevItem = this.myParametersTableModel.items[selectedIndex - 1]
                upButton.isEnabled = dependencyChecker.invoke(prevItem, currentItem)
            }
            if (selectedIndex < this.myParametersTableModel.items.size - 1) {
                val nextItem = this.myParametersTableModel.items[selectedIndex + 1]
                downButton.isEnabled = dependencyChecker.invoke(currentItem, nextItem)
            }
        }
        myParametersTable?.repaint()
    }

    class ArendChangeSignatureCustomDocumentListener(private val dialog: ArendChangeSignatureDialog): DocumentListener {
        private val documentsInstalled = HashSet<Document>()

        fun installDocumentListener(doc: Document) {
            if (documentsInstalled.contains(doc)) return
            documentsInstalled.add(doc)
            doc.addDocumentListener(this)
        }

        override fun documentChanged(e: DocumentEvent) {
            val docManager = PsiDocumentManager.getInstance(dialog.myProject)
            val eventFragment = docManager.getPsiFile(e.document)
            val rowIndex = dialog.myParametersTableModel.items.indexOfFirst { it.typeCodeFragment == eventFragment }
            if (rowIndex == -1) return
            dialog.myParametersTableModel.setValueAtWithoutUpdate(eventFragment, rowIndex, 1)

            if (eventFragment != null) (DaemonCodeAnalyzer.getInstance(dialog.project) as? DaemonCodeAnalyzerImpl)?.restart(eventFragment)
            dialog.updateSignature()
        }
    }

    interface RefactoringTask {
        fun getStartOffset(): Int
        fun execute()
    }

    companion object {
        const val ADDITIONAL_BASE_HEIGHT = 280.0
    }
}