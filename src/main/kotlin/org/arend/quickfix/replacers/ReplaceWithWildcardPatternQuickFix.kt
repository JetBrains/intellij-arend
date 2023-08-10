package org.arend.quickfix.replacers

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.arend.psi.*
import org.arend.psi.ext.ArendPattern
import org.arend.term.abs.Abstract
import org.arend.util.ArendBundle

class ReplaceWithWildcardPatternQuickFix(private val patternRef: SmartPsiElementPointer<PsiElement>): IntentionAction {
    override fun startInWriteAction(): Boolean = true

    override fun getFamilyName(): String = text

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = patternRef.element != null

    override fun getText(): String = ArendBundle.message("arend.pattern.remove")

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val pattern = patternRef.element as? Abstract.Pattern ?: return
        val factory = ArendPsiFactory(project)
        val patternLine = if (pattern.isExplicit) "_" else "{_}"
        val wildcardPattern: PsiElement? = when (pattern) {
            is ArendPattern -> factory.createClause(patternLine).descendantOfType<ArendPattern>()!!
            else -> null
        }

        if (wildcardPattern != null) (pattern as? PsiElement)?.replace(wildcardPattern)
    }
}