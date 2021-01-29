package org.arend.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.arend.core.context.param.DependentLink
import org.arend.psi.ext.ArendCompositeElement
import org.arend.typechecking.error.local.ImpossibleEliminationError

class ImpossibleEliminationQuickFix(val error: ImpossibleEliminationError, val cause: SmartPsiElementPointer<ArendCompositeElement>) : IntentionAction {
    override fun startInWriteAction(): Boolean = true

    override fun getFamilyName(): String = "arend.patternmatching"

    override fun getText(): String = "Do patternmatching on the 'stuck' variable"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = error.clauseParameters != null // this prevents quickfix from showing in the "no matching constructor" quickfix

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        // Case 0: error is thrown on EmptyPattern
        // Case 2: error is thrown in computeParamSpec/reportNoClauses

        // general behavior: attempt to eliminate some variables so that
        println(DependentLink.Helper.toList(error.clauseParameters).map { it.toString() })
        println(cause.element?.text)
        println(error.dataCall)
    }
}