package org.arend.refactoring.changeSignature

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.openapi.application.*
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiCodeFragment
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.descendantsOfType
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.changeSignature.ChangeSignatureDialogBase
import com.intellij.refactoring.ui.CodeFragmentTableCellEditorBase
import com.intellij.refactoring.ui.ComboBoxVisibilityPanel
import com.intellij.refactoring.ui.StringTableCellEditor
import com.intellij.ui.EditorTextField
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.table.TableView
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.Consumer
import org.arend.ArendFileType
import org.arend.naming.scope.ListScope
import org.arend.naming.scope.Scope
import org.arend.psi.ext.ArendDefFunction
import org.arend.psi.ext.ArendReferenceElement
import org.arend.psi.listener.ArendPsiChangeService
import org.arend.resolving.ArendResolveCache
import java.awt.Component
import java.util.Collections.singletonList
import javax.swing.DefaultListSelectionModel
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.event.ChangeEvent
import javax.swing.event.TableModelEvent
import javax.swing.table.TableCellEditor

class ArendChangeSignatureDialog(project: Project, val descriptor: ArendChangeSignatureDescriptor) :
    ChangeSignatureDialogBase<ArendParameterInfo, PsiElement, String, ArendChangeSignatureDescriptor, ArendChangeSignatureDialogParameterTableModelItem, ArendParameterTableModel>(project, descriptor, false, descriptor.method.context),
    ArendExpressionFragmentResolveListener {
    private var parametersPanel: JPanel? = null
    private lateinit var parameterToUsages: MutableMap<ArendChangeSignatureDialogParameterTableModelItem, MutableMap<ArendExpressionCodeFragment, MutableSet<TextRange>>>
    private lateinit var parameterToDependencies: MutableMap<ArendExpressionCodeFragment, MutableSet<ArendChangeSignatureDialogParameterTableModelItem>>
    private val docManager = PsiDocumentManager.getInstance(project)
    lateinit var commonTypeFragmentListener: ArendChangeSignatureCustomDocumentListener

    private fun clearParameter(fragment: ArendExpressionCodeFragment) {
        for (usageEntry in parameterToUsages) usageEntry.value.remove(fragment)
        parameterToDependencies.remove(fragment)
    }

    override fun expressionFragmentResolved(codeFragment: ArendExpressionCodeFragment) {
        val resolveCache = project.service<ArendResolveCache>()
        val referableToItem = HashMap<ArendChangeSignatureDialogParameter, ArendChangeSignatureDialogParameterTableModelItem>()
        for (item in myParametersTable.items) referableToItem[item.associatedReferable] = item

        clearParameter(codeFragment)

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
        updateToolbarButtons()
    }

    override fun updatePropagateButtons() {
        super.updatePropagateButtons()
        updateToolbarButtons()
    }

    override fun customizeParametersTable(table: TableView<ArendChangeSignatureDialogParameterTableModelItem>?) {
        super.customizeParametersTable(table)
    }

    override fun getFileType() = ArendFileType

    override fun createParametersInfoModel(descriptor: ArendChangeSignatureDescriptor) =
        ArendParameterTableModel( descriptor, this, {item: ArendChangeSignatureDialogParameterTableModelItem -> getParametersScope(item)}, myDefaultValueContext)

    override fun createRefactoringProcessor(): BaseRefactoringProcessor =
        ArendChangeSignatureProcessor(project, evaluateChangeInfo(myParametersTableModel))

    override fun createReturnTypeCodeFragment(): PsiCodeFragment {
        val referable = myMethod.method
        val returnExpression = when (referable) {
            is ArendDefFunction -> referable.returnExpr?.text ?: ""
            else -> ""
        }
        return ArendExpressionCodeFragment(myProject, returnExpression, getParametersScope(null), referable, this)
    }

    override fun createCallerChooser(title: String?, treeToReuse: Tree?, callback: Consumer<in MutableSet<PsiElement>>?) = null

    // TODO: add information about errors
    override fun validateAndCommitData(): String? = null

    private fun evaluateChangeInfo(parametersModel: ArendParameterTableModel): ArendChangeInfo {
        return ArendChangeInfo(parametersModel.items.map {  it.parameter }.toMutableList(), myReturnTypeCodeFragment?.text, myMethod.method)
    }

    override fun calculateSignature(): String =
        evaluateChangeInfo(myParametersTableModel).signature()

    override fun createVisibilityControl() = object : ComboBoxVisibilityPanel<String>("", arrayOf()) {}

    override fun createParametersPanel(hasTabsInDialog: Boolean): JPanel {
        commonTypeFragmentListener = ArendChangeSignatureCustomDocumentListener(this)
        myParametersTable = object : TableView<ArendChangeSignatureDialogParameterTableModelItem?>(myParametersTableModel) {
            override fun removeEditor() {
                clearEditorListeners()
                super.removeEditor()
            }

            override fun editingStopped(e: ChangeEvent) {
                super.editingStopped(e)
                val fragment = ((e.source as? CodeFragmentTableCellEditorBase)?.cellEditorValue as? ArendExpressionCodeFragment)
                val item = myParametersTable.items.firstOrNull() { it.typeCodeFragment == fragment }

                val codeAnalyzer = DaemonCodeAnalyzer.getInstance(project) as? DaemonCodeAnalyzerImpl
                if (codeAnalyzer != null && fragment != null && item != null) {
                    val depIndices = parameterToUsages[item]?.keys?.map { f -> myParametersTable.items.indexOfFirst { it.typeCodeFragment == f } }?.toSortedSet()

                    if (depIndices != null) for (i in depIndices.reversed()) invokeLater {
                        project.service<ArendPsiChangeService>().modificationTracker.incModificationCount()
                        invokeNameResolverHighlighting(i)
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
            for (l in oldModelListeners) l.tableChanged(ev)
            if (ev.type == TableModelEvent.UPDATE && ev.lastRow - ev.firstRow == 1 || ev.type == TableModelEvent.DELETE) { //Row swap
                //TODO: Think what really needs to be retypechecked
                highlightDependentFields(ev.lastRow)
            }
            updateToolbarButtons()
        }
        return parametersPanel!! //safe
    }

    fun refactorParameterNames(item: ArendChangeSignatureDialogParameterTableModelItem, newName: String) {
        val dataToWrite = HashMap<Document, Pair<Int, String>>()

         runReadAction {
             val usages = parameterToUsages[item]
             if (usages != null) {
                 val usagesAmendments = HashMap<ArendExpressionCodeFragment, MutableSet<TextRange>>()

                 for (entry in usages) {
                     val codeFragment = entry.key
                     val itemToModify = myParametersTable.items.firstOrNull { it.typeCodeFragment == codeFragment }
                     val itemIndex = itemToModify?.let { myParametersTable.items.indexOf(it) } ?: -1
                     val changes = entry.value.sortedBy { it.startOffset }
                     val updatedChanges = HashSet<TextRange>()
                     val textFile = docManager.getDocument(codeFragment)
                     if (textFile != null) {
                         var text = textFile.text
                         var delta = 0
                         for (change in changes) {
                             text = text.replaceRange(IntRange(change.startOffset + delta, change.endOffset - 1 + delta), newName)
                             val epsilon = newName.length - change.length
                             updatedChanges.add(TextRange(change.startOffset + delta, change.endOffset + delta + epsilon))
                             delta += epsilon
                         }
                         dataToWrite[textFile] = Pair(itemIndex, text)
                         usagesAmendments[codeFragment] = updatedChanges
                     }
                 }

                 for (amendment in usagesAmendments)
                     usages[amendment.key] = amendment.value
             }
        }

        ApplicationManager.getApplication().invokeAndWait({
            executeCommand {
                runWriteAction {
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

    fun getParameterTableItems() = myParametersTable.items

    private fun getParametersScope(item: ArendChangeSignatureDialogParameterTableModelItem?): () -> Scope = { ->
        val items = this.myParametersTableModel.items
        val limit = items.indexOfFirst { it == item }.let { if (it == -1) items.size else it }
        val params = items.take(limit).map { it.associatedReferable }
        ListScope(params)
    }

    private fun getTypeTextField(index: Int) = (this.myParametersTable.getCellEditor(index, 1) as? CodeFragmentTableCellEditorBase?)?.getTableCellEditorComponent(myParametersTable, myParametersTableModel.items[index].typeCodeFragment, false, 0, 0) as? EditorTextField

    private fun invokeNameResolverHighlighting(index: Int) {
        val fragment = if (index == -1) this.myReturnTypeCodeFragment else myParametersTableModel.items[index].typeCodeFragment
        val editorTextField = if (index == -1) this.myReturnTypeField else getTypeTextField(index)
        val codeAnalyzer = DaemonCodeAnalyzer.getInstance(project) as? DaemonCodeAnalyzerImpl
        val document = fragment?.let{ PsiDocumentManager.getInstance(project).getDocument(it) }
        if (fragment is ArendExpressionCodeFragment && codeAnalyzer != null && editorTextField != null && document != null) {
            editorTextField.addNotify()
            codeAnalyzer.restart(fragment)
            val textEditor = editorTextField.editor?.let{ TextEditorProvider.getInstance().getTextEditor(it) }
            if (textEditor != null) codeAnalyzer.runPasses(fragment, document, singletonList(textEditor), IntArray(0), true, null)
        }
    }

    private fun updateToolbarButtons() {
        val parametersPanel = parametersPanel ?: return
        val downButton = ToolbarDecorator.findDownButton(parametersPanel) ?: return
        val upButton = ToolbarDecorator.findUpButton(parametersPanel) ?: return

        val selectedIndices = this.myParametersTable.selectionModel.selectedIndices
        if (selectedIndices.size == 1) {
            val selectedIndex = selectedIndices.first()
            val currentItem = this.myParametersTableModel.items[selectedIndex]

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

    private fun highlightDependentFields(index: Int) { //TODO: Improve using calculated dependencies?; do not use direct highlighter invocation
        project.service<ArendPsiChangeService>().modificationTracker.incModificationCount()
        for (i in index until myParametersTable.items.size) invokeNameResolverHighlighting(i) // refactor me
        invokeNameResolverHighlighting(-1)
    }

    class ArendChangeSignatureCustomDocumentListener(val dialog: ArendChangeSignatureDialog): DocumentListener {
        private val documentsInstalled = HashSet<Document>()

        fun installDocumentListener(doc: Document) {
            if (documentsInstalled.contains(doc)) return
            documentsInstalled.add(doc)
            doc.addDocumentListener(this)
        }

        override fun documentChanged(e: DocumentEvent) {
            dialog.docManager.commitDocument(e.document)
            val eventFragment = dialog.docManager.getPsiFile(e.document)
            val rowIndex = dialog.myParametersTableModel.items.indexOfFirst { it.typeCodeFragment == eventFragment }
            if (rowIndex == -1) return
            dialog.myParametersTableModel.setValueAtWithoutUpdate(eventFragment, rowIndex, 1)

            if (eventFragment != null) (DaemonCodeAnalyzer.getInstance(dialog.project) as? DaemonCodeAnalyzerImpl)?.restart(eventFragment)
            dialog.updateSignature()
        }
    }
}