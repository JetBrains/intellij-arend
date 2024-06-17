package org.arend.refactoring.changeSignature

import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.changeSignature.*
import com.intellij.refactoring.ui.ComboBoxVisibilityPanel
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.Consumer
import org.arend.ArendFileTypeInstance
import org.arend.psi.ArendPsiFactory

class ArendChangeSignatureDialog(project: Project, val descriptor: ArendChangeSignatureDescriptor) :
    ChangeSignatureDialogBase<ArendParameterInfo, PsiElement, String, ArendChangeSignatureDescriptor, ParameterTableModelItemBase<ArendParameterInfo>, ArendParameterTableModel>(project, descriptor, false, descriptor.method.context) {

    override fun getFileType() = ArendFileTypeInstance

    override fun createParametersInfoModel(descriptor: ArendChangeSignatureDescriptor) =
        ArendParameterTableModel(descriptor, myDefaultValueContext)

    override fun createRefactoringProcessor(): BaseRefactoringProcessor =
        ArendChangeSignatureProcessor(project, evaluateChangeInfo(myParametersTableModel))

    override fun createReturnTypeCodeFragment(): PsiCodeFragment =
        createTypeCodeFragment(myMethod.method)

    override fun createCallerChooser(title: String?, treeToReuse: Tree?, callback: Consumer<in MutableSet<PsiElement>>?) = null

    // TODO: add information about errors
    override fun validateAndCommitData(): String? = null

    private fun evaluateChangeInfo(parametersModel: ArendParameterTableModel): ArendChangeInfo {
        return ArendChangeInfo(parametersModel.items.map {  it.parameter }.toMutableList(), myMethod.method)
    }

    override fun calculateSignature(): String =
        evaluateChangeInfo(myParametersTableModel).signature()


    override fun createVisibilityControl() = object : ComboBoxVisibilityPanel<String>("", arrayOf()) {}

    private fun createTypeCodeFragment(function: PsiElement): PsiCodeFragment {
        val factory = ArendPsiFactory(function.project)
        return factory.createFromText(function.text) as PsiCodeFragment
    }
}