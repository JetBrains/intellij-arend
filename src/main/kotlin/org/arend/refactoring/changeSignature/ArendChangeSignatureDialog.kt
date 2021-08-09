package org.arend.refactoring.changeSignature

import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.changeSignature.*
import com.intellij.refactoring.ui.ComboBoxVisibilityPanel
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.Consumer
import org.arend.ArendFileType
import org.arend.psi.ArendPsiFactory

class ArendChangeSignatureDialog(
    project: Project,
    val descriptor: ArendSignatureDescriptor,
    private val changeInfo: ArendChangeInfo?
) : ChangeSignatureDialogBase<
        ArendParameterInfo,
        PsiElement,
        String,
        ArendSignatureDescriptor,
        ParameterTableModelItemBase<ArendParameterInfo>,
        ArendParameterTableModel
        >(project, descriptor, false, descriptor.method.context) {

    override fun getFileType() = ArendFileType

    override fun createParametersInfoModel(descriptor: ArendSignatureDescriptor) =
        ArendParameterTableModel(descriptor, myDefaultValueContext)

    override fun createRefactoringProcessor(): BaseRefactoringProcessor =
        ArendChangeSignatureProcessor(project, changeInfo)

    override fun createReturnTypeCodeFragment(): PsiCodeFragment = createTypeCodeFragment(myMethod.method)

    override fun createCallerChooser(
        title: String?,
        treeToReuse: Tree?,
        callback: Consumer<MutableSet<PsiElement>>?
    ): CallerChooserBase<PsiElement>? = null

    // TODO: add information about errors
    override fun validateAndCommitData(): String? = null

    override fun calculateSignature(): String = changeInfo?.signature() ?: ""

    override fun createVisibilityControl() = object : ComboBoxVisibilityPanel<String>("", arrayOf()) {}

    private fun createTypeCodeFragment(function: PsiElement): PsiCodeFragment {
        val factory = ArendPsiFactory(function.project)
        return factory.createFromText(function.text) as PsiCodeFragment
    }
}