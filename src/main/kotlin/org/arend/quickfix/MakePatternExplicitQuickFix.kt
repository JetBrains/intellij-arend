package org.arend.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.arend.psi.*

class MakePatternExplicitQuickFix(private val atomPattern: ArendAtomPattern) : IntentionAction {
    override fun startInWriteAction() = true

    override fun getFamilyName() = "arend.pattern"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) = true

    override fun getText() = "Make pattern explicit"

    private fun getAtom(pattern: ArendPattern) =
        pattern.atomPattern?.let { if (pattern.asPattern == null) it else null }

    private fun getImplicitPattern(atom: ArendAtomPattern): ArendPattern? =
        if (atom.lbrace == null) {
            val list = atomPattern.patternList
            if (list.size == 1) getAtom(list.first())?.let { getImplicitPattern(it) } else null
        } else {
            atom.patternList.firstOrNull()
        }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val pattern = getImplicitPattern(atomPattern) ?: return
        val atom = getAtom(pattern)
        if (atom != null) {
            atomPattern.replaceWithNotification(atom)
            return
        }

        val id = if (pattern.asPattern == null && pattern.colon == null && pattern.atomPatternOrPrefixList.isEmpty())
            pattern.defIdentifier ?: pattern.longName
        else null

        when (val parent = atomPattern.parent) {
            is ArendPattern ->
                if (id != null && parent.asPattern == null) {
                    atomPattern.replaceWithNotification(id)
                } else {
                    parent.replaceWithNotification(pattern)
                }
            is ArendAtomPatternOrPrefix ->
                if (id != null) {
                    atomPattern.replaceWithNotification(id)
                } else {
                    val lbrace = atomPattern.lbrace ?: return
                    val rbrace = atomPattern.rbrace ?: return
                    val parens = ArendPsiFactory(parent.project).createPairOfParens()
                    atomPattern.addBefore(parens.first, lbrace)
                    atomPattern.addBefore(pattern, lbrace)
                    atomPattern.addBefore(parens.second, lbrace)
                    atomPattern.deleteChildRangeWithNotification(lbrace, rbrace)
                }
        }
    }
}