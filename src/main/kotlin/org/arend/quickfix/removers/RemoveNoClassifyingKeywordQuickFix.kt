package org.arend.quickfix.removers

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.elementType
import org.arend.psi.ArendElementTypes.NO_CLASSIFYING_KW
import org.arend.psi.deleteWithWhitespaces
import org.arend.util.ArendBundle

class RemoveNoClassifyingKeywordQuickFix(private val noClassifyingElement: PsiElement?) : IntentionAction {
    override fun startInWriteAction(): Boolean = true

    override fun getText(): String = ArendBundle.message("arend.noclassifying.remove")

    override fun getFamilyName(): String = text

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = noClassifyingElement != null &&
            noClassifyingElement.elementType == NO_CLASSIFYING_KW

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        noClassifyingElement?.deleteWithWhitespaces()
    }
}
