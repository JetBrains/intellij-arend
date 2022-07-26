package org.arend.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.util.castSafelyTo
import org.arend.psi.*
import org.arend.util.ArendBundle
import org.arend.psi.parser.api.ArendPattern as ArendPattern

class MakePatternExplicitQuickFix(private val patternRef: SmartPsiElementPointer<ArendPattern>,
                                  private val single: Boolean) : IntentionAction {
    override fun startInWriteAction() = true

    override fun getFamilyName() = text

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) =
            patternRef.element != null

    override fun getText() = ArendBundle.message("arend.pattern.makeExplicit")


    private fun makeExplicit(pattern: ArendPattern) {
        val factory = ArendPsiFactory(pattern.project)
        pattern.firstChild.deleteWithNotification()
        pattern.lastChild.deleteWithNotification()
        if (pattern.parent is ArendPattern && pattern.sequence.isNotEmpty()) {
            val newPattern = factory.createPattern("(${pattern.text})")
            pattern.replaceWithNotification(newPattern)
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
                    makeExplicit(patternComponent as ArendPattern)
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