package org.arend.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.arend.core.context.param.DependentLink
import org.arend.core.pattern.BindingPattern
import org.arend.core.pattern.ConstructorPattern
import org.arend.core.pattern.EmptyPattern
import org.arend.typechecking.error.local.MissingClausesError
import org.arend.core.pattern.Pattern
import org.arend.psi.*
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.PsiLocatedReferable

class ImplementMissingClausesQuickFix(private val missingClausesError : MissingClausesError, private val cause: ArendCompositeElement) : IntentionAction {
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
                var parameter: DependentLink? = if (!missingClausesError.isCase && !missingClausesError.isElim) missingClausesError.parameters else null
                while (iterator.hasNext()) {
                    val pattern = iterator.next()
                    val str = transformPattern(pattern, occupiedNames, if (parameter == null || parameter.isExplicit) Companion.Braces.NO else Companion.Braces.BRACES)
                    if (str != null) append(str) else {
                        error = true
                        break
                    }
                    if (iterator.hasNext()) append(", ")
                    parameter = if (parameter != null && parameter.hasNext()) parameter.next else null
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
        enum class Braces { NO, PAREN, BRACES }

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

        fun transformPattern(pattern: Pattern, occupiedNames: HashMap<String, Int>, paren: Braces): String? {
            when (pattern) {
                is ConstructorPattern -> {
                    val definition = pattern.definition
                    val argumentPatterns = ArrayList<String?>()
                    val patternIterator = pattern.patterns.patternList.iterator()
                    var constructorArgument: DependentLink? = definition.parameters

                    while (patternIterator.hasNext()) {
                        val argumentPattern = patternIterator.next()
                        argumentPatterns.add(transformPattern(argumentPattern, occupiedNames, if (constructorArgument == null || constructorArgument.isExplicit) Companion.Braces.PAREN else Companion.Braces.BRACES))
                        constructorArgument = if (constructorArgument != null && constructorArgument.hasNext()) constructorArgument.next else null
                    }

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
                        val referable = PsiLocatedReferable.fromReferable(definition.referable)
                        val result = buildString {
                            append(if (referable != null) referable.name else definition.name)
                            for (argument in argumentPatterns) append(" $argument")
                        }
                        if (paren == Companion.Braces.PAREN) "($result)" else result
                    }

                    return if (paren == Companion.Braces.BRACES) "{$returnString}" else returnString
                }

                is BindingPattern -> {
                    val name = pattern.binding.name ?: "_"

                    val result = if (name != "_") {
                        var usedNs = occupiedNames[name]
                        if (usedNs == null) usedNs = 1
                        occupiedNames[name] = usedNs + 1
                        if (usedNs > 1) "$name$usedNs" else name
                    } else name

                    return if (paren == Companion.Braces.BRACES) "{$result}" else result
                }

                is EmptyPattern -> return "()"
            }
            return null
        }

    }

}