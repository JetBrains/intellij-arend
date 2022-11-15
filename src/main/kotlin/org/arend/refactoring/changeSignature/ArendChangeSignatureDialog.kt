package org.arend.refactoring.changeSignature

import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.changeSignature.ChangeSignatureDialogBase
import com.intellij.refactoring.ui.ComboBoxVisibilityPanel
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.Consumer
import org.arend.ArendFileType
import org.arend.psi.ext.*

class ArendChangeSignatureDialog(project: Project, val descriptor: ArendChangeSignatureDescriptor) :
    ChangeSignatureDialogBase<ArendParameterInfo, PsiElement, String, ArendChangeSignatureDescriptor, ArendChangeSignatureDialogParameterTableModelItem, ArendParameterTableModel>(project, descriptor, false, descriptor.method.context) {

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
}