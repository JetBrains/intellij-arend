package org.arend.refactoring.changeSignature

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.changeSignature.ChangeSignatureDialogBase
import com.intellij.refactoring.ui.CodeFragmentTableCellEditorBase
import com.intellij.refactoring.ui.ComboBoxVisibilityPanel
import com.intellij.ui.EditorTextField
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.Consumer
import org.arend.ArendFileType
import org.arend.psi.ext.*
import java.util.Collections.singletonList
import javax.swing.JPanel

class ArendChangeSignatureDialog(project: Project, val descriptor: ArendChangeSignatureDescriptor) :
    ChangeSignatureDialogBase<ArendParameterInfo, PsiElement, String, ArendChangeSignatureDescriptor, ArendChangeSignatureDialogParameterTableModelItem, ArendParameterTableModel>(project, descriptor, false, descriptor.method.context) {
    val returnTypeFragmentLocation = object: FragmentLocation {
        override fun getCodeFragment(): ArendChangeSignatureDialogCodeFragment = this@ArendChangeSignatureDialog.myReturnTypeCodeFragment as ArendChangeSignatureDialogCodeFragment

        override fun getEditor(): Editor = this@ArendChangeSignatureDialog.myReturnTypeField.editor!!
    }
    override fun getFileType() = ArendFileType

    override fun createParametersInfoModel(descriptor: ArendChangeSignatureDescriptor) =
        ArendParameterTableModel(descriptor, myDefaultValueContext)

    override fun createRefactoringProcessor(): BaseRefactoringProcessor =
        ArendChangeSignatureProcessor(project, evaluateChangeInfo(myParametersTableModel))

    override fun createReturnTypeCodeFragment(): PsiCodeFragment {
        val referable = myMethod.method
        val returnExpression = when (referable) {
            is ArendDefFunction -> referable.returnExpr?.text ?: ""
            else -> ""
        }
        return ArendChangeSignatureDialogCodeFragment(myProject, returnExpression, referable, myParametersTableModel, null)
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
        val result = super.createParametersPanel(hasTabsInDialog)
        val codeAnalyzer = DaemonCodeAnalyzer.getInstance(project)
        if (codeAnalyzer is DaemonCodeAnalyzerImpl) {
            for ((index, item) in myParametersTableModel.items.withIndex()) {
                val fragment = item.typeCodeFragment
                val document = PsiDocumentManager.getInstance(project).getDocument(fragment)
                val cellEditor = this.myParametersTable.getCellEditor(index, 1) as? CodeFragmentTableCellEditorBase
                if (cellEditor != null && document != null) {
                    val textFieldEditor = cellEditor.getTableCellEditorComponent(myParametersTable, fragment, false, 0, 0) as? EditorTextField
                    textFieldEditor?.addNotify()
                    val textFieldTextEditor = textFieldEditor?.editor?.let{ TextEditorProvider.getInstance().getTextEditor(it) }
                    if (textFieldTextEditor != null) codeAnalyzer.runPasses(fragment, document, singletonList(textFieldTextEditor), IntArray(0), true, null)
                }
            }
        }
        return result
    }

    companion object {
        class ParameterLocation(val item: ArendChangeSignatureDialogParameterTableModelItem): FragmentLocation {
            override fun getCodeFragment(): ArendChangeSignatureDialogCodeFragment = item.typeCodeFragment

            override fun getEditor(): Editor {
                TODO("Not yet implemented")
            }
        }
    }
}