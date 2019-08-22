package org.arend.intention

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.arend.core.context.binding.Binding
import org.arend.core.context.param.DependentLink
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

class SplitAtomPatternIntention : SelfTargetingIntention<ArendDefIdentifier>(ArendDefIdentifier::class.java, "Split atomic pattern") {
    override fun isApplicableTo(element: ArendDefIdentifier, caretOffset: Int, editor: Editor?): Boolean {
        val parent = element.parent
        val project = editor?.project
        if (project != null && (parent is ArendPattern || parent is ArendAtomPatternOrPrefix)) {
            var patternOwner: PsiElement? = parent
            while (patternOwner is ArendPattern || patternOwner is ArendAtomPattern || patternOwner is ArendAtomPatternOrPrefix) patternOwner = patternOwner.parent
            var definition: ArendDefinition? = null
            val ownerParent = patternOwner?.parent
            var abstractPatterns: List<Abstract.Pattern>? = null
            if (patternOwner is ArendClause && ownerParent is ArendFunctionClauses) {
                val body = ownerParent.parent
                val func = body?.parent
                if (body is ArendFunctionBody && func is ArendDefFunction) {
                    abstractPatterns = patternOwner.patterns
                    definition = func
                }
            }
            if (patternOwner is ArendConstructorClause && ownerParent is ArendDataBody) {
                val data = ownerParent.parent
                abstractPatterns = patternOwner.patterns
                if (data is ArendDefData) definition = data
            }
            if (definition != null) {
                val typeCheckedDefinition = TypeCheckingService.getInstance(project).getTypechecked(definition)
                if (typeCheckedDefinition != null && abstractPatterns != null) {
                    val patterns = computePatterns(abstractPatterns, typeCheckedDefinition.parameters, definition as? Abstract.EliminatedExpressionsHolder, definition.scope, project)
                    return patterns != null
                }

            }
        }
        return false
    }

    private fun computePatterns(abstractPatterns: List<Abstract.Pattern>, parameters: DependentLink, elimExprHolder: Abstract.EliminatedExpressionsHolder?, scope: Scope, project: Project): List<Pattern>? {
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

            ElimTypechecking.getEliminatedParameters(elimVars, emptyList(), parameters, listErrorReporter, context) ?: return null
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
        if (listErrorReporter.errorList.isNotEmpty()) return null
        return patterns
    }

    override fun applyTo(element: ArendDefIdentifier, project: Project?, editor: Editor?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}