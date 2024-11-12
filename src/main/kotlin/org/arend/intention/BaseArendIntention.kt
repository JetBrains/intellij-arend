package org.arend.intention

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.psi.PsiFile
import org.arend.psi.ArendFile
import org.arend.util.checkArcFile

abstract class BaseArendIntention(text: String) : PsiElementBaseIntentionAction() {
    init {
        this.text = text
    }

    override fun getFamilyName() = text

    override fun checkFile(file: PsiFile?) = canModify(file)

    companion object {
        fun canModify(file: PsiFile?) = file is ArendFile && !checkArcFile(file.virtualFile) && BaseIntentionAction.canModify(file) && !file.isInjected
    }
}