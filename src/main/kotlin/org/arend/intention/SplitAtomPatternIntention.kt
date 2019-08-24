package org.arend.intention

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.arend.core.context.binding.Binding
import org.arend.core.context.param.DependentLink
import org.arend.core.definition.DataDefinition
import org.arend.core.expr.DataCallExpression
import org.arend.core.pattern.BindingPattern
import org.arend.core.pattern.ConstructorPattern
import org.arend.core.pattern.Pattern
import org.arend.error.ListErrorReporter
import org.arend.naming.reference.Referable
import org.arend.naming.reference.converter.IdReferableConverter
import org.arend.naming.resolving.visitor.ExpressionResolveNameVisitor
import org.arend.naming.scope.ConvertingScope
import org.arend.naming.scope.ListScope
import org.arend.naming.scope.Scope
import org.arend.psi.*
import org.arend.term.abs.Abstract
import org.arend.term.abs.ConcreteBuilder
import org.arend.typechecking.TypeCheckingService
import org.arend.typechecking.patternmatching.ElimTypechecking
import org.arend.typechecking.patternmatching.PatternTypechecking
import org.arend.typechecking.patternmatching.PatternTypechecking.Flag
import org.arend.typechecking.typecheckable.provider.EmptyConcreteProvider
import org.arend.typechecking.visitor.DesugarVisitor
import java.util.*

class SplitAtomPatternIntention : SelfTargetingIntention<PsiElement>(PsiElement::class.java, "Split atomic pattern") {
    private var dataDefinition: DataDefinition? = null
    private var clause: ArendClause? = null

    override fun isApplicableTo(element: PsiElement, caretOffset: Int, editor: Editor?): Boolean {
        if (element is ArendPattern && element.atomPatternOrPrefixList.size == 0 || element is ArendAtomPatternOrPrefix)  {
            val pattern = checkApplicability(element, editor?.project)
            if (pattern != null) {
                val type = pattern.toExpression().type //do we want to normalize this to whnf?
                if (type is DataCallExpression) {
                    dataDefinition = type.definition
                    return dataDefinition != null && clause != null
                }
            }
        }
        return false
    }

    override fun applyTo(element: PsiElement, project: Project?, editor: Editor?) {
        val localClause = clause
        val data = dataDefinition

        if (localClause != null && data != null && project != null) {
            val factory = ArendPsiFactory(project)
            if (data.constructors.isEmpty()) {
                val emptyPattern: PsiElement? = when (element) {
                    is ArendPattern -> factory.createClause("()").childOfType<ArendPattern>()
                    is ArendAtomPatternOrPrefix -> factory.createAtomPattern("()")
                    else -> null
                }
                if (emptyPattern != null) element.replaceWithNotification(emptyPattern)
            } else {
                var first = true
                val patterns = localClause.patterns

                for (constructor in data.constructors) {

                }
            }
        }
    }

    private fun checkApplicability(element : PsiElement, project: Project?): Pattern? {
        if (project != null) {
            var patternOwner: PsiElement? = element
            var pattern: PsiElement? = null
            val indexList = ArrayList<Int>()

            while (patternOwner is ArendPattern || patternOwner is ArendAtomPattern || patternOwner is ArendAtomPatternOrPrefix) {
                pattern = patternOwner
                patternOwner = patternOwner.parent
                if (patternOwner is ArendPattern) {
                    val i = patternOwner.atomPatternOrPrefixList.indexOf(pattern)
                    if (i != -1) indexList.add(0, i)
                }
                if (patternOwner is ArendClause) indexList.add(0, patternOwner.patternList.indexOf(pattern))
                if (patternOwner is ArendConstructorClause) indexList.add(0, patternOwner.patternList.indexOf(pattern))
            }

            var definition: ArendDefinition? = null
            val ownerParent = patternOwner?.parent
            var abstractPatterns: List<Abstract.Pattern>? = null

            if (pattern != null && patternOwner is ArendClause && ownerParent is ArendFunctionClauses) {
                val body = ownerParent.parent
                val func = body?.parent
                clause = patternOwner
                if (body is ArendFunctionBody && func is ArendDefFunction) {
                    abstractPatterns = patternOwner.patterns
                    definition = func
                }
            }
            if (pattern != null && patternOwner is ArendConstructorClause && ownerParent is ArendDataBody) {
                clause = null
                val data = ownerParent.parent
                abstractPatterns = patternOwner.patterns
                if (data is ArendDefData) definition = data
            }

            if (definition != null) {
                val typeCheckedDefinition = TypeCheckingService.getInstance(project).getTypechecked(definition)
                if (typeCheckedDefinition != null && abstractPatterns != null) {
                    val (patterns, isElim) = computePatterns(abstractPatterns, typeCheckedDefinition.parameters, definition as? Abstract.EliminatedExpressionsHolder, definition.scope, project)
                    if (patterns != null) {
                        val typecheckedPattern = if (isElim) patterns[indexList[0]] else findMatchingPattern(abstractPatterns, typeCheckedDefinition.parameters, patterns, indexList[0])
                        if (typecheckedPattern != null) {
                            val pattern2 = findPattern(indexList.drop(1), typecheckedPattern, abstractPatterns[indexList[0]])
                            return pattern2 as? BindingPattern
                        }
                    }
                }
            }
        }
        return null
    }

    private fun computePatterns(abstractPatterns: List<Abstract.Pattern>, parameters: DependentLink, elimExprHolder: Abstract.EliminatedExpressionsHolder?, scope: Scope, project: Project): Pair<List<Pattern>?, Boolean> {
        val listErrorReporter = ListErrorReporter()
        val concreteParameters = elimExprHolder?.parameters?.let { params ->
            ConcreteBuilder.convert(IdReferableConverter.INSTANCE, true) { it.buildTelescopeParameters(params) }
        }
        val elimVars = elimExprHolder?.eliminatedExpressions?.let { vars ->
            ConcreteBuilder.convert(IdReferableConverter.INSTANCE, true) { it.buildReferences(vars) }
        }
        val elimParams = if (concreteParameters != null && elimVars != null) {
            val context = LinkedHashMap<Referable, Binding>()
            var link = parameters
            loop@ for (parameter in concreteParameters) {
                for (referable in parameter.referableList) {
                    if (!link.hasNext()) break@loop

                    context[referable] = link
                    link = link.next
                }
            }
            val listScope = ListScope(context.keys.toList())
            for (elimVar in elimVars) ExpressionResolveNameVisitor.resolve(elimVar, listScope, null)

            ElimTypechecking.getEliminatedParameters(elimVars, emptyList(), parameters, listErrorReporter, context) ?: return Pair(null, true)
        } else {
            emptyList()
        }

        val concretePatterns = ConcreteBuilder.convert(IdReferableConverter.INSTANCE, true) { it.buildPatterns(abstractPatterns) }
        DesugarVisitor.desugarPatterns(concretePatterns, listErrorReporter)
        ExpressionResolveNameVisitor(EmptyConcreteProvider.INSTANCE, ConvertingScope(TypeCheckingService.getInstance(project).newReferableConverter(true), scope), ArrayList(), listErrorReporter, null).visitPatterns(concretePatterns)
        val patternTypechecking = PatternTypechecking(listErrorReporter, EnumSet.of(Flag.ALLOW_INTERVAL, Flag.ALLOW_CONDITIONS), null)
        val patterns = patternTypechecking.typecheckPatterns(concretePatterns, parameters, elimParams)
        val builder = StringBuilder()
        for (error in listErrorReporter.errorList) builder.append(error)
        return Pair(if (listErrorReporter.errorList.isEmpty()) patterns else null, elimParams.isNotEmpty())
    }

    private fun findPattern(indexList: List<Int>, typecheckedPattern: Pattern, abstractPattern: Abstract.Pattern): Pattern? {
        if (indexList.isEmpty()) return typecheckedPattern
        if (typecheckedPattern is ConstructorPattern) {
            val typecheckedPatternChild = findMatchingPattern(abstractPattern.arguments, typecheckedPattern.parameters, typecheckedPattern.patterns.patternList, indexList[0])
            val abstractPatternChild = abstractPattern.arguments.getOrNull(indexList[0])
            if (typecheckedPatternChild != null && abstractPatternChild != null)
                return findPattern(indexList.drop(1), typecheckedPatternChild, abstractPatternChild)
        }
        return null
    }

    private fun findMatchingPattern(abstractPatterns: List<Abstract.Pattern>, parameters: DependentLink, typecheckedPatterns: List<Pattern>, index: Int): Pattern? {
        var link = parameters
        var i = 0
        var j = 0

        while (link.hasNext() && i < abstractPatterns.size) {
            if (index == i) return typecheckedPatterns[j]

            val isEqual = link.isExplicit == abstractPatterns[i].isExplicit
            if (isEqual || link.isExplicit) i++
            if (isEqual || !link.isExplicit) {
                link = link.next
                j++
            }
        }

        return null
    }
}