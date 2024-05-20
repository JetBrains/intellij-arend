package org.arend.quickfix.removers

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.elementType
import org.arend.psi.ArendElementTypes.CLASSIFYING_KW
import org.arend.psi.deleteWithWhitespaces
import org.arend.util.ArendBundle

class RemoveClassifyingFieldQuickFix(private val classifyingElement: PsiElement?) : IntentionAction {
    override fun startInWriteAction(): Boolean = true

    override fun getFamilyName(): String = text

    override fun getText(): String = ArendBundle.message("arend.classifying.remove")

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = classifyingElement != null &&
            classifyingElement.elementType == CLASSIFYING_KW

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        classifyingElement?.deleteWithWhitespaces()
    }
}
