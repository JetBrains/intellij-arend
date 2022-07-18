package org.arend.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.castSafelyTo
import org.arend.psi.*
import org.arend.util.ArendBundle

class MakePatternExplicitQuickFix(private val atomPatternRef: SmartPsiElementPointer<PsiElement>,
                                  private val single: Boolean) : IntentionAction {
    override fun startInWriteAction() = true

    override fun getFamilyName() = text

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) =
            atomPatternRef.element != null

    override fun getText() = ArendBundle.message("arend.pattern.makeExplicit")

    private fun getTargetPattern(pattern: ArendAtomPattern) =
            pattern.patternList.singleOrNull()?.atomPatternList?.singleOrNull()
//    let { if (pattern.asPattern == null) it else null }

    private fun getImplicitPattern(atom: ArendAtomPattern): ArendAtomPattern? {
        return if (atom.lbrace == null) {
            val doubleParent = atom.parent.parent.castSafelyTo<ArendAtomPattern>() ?: return null
            return getImplicitPattern(doubleParent)
        } else {
            atom
        }
    }

    private fun makeExplicit(atomPattern: ArendAtomPattern) {
        val pattern = getImplicitPattern(atomPattern) ?: return
        val targetPattern = getTargetPattern(pattern)
        if (targetPattern != null) {
            pattern.replaceWithNotification(targetPattern)
            return
        }

//        val id = if (pattern.parent.castSafelyTo<ArendPattern>()?.asPattern == null && /*pattern.atomPattern?.type == null &&*/ pattern.patternList.isEmpty())
//            pattern.patternList.firstOrNull()?.longName /*?: pattern.longName*/
//        else null
//

        if (atomPattern.parent.castSafelyTo<ArendPattern>()?.atomPatternList != listOf(atomPattern)) {
            val lbrace = atomPattern.lbrace ?: return
            val rbrace = atomPattern.rbrace ?: return
            val parens = ArendPsiFactory(atomPattern.parent.project).createPairOfParens()
            atomPattern.addBefore(parens.first, lbrace)
            atomPattern.addBefore(atomPattern.patternList.single(), lbrace)
            atomPattern.addBefore(parens.second, lbrace)
            atomPattern.deleteChildRangeWithNotification(lbrace, rbrace)
        } else {
            atomPattern.parent.replaceWithNotification(pattern.patternList.single())
        }
//        when (val parent = atomPattern.parent) {
////            is ArendPattern ->
////                if (id != null && parent.asPattern == null) {
////                    atomPattern.replaceWithNotification(id)
////                } else {
////                    parent.replaceWithNotification(pattern)
////                }
//            is ArendPattern -> {
////                if (id != null) {
////                    atomPattern.replaceWithNotification(id)
////                } else {
//
//                }
//        }
    }

    private fun makeAllExplicit(): Boolean {
        val atomPattern = atomPatternRef.element ?: return false
        val parent = (atomPattern.parent as? ArendPattern)?.parent?.parent ?: return false
        var ok = false
        var node = parent.firstChild
        while (node != null) {
            if (node is ArendClause || node is ArendConstructorClause) {
                for (pattern in PsiTreeUtil.getChildrenOfTypeAsList(node, ArendPattern::class.java)) {
//                    pattern.atomPattern?.let {
//                        if (it.rbrace != null) {
//                            if (it == atomPattern) {
//                                ok = true
//                            }
//                            makeExplicit(it)
//                        }
//                    }
                }
            }
            node = node.nextSibling
        }

        return ok
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val element = atomPatternRef.element ?: return
        val atom = (if (element is ArendPattern) element.parent else element).castSafelyTo<ArendAtomPattern>() ?: return
        if (single || !makeAllExplicit()) {
            makeExplicit(atom)
        }
    }
}