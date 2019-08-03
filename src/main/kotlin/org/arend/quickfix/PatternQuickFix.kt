package org.arend.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.arend.psi.*
import org.arend.psi.ext.ArendPatternImplMixin

class RemovePattern(private val pattern: ArendPatternImplMixin) : IntentionAction {
    override fun startInWriteAction() = true

    override fun getFamilyName() = "arend.pattern"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) = true

    override fun getText() = "Remove excessive patterns"

    private fun removeClauses(clause: ArendClause) {
        when (val pParent = clause.parent) {
            is ArendConstructor -> pParent.elim?.let {
                pParent.deleteChildRangeWithNotification(it, pParent.lastChild)
            }
            is ArendFunctionClauses -> {
                val body = pParent.parent
                if (body is ArendFunctionBody || body is ArendInstanceBody) {
                    val prev = body.prevSibling
                    if (prev is PsiWhiteSpace) {
                        prev.parent.deleteChildRangeWithNotification(prev, body)
                    } else {
                        body.deleteWithNotification()
                    }
                }
            }
        }
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        var first = pattern.prevRelevant
        if (first is LeafPsiElement && first.elementType == ArendElementTypes.COMMA) {
            val prev = first.prevRelevant // remove irrelevant elements before comma
            if (prev != first) {
                first = prev.nextSibling
            }
        } else {
            first = pattern
        }

        // if there are no patterns left, remove all clauses
        if (first == pattern || first is LeafPsiElement && first.elementType == ArendElementTypes.PIPE) {
            when (val parent = first.parent) {
                is ArendClause -> {
                    removeClauses(parent)
                    return
                }
                is ArendConstructorClause -> {
                    (parent.parent as? ArendDataBody)?.deleteWithNotification()
                    return
                }
            }
        }

        var last: PsiElement = pattern
        while (true) {
            val next = last.nextSibling // remove everything till the next '=>', ')', '}', or '\as'
            if (next == null || next is LeafPsiElement && (next.elementType in listOf(ArendElementTypes.FAT_ARROW, ArendElementTypes.RPAREN, ArendElementTypes.RBRACE, ArendElementTypes.AS_KW))) {
                break
            }
            last = next
        }
        if (last is PsiWhiteSpace || last is PsiComment) {
            last = last.prevRelevant
        }

        first.parent.deleteChildRangeWithNotification(first, last)
    }
}
