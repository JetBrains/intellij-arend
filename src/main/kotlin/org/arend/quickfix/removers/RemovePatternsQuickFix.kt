package org.arend.quickfix.removers

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.childrenOfType
import org.arend.psi.*
import org.arend.psi.ext.*
import org.arend.util.ArendBundle

class RemovePatternsQuickFix(private val patternRef: SmartPsiElementPointer<ArendPattern>,
                             private val single: Boolean) : IntentionAction {
    override fun startInWriteAction() = true

    override fun getFamilyName() = text

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        return patternRef.element != null
    }

    override fun getText() = ArendBundle.message("arend.pattern.remove")

    private fun removeClauses(clause: ArendClause) {
        when (val pParent = clause.parent) {
            is ArendConstructor -> pParent.elim?.let {
                pParent.deleteChildRange(it, pParent.lastChild)
            }
            is ArendFunctionClauses -> {
                val body = pParent.parent
                if (body is ArendFunctionBody) {
                    val prev = body.prevSibling
                    if (prev is PsiWhiteSpace) {
                        prev.parent.deleteChildRange(prev, body)
                    } else {
                        body.delete()
                    }
                }
            }
        }
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val pattern = patternRef.element ?: return
        var first = pattern.extendLeft
        first.prevSibling?.let {
            if (it is LeafPsiElement && it.elementType == ArendElementTypes.COMMA) {
                first = it.extendLeft
            }
        }

        var last: PsiElement = pattern
        if (single) {
            last.extendRight.nextSibling?.let {
                if (it is LeafPsiElement && it.elementType == ArendElementTypes.COMMA) {
                    last = it
                }
            }
        } else {
            // If we need to remove all patterns, just remove all clauses
            if (first.prevSibling.let { it == null || it is LeafPsiElement && it.elementType == ArendElementTypes.PIPE }) {
                when (val parent = first.parent) {
                    is ArendClause -> removeClauses(parent)
                    is ArendConstructorClause -> (parent.parent as? ArendDataBody)?.delete()
                }
                return
            }

            // Remove everything till the next '=>', ')', '}', or '\as'
            while (true) {
                val next = last.nextSibling
                if (next == null || next is LeafPsiElement && next.elementType in listOf(ArendElementTypes.FAT_ARROW, ArendElementTypes.RPAREN, ArendElementTypes.RBRACE, ArendElementTypes.AS_KW)) {
                    break
                }
                last = next
            }

            // Keep whitespaces and comments
            if (last is PsiWhiteSpace || last is PsiComment) {
                last = last.extendLeft
                last.prevSibling?.let {
                    last = it
                }
            }
        }

        // Add a whitespace before '=>' or '\as'
        val parent = first.parent
        if (last.nextElement.let { it is LeafPsiElement && it.elementType in listOf(ArendElementTypes.FAT_ARROW, ArendElementTypes.AS_KW) }) {
            parent.addAfter(ArendPsiFactory(parent.project).createWhitespace(" "), last)
        }

        parent.deleteChildRange(first, last)
        val child = parent.childrenOfType<ArendPattern>().singleOrNull()
        if (parent is ArendPattern && child is ArendPattern && !parent.isTuplePattern) {
            parent.replace(child)
        }
    }
}
