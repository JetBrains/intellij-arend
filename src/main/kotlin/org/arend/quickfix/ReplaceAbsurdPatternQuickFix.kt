package org.arend.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

class ReplaceAbsurdPatternQuickFix: IntentionAction {
    override fun startInWriteAction(): Boolean = true

    override fun getFamilyName(): String = "arend.pattern"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = true

    override fun getText(): String  = "Replace with expected pattern constructors"

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}