package org.arend.refactoring.changeSignature

import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.impl.source.PsiTypeCodeFragmentImpl
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.changeSignature.*
import com.intellij.refactoring.ui.JavaComboBoxVisibilityPanel
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.Consumer
import org.arend.ArendFileType

class ArendChangeSignatureDialog(
    project: Project,
    val descriptor: ArendSignatureDescriptor,
) : ChangeSignatureDialogBase<
        ParameterInfoImpl,
        PsiMethod,
        String,
        ArendSignatureDescriptor,
        ParameterTableModelItemBase<ParameterInfoImpl>,
        ArendParameterTableModel
        >(project, descriptor, false, descriptor.method) {


    override fun getFileType() = ArendFileType

    override fun createParametersInfoModel(d: ArendSignatureDescriptor): ArendParameterTableModel {
        return ArendParameterTableModel(d, myDefaultValueContext)
    }

    override fun createRefactoringProcessor(): BaseRefactoringProcessor? {
        println("createRefactoringProcessor")
        return null
    }

    override fun createReturnTypeCodeFragment(): PsiCodeFragment? {
        return createTypeCodeFragment(myMethod.function)
    }

    override fun createCallerChooser(
        title: String?,
        treeToReuse: Tree?,
        callback: Consumer<MutableSet<PsiMethod>>?
    ): CallerChooserBase<PsiMethod>? {
        println("createCallerChooser")
        return null
    }

    override fun validateAndCommitData(): String? {
        return "ValidateAndCommitData"
    }

    override fun calculateSignature(): String {
        return "calculateSignature"
    }

    override fun createVisibilityControl() = JavaComboBoxVisibilityPanel()

    private fun createTypeCodeFragment(function: PsiElement): PsiCodeFragment {
        return PsiTypeCodeFragmentImpl(function.project, true, "name", function.text, 0, myDefaultValueContext)
    }
}