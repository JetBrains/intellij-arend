package org.arend.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.arend.psi.ArendPsiFactory
import org.arend.psi.ext.*
import org.arend.psi.parentOfType
import org.arend.util.ArendBundle

class MakePatternExplicitQuickFix(private val patternRef: SmartPsiElementPointer<ArendPattern>,
                                  private val single: Boolean) : IntentionAction {
    override fun startInWriteAction() = true

    override fun getFamilyName() = text

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) =
            patternRef.element != null

    override fun getText() = ArendBundle.message("arend.pattern.makeExplicit")


    private fun makeExplicit(pattern: ArendPattern) {
        val factory = ArendPsiFactory(pattern.project)
        pattern.firstChild.delete()
        pattern.lastChild.delete()
        if (pattern.parent is ArendPattern && pattern.sequence.isNotEmpty()) {
            val newPattern = factory.createPattern("(${pattern.text})")
            pattern.replace(newPattern)
        }
    }

    private fun makeAllExplicit(): Boolean {
        val pattern = patternRef.element ?: return false
        val parentClauseOwner = pattern.parentOfType<ArendClause>()?.parent

        var ok = false
        for (child in parentClauseOwner?.children ?: emptyArray()) {
            if (child !is ArendClause) continue
            for (patternComponent in child.patterns) {
                if (!patternComponent.isExplicit) {
                    if (patternComponent == pattern) {
                        ok = true
                    }
                    makeExplicit(patternComponent)
                }
            }
        }
        return ok
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val element = patternRef.element ?: return
        if (single || !makeAllExplicit()) {
            makeExplicit(element)
        }
    }
}