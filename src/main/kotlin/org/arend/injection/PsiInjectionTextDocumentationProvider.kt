package org.arend.injection

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile


class PsiInjectionTextDocumentationProvider : AbstractDocumentationProvider() {
    override fun getCustomDocumentationElement(editor: Editor, file: PsiFile, contextElement: PsiElement?, targetOffset: Int) =
        InjectedLanguageManager.getInstance(file.project).findInjectedElementAt(file, targetOffset)?.parent?.reference?.resolve()
}