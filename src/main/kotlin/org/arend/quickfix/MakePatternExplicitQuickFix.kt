package org.arend.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.PsiTreeUtil
import org.arend.psi.*

class MakePatternExplicitQuickFix(private val atomPatternRef: SmartPsiElementPointer<ArendAtomPattern>,
                                  private val single: Boolean) : IntentionAction {
    override fun startInWriteAction() = true

    override fun getFamilyName() = "arend.pattern"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) = atomPatternRef.element != null

    override fun getText() = "Make pattern explicit"

    private fun getAtom(pattern: ArendPattern) =
            pattern.atomPattern?.let { if (pattern.asPattern == null) it else null }

    private fun getImplicitPattern(atom: ArendAtomPattern): ArendPattern? {
        val atomPattern = atomPatternRef.element ?: return null
        return if (atom.lbrace == null) {
            val list = atomPattern.patternList
            if (list.size == 1) getAtom(list.first())?.let { getImplicitPattern(it) } else null
        } else {
            atom.patternList.firstOrNull()
        }
    }

    private fun makeExplicit(atomPattern: ArendAtomPattern) {
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

    private fun makeAllExplicit(): Boolean {
        val atomPattern = atomPatternRef.element ?: return false
        val parent = (atomPattern.parent as? ArendPattern)?.parent?.parent ?: return false

        var ok = false
        var node = parent.firstChild
        while (node != null) {
            if (node is ArendClause || node is ArendConstructorClause) {
                for (pattern in PsiTreeUtil.getChildrenOfTypeAsList(node, ArendPattern::class.java)) {
                    pattern.atomPattern?.let {
                        if (it.rbrace != null) {
                            if (it == atomPattern) {
                                ok = true
                            }
                            makeExplicit(it)
                        }
                    }
                }
            }
            node = node.nextSibling
        }

        return ok
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val atomPattern = atomPatternRef.element ?: return
        if (single || !makeAllExplicit()) {
            makeExplicit(atomPattern)
        }
    }
}