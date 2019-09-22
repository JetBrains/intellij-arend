package org.arend.quickfix.removers

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.arend.psi.*
import org.arend.psi.ext.ArendPatternImplMixin

class ReplaceWithWildcardPatternQuickFix(private val patternPointer: SmartPsiElementPointer<ArendPatternImplMixin>): IntentionAction {
    override fun startInWriteAction(): Boolean = true

    override fun getFamilyName(): String = "arend.pattern"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = patternPointer.element != null

    override fun getText(): String = "Remove redundant pattern"

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val pattern = patternPointer.element ?: return
        val factory = ArendPsiFactory(project)
        val patternLine = if (pattern.isExplicit) "_" else "{_}"
        val wildcardPattern: PsiElement? = when (pattern) {
            is ArendPattern -> factory.createClause(patternLine).childOfType<ArendPattern>()!!
            is ArendAtomPatternOrPrefix -> factory.createAtomPattern(patternLine)
            else -> null
        }

        if (wildcardPattern != null) pattern.replaceWithNotification(wildcardPattern)
    }
}