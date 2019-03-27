package org.arend.formatting

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

class ArendTypedHandlerDelegate: TypedHandlerDelegate() {

    override fun charTyped(c: Char, project: Project, editor: Editor, file: PsiFile): Result {
        if (c == '(')  /*Needed to prevent automatic reformat */ return Result.STOP

        return super.charTyped(c, project, editor, file)
    }
}