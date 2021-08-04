package org.arend.refactoring.changeSignature

import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.impl.source.PsiTypeCodeFragmentImpl
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.changeSignature.*
import com.intellij.refactoring.ui.ComboBoxVisibilityPanel
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.Consumer
import org.arend.ArendFileType

class ArendChangeSignatureDialog(
    project: Project,
    val descriptor: ArendSignatureDescriptor,
) : ChangeSignatureDialogBase<
        ArendParameterInfo,
        PsiMethod,
        String,
        ArendSignatureDescriptor,
        ParameterTableModelItemBase<ArendParameterInfo>,
        ArendParameterTableModel
        >(project, descriptor, false, descriptor.method) {


    override fun getFileType() = ArendFileType

    override fun createParametersInfoModel(d: ArendSignatureDescriptor) =
        ArendParameterTableModel(d, myDefaultValueContext)

    override fun createRefactoringProcessor(): BaseRefactoringProcessor? {
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
        return null
    }

    override fun validateAndCommitData(): String? {
        return "ValidateAndCommitData"
    }

    override fun calculateSignature(): String {
        return "calculateSignature"
    }

    override fun createVisibilityControl() = object : ComboBoxVisibilityPanel<String>("", arrayOf()) {}

    private fun createTypeCodeFragment(function: PsiElement): PsiCodeFragment {
        // or `return PsiTypeCodeFragmentImpl(function.project, true, "dummyName", function.text, 0, myDefaultValueContext)`
        return PsiTypeCodeFragmentImpl(function.project, true, "dummyName", function.text, 0, function.context)
    }

    private class Type
}