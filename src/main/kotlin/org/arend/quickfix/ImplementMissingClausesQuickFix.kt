package org.arend.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.arend.core.pattern.BindingPattern
import org.arend.core.pattern.ConstructorPattern
import org.arend.core.pattern.EmptyPattern
import org.arend.typechecking.error.local.MissingClausesError
import org.arend.core.pattern.Pattern
import org.arend.psi.*
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.PsiLocatedReferable

class ImplementMissingClausesQuickFix(private val missingClausesError : MissingClausesError, val cause: ArendCompositeElement) : IntentionAction {
    private val clauses = ArrayList<ArendClause>()
    private var anchor : PsiElement? = null

    override fun startInWriteAction(): Boolean = true

    override fun getFamilyName(): String = "arend.patternmatching"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        val occupiedNames = HashMap<String, Int>()
        val psiFactory = ArendPsiFactory(project)
        var error = false
        clauses.clear()

        for (clause in missingClausesError.missingClauses) {
            if (error) break
            val clauseText = buildString {
                val iterator = clause.iterator()
                while (iterator.hasNext()) {
                    val pattern = iterator.next()
                    val str = transformPattern(pattern, occupiedNames, false)
                    if (str != null) append(str) else {
                        error = true
                        break
                    }
                    if (iterator.hasNext()) append(", ")
                }
            }
            clauses.add(psiFactory.createClause(clauseText))
        }

        anchor = when (cause) {
            is ArendDefFunction -> cause.functionBody?.functionClauses?.lastChild
            is ArendCaseExpr -> cause.clauseList.lastOrNull() ?: cause.lbrace
            else -> null
        }

        return !error && (anchor != null)
    }

    override fun getText(): String = "Implement missing clauses"

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val psiFactory = ArendPsiFactory(project)
        val anchorVal = anchor
        if (anchorVal != null) insertClauses(psiFactory, anchorVal, clauses)
    }

    companion object {
        fun insertClauses(psiFactory: ArendPsiFactory, anchor: PsiElement, clauses: List<ArendClause>) {
            val anchorParent = anchor.parent
            if (anchorParent != null) for (clause in clauses) {
                val pipe = clause.findPrevSibling()
                var currAnchor : PsiElement? = null
                if (pipe != null) currAnchor = anchorParent.addAfter(pipe, anchor)
                anchorParent.addBefore(psiFactory.createWhitespace("\n"), currAnchor)
                currAnchor = anchorParent.addAfterWithNotification(clause, currAnchor)
                anchorParent.addBefore(psiFactory.createWhitespace(" "), currAnchor)
            }
        }

        fun transformPattern(pattern: Pattern, occupiedNames: HashMap<String, Int>, enforceSurroundWithParen: Boolean): String? {
            when (pattern) {
                is ConstructorPattern -> {
                    val definition = pattern.definition
                    val argumentPatterns = pattern.patterns.patternList.map { transformPattern(it, occupiedNames, definition != null) }
                    val returnString = if (definition == null) {
                        buildString {
                            append("(")
                            val iterator = argumentPatterns.iterator()
                            while (iterator.hasNext()) {
                                append(iterator.next())
                                if (iterator.hasNext()) append(", ")
                                iterator.next()
                            }
                            append(")")
                        }
                    } else {
                        val referable = PsiLocatedReferable.fromReferable(definition.referable) /* debug here */
                        buildString {
                            append(if (referable != null) referable.name else definition.name)
                            for (argument in argumentPatterns) append(" $argument")
                        }
                    }

                    return if (enforceSurroundWithParen) "($returnString)" else returnString
                }

                is BindingPattern -> {
                    val name = pattern.binding.name ?: return "_"

                    if (name != "_") {
                        var usedNs = occupiedNames[name]
                        if (usedNs == null) usedNs = 1
                        occupiedNames[name] = usedNs + 1
                        return if (usedNs > 1) "$name$usedNs" else name
                    }
                }

                is EmptyPattern -> return "()"
            }
            return null
        }

    }

}