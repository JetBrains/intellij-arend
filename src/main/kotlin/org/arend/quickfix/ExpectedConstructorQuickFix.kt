package org.arend.quickfix

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.arend.core.context.LinkList
import org.arend.core.context.binding.Binding
import org.arend.core.context.param.DependentLink
import org.arend.core.definition.ClassDefinition
import org.arend.core.definition.Constructor
import org.arend.core.definition.DataDefinition
import org.arend.core.expr.ClassCallExpression
import org.arend.core.expr.DataCallExpression
import org.arend.core.expr.ExpressionFactory
import org.arend.core.expr.ReferenceExpression
import org.arend.core.expr.type.Type
import org.arend.core.pattern.*
import org.arend.core.subst.ExprSubstitution
import org.arend.error.CountingErrorReporter
import org.arend.error.DummyErrorReporter
import org.arend.ext.error.GeneralError
import org.arend.ext.error.ListErrorReporter
import org.arend.ext.variable.Variable
import org.arend.ext.variable.VariableImpl
import org.arend.intention.SplitAtomPatternIntention
import org.arend.intention.SplitAtomPatternIntention.Companion.doReplacePattern
import org.arend.intention.SplitAtomPatternIntention.Companion.isSucPattern
import org.arend.naming.reference.Referable
import org.arend.naming.renamer.Renamer
import org.arend.naming.renamer.StringRenamer
import org.arend.naming.resolving.visitor.DefinitionResolveNameVisitor
import org.arend.naming.resolving.visitor.ExpressionResolveNameVisitor
import org.arend.psi.*
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.ArendFunctionalDefinition
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.quickfix.referenceResolve.ResolveReferenceAction
import org.arend.resolving.ArendReferableConverter
import org.arend.resolving.DataLocatedReferable
import org.arend.resolving.PsiConcreteProvider
import org.arend.term.abs.Abstract
import org.arend.term.abs.ConcreteBuilder.*
import org.arend.term.concrete.BaseConcreteExpressionVisitor
import org.arend.term.concrete.Concrete
import org.arend.typechecking.error.local.ExpectedConstructorError
import org.arend.typechecking.patternmatching.ElimTypechecking
import org.arend.typechecking.patternmatching.ExpressionMatcher
import org.arend.typechecking.patternmatching.PatternTypechecking
import org.arend.typechecking.visitor.CheckTypeVisitor
import java.util.Collections.singletonList
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.collections.LinkedHashSet
import kotlin.math.abs

class ExpectedConstructorQuickFix(val error: ExpectedConstructorError, val cause: SmartPsiElementPointer<ArendCompositeElement>) : IntentionAction {
    override fun startInWriteAction(): Boolean = true

    override fun getFamilyName(): String = "arend.patternmatching"

    override fun getText(): String = "Do patternmatching on the 'stuck' variable"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = true

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val errorHintBuffer = StringBuffer()
        val definition = error.definition as DataLocatedReferable
        val definitionPsi = definition.data?.element
        val constructorPsi = this.cause.element?.ancestor<ArendConstructor>()
        val caseExprPsi = this.cause.element?.ancestor<ArendCaseExpr>()
        val bodyPsi = (definitionPsi as? ArendFunctionalDefinition)?.body
        val dataBodyPsi = (definitionPsi as? ArendDefData)?.dataBody

        val clausesListPsi : List<Abstract.Clause>? = when {
            bodyPsi is ArendFunctionBody -> bodyPsi.functionClauses?.clauseList as? List<Abstract.Clause>
            bodyPsi is ArendInstanceBody -> bodyPsi.functionClauses?.clauseList as? List<Abstract.Clause>
            constructorPsi != null -> constructorPsi.clauseList
            dataBodyPsi != null -> dataBodyPsi.constructorClauseList
            else -> null
        }

        val elimPsi = when {
            bodyPsi != null -> bodyPsi.elim
            constructorPsi != null -> constructorPsi.elim
            dataBodyPsi != null -> dataBodyPsi.elim
            else -> null
        }

        var typecheckedParameters: DependentLink? = null

        if (definitionPsi is Abstract.Definition) {
            //STEP 0: Typecheck patterns of the function definition for the 2nd time
            val elimParams: List<DependentLink>
            val expectedConstructorErrorEntries = ArrayList<ExpectedConstructorErrorEntry>()

            run {
                val cer = CountingErrorReporter(GeneralError.Level.ERROR, DummyErrorReporter.INSTANCE)
                val psiConcreteProvider = PsiConcreteProvider(project, cer, null)
                val resolveNameVisitor = DefinitionResolveNameVisitor(psiConcreteProvider, ArendReferableConverter, cer)

                var concreteDefinition : Concrete.GeneralDefinition = convert(ArendReferableConverter, definitionPsi, cer)
                var concreteCaseExpr: Concrete.CaseExpression? = null

                when (concreteDefinition) {
                    is Concrete.BaseFunctionDefinition -> resolveNameVisitor.visitFunction(concreteDefinition, definitionPsi.scope)
                    is Concrete.DataDefinition -> resolveNameVisitor.visitData(concreteDefinition, definitionPsi.scope)
                }

                if (constructorPsi != null) {
                    var constructorFound = false
                    ccLoop@for (cC in (concreteDefinition as Concrete.DataDefinition).constructorClauses) for (c in cC.constructors) {
                        if ((c.data as? DataLocatedReferable)?.data?.element == constructorPsi) {
                            concreteDefinition = c
                            constructorFound = true
                            break@ccLoop
                        }
                    }
                    if (!constructorFound) throw java.lang.IllegalStateException()
                }

                if (error.caseExpressions != null && caseExprPsi != null && concreteDefinition is Concrete.BaseFunctionDefinition) {
                    val children = ArrayList<Concrete.Expression>()
                    when (val b = concreteDefinition.body) {
                        is Concrete.ElimFunctionBody -> for (c in b.clauses) children.add(c.expression)
                        is Concrete.TermFunctionBody -> children.add(b.term)
                        is Concrete.CoelimFunctionBody -> for (cce in b.coClauseElements) if (cce is Concrete.ClassFieldImpl) children.add(cce.implementation)
                    }

                    val searcher = object : BaseConcreteExpressionVisitor<Void>() {
                        override fun visitCase(expr: Concrete.CaseExpression?, params: Void?): Concrete.Expression {
                            if (expr?.data == caseExprPsi) concreteCaseExpr = expr
                            return super.visitCase(expr, params)
                        }
                    }
                    for (child in children) child.accept(searcher, null)
                    //TODO: Throw some error in case this code failed to find "concreteCaseExpr"
                }

                //if (cer.errorsNumber > 0) return@invoke
                val errorReporter = ListErrorReporter()

                val eliminatedReferences = when (concreteDefinition) {
                    is Concrete.BaseFunctionDefinition -> (concreteDefinition.body as? Concrete.ElimFunctionBody)?.eliminatedReferences
                    is Concrete.DataDefinition -> concreteDefinition.eliminatedReferences
                    is Concrete.Constructor -> concreteDefinition.eliminatedReferences
                    else -> null
                }

                val parameters = if (concreteCaseExpr != null) null else when (concreteDefinition) {
                    is Concrete.BaseFunctionDefinition -> concreteDefinition.parameters
                    is Concrete.DataDefinition -> concreteDefinition.parameters
                    is Concrete.Constructor -> concreteDefinition.parameters
                    else -> null
                }

                val concreteCaseExprVal = concreteCaseExpr
                typecheckedParameters = when {
                    concreteCaseExprVal != null -> {
                        this.error.clauseParameters
                    }
                    constructorPsi != null -> {
                        (definition.typechecked as DataDefinition).constructors.firstOrNull { (it.referable as DataLocatedReferable).data?.element == constructorPsi }?.parameters ?: throw java.lang.IllegalStateException()
                    }
                    else -> definition.typechecked.parameters
                }

                val clauses = concreteCaseExpr?.clauses
                    ?: when (concreteDefinition) {
                        is Concrete.BaseFunctionDefinition -> (concreteDefinition.body as? Concrete.ElimFunctionBody)?.clauses
                        is Concrete.DataDefinition -> concreteDefinition.constructorClauses
                        is Concrete.Constructor -> concreteDefinition.clauses
                        else -> null
                    }

                val mode = if (concreteCaseExpr != null) PatternTypechecking.Mode.CASE else when (concreteDefinition) {
                    is Concrete.BaseFunctionDefinition -> PatternTypechecking.Mode.FUNCTION
                    is Concrete.DataDefinition -> PatternTypechecking.Mode.DATA
                    is Concrete.Constructor -> PatternTypechecking.Mode.CONSTRUCTOR
                    else -> null
                }

                val context = HashMap<Referable, Binding>()
                if (parameters != null) for (pair in parameters.map { it.referableList }.flatten().zip(DependentLink.Helper.toList(typecheckedParameters))) context[pair.first] = pair.second
                elimParams = if (eliminatedReferences != null) ElimTypechecking.getEliminatedParameters(eliminatedReferences, clauses, typecheckedParameters, errorReporter, context) else emptyList()

                val typechecker = CheckTypeVisitor(errorReporter, null, null)

                if (clauses != null && mode != null) for (clause in clauses) {
                    val substitution = ExprSubstitution()
                    errorReporter.errorList.clear()

                    PatternTypechecking(errorReporter, mode, typechecker, false, error.caseExpressions, elimParams)
                        .typecheckPatterns(clause.patterns, parameters, typecheckedParameters, substitution, ExprSubstitution(), clause)

                    val relevantErrors = errorReporter.errorList.filterIsInstance<ExpectedConstructorError>()
                    if (relevantErrors.size == 1) {
                        val relevantError = relevantErrors[0]
                        val constructorTypechecked = (relevantError.referable as? DataLocatedReferable)?.typechecked
                        if (constructorTypechecked is Constructor) expectedConstructorErrorEntries.add(ExpectedConstructorErrorEntry(relevantError, clause, substitution, constructorTypechecked))
                    }
                }
            }

            val definitionParameters = DependentLink.Helper.toList(typecheckedParameters)
            val entriesToRemove = ArrayList<ExpectedConstructorErrorEntry>()

            ecLoop@for (ecEntry in expectedConstructorErrorEntries) {
                val currentClause = ecEntry.clause.data as Abstract.Clause
                val concreteCurrentClause = ecEntry.clause
                val error = ecEntry.error
                val parameterType = error.parameter?.type

                fun reportError() {
                    entriesToRemove.add(ecEntry)
                    errorHintBuffer.append("ExpectedConstructorError quickfix was unable to compute matching patterns for the parameter ${error.parameter}\n")
                    errorHintBuffer.append("Constructor: ${ecEntry.constructorTypechecked.name}\n")
                    errorHintBuffer.append("Patterns of the constructor: ${ecEntry.constructorTypechecked.patterns.map{ it.toExpression() }.toList()}\n")
                    errorHintBuffer.append("Containing clause: ${(currentClause as PsiElement).text}\n")
                }

                if (error.caseExpressions == null) {
                    // STEP 1: Compute matching patterns
                    val rawSubsts = HashMap<Binding, ExpressionPattern>()
                    if (parameterType !is DataCallExpression || ExpressionMatcher.computeMatchingPatterns(parameterType, ecEntry.constructorTypechecked, ExprSubstitution(), rawSubsts) == null) {
                        reportError()
                        continue@ecLoop
                    }

                    // STEP 2: Calculate lists of variables which need to be eliminated or specialized
                    val clauseParameters = DependentLink.Helper.toList(error.patternParameters)
                    val definitionToClauseMap = HashMap<Variable, Variable>()
                    val clauseToDefinitionMap = HashMap<Variable, Variable>()

                    for (variable in ecEntry.substitution.keys) {
                        val binding = (ecEntry.substitution.get(variable) as? ReferenceExpression)?.binding
                        if (binding != null && variable is Binding && definitionParameters.contains(variable) && clauseParameters.contains(binding)) {
                            definitionToClauseMap[variable] = binding
                            clauseToDefinitionMap[binding] = variable
                        }
                    }

                    //This piece of code filters out trivial substitutions and also ensures that the key of each substitution is either an element of definitionParametersToEliminate or clauseParametersToSpecialize
                    for (subst in rawSubsts) if (subst.value !is BindingPattern) {
                        if (definitionParameters.contains(subst.key)) {
                            ecEntry.clauseDefinitionParametersToEliminate.add(subst.key)
                            ecEntry.correctedSubsts[subst.key] = subst.value
                        } else {
                            val localClauseBinding =
                                if (clauseParameters.contains(subst.key)) subst.key else (ecEntry.substitution[subst.key] as? ReferenceExpression)?.binding
                            if (localClauseBinding != null) {
                                val definitionBinding = clauseToDefinitionMap[localClauseBinding]
                                if (definitionBinding != null && definitionParameters.contains(definitionBinding)) {
                                    ecEntry.correctedSubsts[definitionBinding] = subst.value
                                    ecEntry.clauseDefinitionParametersToEliminate.add(definitionBinding)
                                } else if (clauseParameters.contains(localClauseBinding)) {
                                    ecEntry.correctedSubsts[localClauseBinding] = subst.value
                                    ecEntry.clauseParametersToSpecialize.add(localClauseBinding)
                                }
                            }
                        }
                    }

                    //STEP 3: Match clauseParameters with currentClause PSI
                    if (!matchConcreteWithWellTyped(currentClause as PsiElement, concreteCurrentClause.patterns, prepareExplicitnessMask(definitionParameters, elimParams), clauseParameters.iterator(), ecEntry.matchData) ||
                        ecEntry.clauseParametersToSpecialize.any { ecEntry.matchData[it] == null })
                        throw IllegalStateException("ExpectedConstructorError quickfix failed to calculate the correspondence between psi and concrete name patterns")
                } else {
                    // STEP 1C: Compute matching expressions
                    val matchResults = ArrayList<ExpressionMatcher.MatchResult>()
                    if (parameterType !is DataCallExpression || ExpressionMatcher.computeMatchingExpressions(parameterType, ecEntry.constructorTypechecked, true, matchResults) == null) {
                        reportError()
                        continue@ecLoop
                    }

                    println(ecEntry)
                    for (mr in matchResults) if (mr.pattern !is BindingPattern) {
                        println("Binding: ${mr.binding} Pattern: ${(mr.pattern as? ExpressionPattern)?.toExpression()} Expression: ${mr.expression}")

                    }
                }
            }
            expectedConstructorErrorEntries.removeAll(entriesToRemove)
            val clauseToEntryMap = HashMap<Abstract.Clause, ExpectedConstructorErrorEntry>()
            val definitionParametersToEliminate = HashSet<Variable>() // Subset of definitionParameters (global)

            for (entry in expectedConstructorErrorEntries) {
                clauseToEntryMap[entry.clause.data as Abstract.Clause] = entry
                definitionParametersToEliminate.addAll(entry.clauseDefinitionParametersToEliminate)
            }

            // At this point we have collected enough information to actually attempt modifying PSI
            val psiFactory = ArendPsiFactory(project)

            // STEP 4: Eliminate global variables and insert primers corresponding to definitionVariablesToEliminate
            // We may need to modify all clauses not just the one upon which this refactoring was invoked.
            if (elimParams.isNotEmpty() && elimPsi != null) { //elim
                val paramsMap = HashMap<DependentLink, ArendRefIdentifier>()
                for (e in elimParams.zip(elimPsi.refIdentifierList)) paramsMap[e.first] = e.second
                for (ecEntry in expectedConstructorErrorEntries) for (e in elimParams.zip((ecEntry.clause.data as Abstract.Clause).patterns)) (e.second as? ArendPattern)?.let { ecEntry.patternPrimers[e.first] = it}


                definitionParametersToEliminate.removeAll(elimParams)

                var anchor: PsiElement? = null
                for (param in definitionParameters) if (definitionParametersToEliminate.contains(param)) {
                    val template = psiFactory.createRefIdentifier("${param.name}, dummy")
                    val comma = template.nextSibling
                    var commaInserted = false
                    if (anchor != null) {
                        anchor = elimPsi.addAfter(comma, anchor)
                        commaInserted = true
                    }
                    anchor = elimPsi.addAfterWithNotification(template, anchor ?: elimPsi.elimKw)
                    elimPsi.addBefore(psiFactory.createWhitespace(" "), anchor)
                    if (!commaInserted) {
                        anchor = elimPsi.addAfter(comma, anchor)
                    }
                } else {
                    anchor = paramsMap[param]
                }

                if (clausesListPsi != null) for (transformedClause in clausesListPsi)
                    doInsertPrimers(psiFactory, transformedClause, definitionParameters, elimParams, definitionParametersToEliminate, clauseToEntryMap[transformedClause]?.patternPrimers) { p -> p.name }
            } else if (clausesListPsi != null)
                for (ecEntry in expectedConstructorErrorEntries) {
                    val currentClause = ecEntry.clause.data as ArendClause
                    val parameterIterator = definitionParameters.iterator()
                    val patternIterator = currentClause.patternList.iterator()
                    val patternMatchedParameters = LinkedHashSet<Variable>()
                    while (parameterIterator.hasNext() && patternIterator.hasNext()) {
                        val pattern = patternIterator.next()
                        var parameter: DependentLink? = parameterIterator.next()
                        if (pattern.isExplicit) while (parameter != null && !parameter.isExplicit)
                            parameter = if (parameterIterator.hasNext()) parameterIterator.next() else null
                        if (parameter != null && pattern.isExplicit == parameter.isExplicit) {
                            patternMatchedParameters.add(parameter)
                            ecEntry.patternPrimers[parameter] = pattern
                        }
                    }

                    val newVars = definitionParametersToEliminate.minus(patternMatchedParameters)
                    val newVarsPlusPrecedingImplicitVars = HashSet<Variable>(newVars)
                    var implicitFlag = false
                    for (param in definitionParameters.reversed()) {
                        if (implicitFlag && !param.isExplicit) newVarsPlusPrecedingImplicitVars.add(param)
                        if (newVars.contains(param) && !param.isExplicit) implicitFlag = true
                        if (param.isExplicit) implicitFlag = false
                    }

                    doInsertPrimers(psiFactory, currentClause, definitionParameters, ArrayList(patternMatchedParameters), newVarsPlusPrecedingImplicitVars, ecEntry.patternPrimers) { p ->
                        val builder = StringBuilder()
                        if (!p.isExplicit) builder.append("{")
                        builder.append(if (newVars.contains(p)) p.name else "_")
                        if (!p.isExplicit) builder.append("}")
                        builder.toString()
                    }
                }

            // STEP 5: Analyze matchData and insert primers corresponding to localClauseVariablesToSpecialize
            for (ecEntry in expectedConstructorErrorEntries) {
                for (p in ecEntry.clauseParametersToSpecialize) {
                    when (val descriptor = ecEntry.matchData[p]) {
                        is ExplicitNamePattern -> ecEntry.patternPrimers[p] = descriptor.bindingPsi as ArendPattern
                        is ImplicitConstructorPattern -> {
                            val positionKey = Pair(descriptor.enclosingNode, descriptor.followingParameter)
                            var positionList = ecEntry.insertData[positionKey]
                            if (positionList == null) {
                                positionList = ArrayList()
                                ecEntry.insertData[positionKey] = positionList
                            }
                            positionList.add(Pair(descriptor.implicitArgCount, p))
                        }
                    }
                }

                insertPrimers(psiFactory, ecEntry.insertData) { a, b -> ecEntry.patternPrimers[a] = b }
            }

            for (ecEntry in expectedConstructorErrorEntries) {
                val currentClause = ecEntry.clause.data as Abstract.Clause
                val processedSubstitutions = HashMap<PsiElement, ExpressionPattern>()
                val newPrimers = HashMap<ImplicitConstructorPattern, ExpressionPattern>()
                val mismatchedPatterns = HashMap<PsiElement, Pair<ExpressionPattern, List<PsiElement>>>()

                // STEP 6: Unify matched patterns with existing patterns and calculate elementary substitutions (of the form "variable" -> "expression")
                for (cS in ecEntry.correctedSubsts) {
                    val patternPrimer = ecEntry.patternPrimers[cS.key]
                    if (patternPrimer != null) {
                        var concretePrimer = convertPattern(patternPrimer, ArendReferableConverter, null, null)
                        val primerOk: Boolean
                        run {
                            val cer = CountingErrorReporter(DummyErrorReporter.INSTANCE)
                            val resolver = ExpressionResolveNameVisitor(ArendReferableConverter, patternPrimer.scope, ArrayList<Referable>(), cer, null)
                            val primerList = ArrayList<Concrete.Pattern>()
                            primerList.add(concretePrimer)
                            resolver.visitPatterns(primerList, null, true)
                            concretePrimer = primerList[0]
                            resolver.resolvePattern(concretePrimer)
                            primerOk = cer.errorsNumber == 0
                        }

                        if (primerOk) unify(cS.value, concretePrimer, processedSubstitutions, newPrimers, mismatchedPatterns)
                    }
                }

                // STEP 7: Insert new primers
                val newPrimerInsertData = HashMap<Pair<PsiElement, PsiElement?>, MutableList<Pair<Int, Variable>>>()
                val tempVarMatchInfo = HashMap<Variable, ExpressionPattern>()
                for (p in newPrimers) {
                    val descriptor = p.key
                    val positionKey = Pair(descriptor.enclosingNode, descriptor.followingParameter)
                    var positionList = newPrimerInsertData[positionKey]
                    if (positionList == null) {
                        positionList = ArrayList()
                        newPrimerInsertData[positionKey] = positionList
                    }
                    val temp = VariableImpl("_")
                    positionList.add(Pair(descriptor.implicitArgCount, temp))
                    tempVarMatchInfo[temp] = p.value
                }

                insertPrimers(psiFactory, newPrimerInsertData) {tempVar, createdPrimer -> tempVarMatchInfo[tempVar]?.let{ processedSubstitutions[createdPrimer] = it}}

                // STEP 8: Perform actual substitutions (at this point all keys of substEntry should be simple NamePatterns...)
                val clauseExpressionList : List<PsiElement?> = when (currentClause) {
                    is ArendClause -> singletonList(currentClause.expr)
                    is ArendConstructorClause -> currentClause.constructorList
                    else -> emptyList()
                }
                val variablePatterns = SplitAtomPatternIntention.findAllVariablePatterns(currentClause, null)
                val newVariables = ArrayList<String>()

                for (clauseExpression in clauseExpressionList) if (clauseExpression != null)
                    for (mismatchedEntry in mismatchedPatterns) {
                        for (varToRemove in mismatchedEntry.value.second) {
                            val defIdentifier = varToRemove.childOfType<ArendDefIdentifier>()
                            if (defIdentifier != null) SplitAtomPatternIntention.doSubstituteUsages(project, defIdentifier, clauseExpression, "{?${defIdentifier.name}}")
                        }
                    }

                for (mismatchedEntry in mismatchedPatterns) {
                    val printData = printPattern(mismatchedEntry.value.first, currentClause as ArendCompositeElement, variablePatterns.map{ VariableImpl(it.name)}.toList(), newVariables, StringRenamer())
                    doReplacePattern(psiFactory, mismatchedEntry.key, printData.patternString, printData.requiresParentheses)
                }

                val varsNoLongerUsed = HashSet<ArendDefIdentifier>()

                for (substEntry in processedSubstitutions) {
                    val renamer = StringRenamer()
                    fun computePrintData() =
                        printPattern(substEntry.value, currentClause as ArendCompositeElement, variablePatterns.minus(varsNoLongerUsed).map{ VariableImpl(it.name)}.toList(), newVariables, renamer)

                    var printData = computePrintData()
                    val namePatternToReplace = substEntry.key

                    val idOrUnderscore = namePatternToReplace.childOfType<ArendDefIdentifier>() ?: namePatternToReplace.childOfType<ArendAtomPattern>()?.underscore
                    val asPiece = namePatternToReplace.childOfType<ArendAsPattern>()
                    val asDefIdentifier = asPiece?.defIdentifier

                    if (idOrUnderscore is ArendDefIdentifier) (substEntry.value.toExpression().type as? DataCallExpression)?.definition?.let{ renamer.setParameterName(it, idOrUnderscore.name) }

                    val target = asDefIdentifier ?: if (idOrUnderscore is ArendDefIdentifier) idOrUnderscore else null
                    val nonCachedResolver : (ArendRefIdentifier) -> PsiElement? = { (it as ArendCompositeElement).scope.resolveName(it.name) as? PsiElement }

                    val noOfUsages = if (target != null) {
                        fun calculateNumberOfUsages(element: PsiElement): Int {
                            return if (element is ArendRefIdentifier && nonCachedResolver.invoke(element) == target /* do not use cache! */ ) 1
                            else {
                                var result = 0
                                for (child in element.children) result += calculateNumberOfUsages(child)
                                result
                            }
                        }
                        var noOfUsages = 0
                        for (clauseExpression in clauseExpressionList) noOfUsages += if (clauseExpression != null) calculateNumberOfUsages(clauseExpression) else 0
                        noOfUsages
                    } else 0

                    val useAsPattern = (printData.complexity > 2 || printData.containsClassConstructor) && noOfUsages > 0

                    if (!useAsPattern && asPiece == null && idOrUnderscore is ArendDefIdentifier) {
                        varsNoLongerUsed.add(idOrUnderscore)
                        newVariables.clear()
                        printData = computePrintData() //Recompute print data, this time allowing the variable being substituted to be reused in the subtituted pattern (as one of its NamePatterns)
                    }

                    val existingAsName = asPiece?.defIdentifier
                    for (clauseExpression in clauseExpressionList) if (clauseExpression != null) {
                        if (existingAsName == null && !useAsPattern && idOrUnderscore is ArendDefIdentifier)
                            SplitAtomPatternIntention.doSubstituteUsages(project, idOrUnderscore, clauseExpression, printData.patternString, nonCachedResolver)
                    }

                    if (idOrUnderscore != null) {
                        var asName: String = if (!useAsPattern) "" else {
                            val n = (if (existingAsName != null) existingAsName.name else (idOrUnderscore as? ArendDefIdentifier)?.name)
                            if (n != null) n else {
                                val freshName = Renamer().generateFreshName(VariableImpl("_x"), (variablePatterns.map{ VariableImpl(it.name) } + newVariables.map { VariableImpl(it) }).toList())
                                newVariables.add(freshName)
                                freshName
                            }
                        }
                        (((((substEntry.value as? ConstructorExpressionPattern)?.dataExpression as? ClassCallExpression)?.definition?.referable?.data) as? SmartPsiElementPointer<*>)?.element as? PsiLocatedReferable)?.let {
                            if (asName.isNotEmpty()) asName += " : ${ResolveReferenceAction.getTargetName(it, currentClause as ArendCompositeElement)}"
                        }

                        var result = doReplacePattern(psiFactory, namePatternToReplace, printData.patternString, printData.requiresParentheses, asName) //TODO: Special insertion for integers?
                        var number = (result as? ArendAtomPatternOrPrefix)?.number
                        if (number != null) {
                            var needsReplace = false
                            while (result != null && isSucPattern(result.parent)) {
                                number++
                                result = result.parent
                                needsReplace = true
                            }

                            if (needsReplace && result != null) {
                                doReplacePattern(psiFactory, result, number.toString(), false)
                            }
                        }
                    }
                }
            }
        } else { // case
            //TODO: Implement me
        }

        if (editor != null && errorHintBuffer.isNotEmpty()) ApplicationManager.getApplication().invokeLater {
            val text = errorHintBuffer.toString().trim()
            HintManager.getInstance().showErrorHint(editor, text)
        }
    }

    companion object {
        class ExpectedConstructorErrorEntry(val error: ExpectedConstructorError, val clause: Concrete.Clause, val substitution: ExprSubstitution, val constructorTypechecked: Constructor) {
            val matchData = HashMap<DependentLink, VariableLocationDescriptor>() // Initialized at STEP 1
            val correctedSubsts = HashMap<Variable, ExpressionPattern>() // Initialized at STEP 2
            val clauseParametersToSpecialize = HashSet<Variable>() // Subset of ClauseParameters; Initialized at STEP 2
            val clauseDefinitionParametersToEliminate = HashSet<Variable>() // Subset of definitionParameters (per clause); Initialized at STEP 2
            val patternPrimers = HashMap<Variable, ArendPattern>() // Initialized at STEP 4
            val insertData = HashMap<Pair<PsiElement, PsiElement?>, MutableList<Pair<Int, Variable>>>() // Initialized at STEP 5

            override fun toString(): String {
                val result = StringBuffer()
                val currentClause = clause.data as ArendClause

                result.appendLine("Constructor: ${constructorTypechecked.name}; ")
                result.appendLine("Clause: ${currentClause.text}")

                if (substitution.keys.isNotEmpty()) {
                    result.appendLine("\nerror.substitution contents:")
                    printExprSubstitution(substitution, result)
                    result.appendLine()
                }

                if (clauseDefinitionParametersToEliminate.isNotEmpty()) {
                    result.append("Definition parameters which need to be eliminated: {")
                    for (v in clauseDefinitionParametersToEliminate) result.append("${v.toString1()}; ")
                    result.appendLine("}")
                }

                if (clauseParametersToSpecialize.isNotEmpty()) {
                    result.append("Local clause variables which need to be specialized: {")
                    for (v in clauseParametersToSpecialize) result.append("${v.toString1()}; ")
                    result.appendLine("}\n")
                }

                if (matchData.isNotEmpty()) result.appendLine("Result of matching local clause parameters with abstract PSI:")
                for (mdEntry in matchData) when (val mdValue = mdEntry.value) {
                    is ExplicitNamePattern ->
                        result.appendLine("${mdEntry.key.toString1()} -> existing parameter ${mdValue.bindingPsi.javaClass.simpleName}/${mdValue.bindingPsi.text}")
                    is ImplicitConstructorPattern ->
                        result.appendLine("${mdEntry.key.toString1()} -> implicit parameter of ${mdValue.enclosingNode.javaClass.simpleName}/\"${mdValue.enclosingNode.text}\" before \"${mdValue.followingParameter?.text}\" after ${mdValue.implicitArgCount} implicit args")
                }

                result.appendLine()

                return result.toString()
            }
            companion object {
                fun Variable.toString1(): String = "${this}@${this.hashCode().rem(100000)}"

                fun printExprSubstitution(substitution: ExprSubstitution, buffer: StringBuffer) {
                    for (variable in substitution.keys) {
                        val binding = (substitution.get(variable) as? ReferenceExpression)?.binding
                        buffer.appendLine("${variable.toString1()} -> ${binding?.toString1() ?: substitution[variable]}")
                    }
                }
            }
        }

        private fun collectBindings(pattern: Concrete.Pattern, collectedVariables: MutableList<PsiElement>) {
            val data = pattern.data as PsiElement
            when (pattern) {
                is Concrete.NamePattern -> collectedVariables.add(data)
                is Concrete.ConstructorPattern -> for (p in pattern.patterns) collectBindings(p, collectedVariables)
                is Concrete.TuplePattern -> for (p in pattern.patterns) collectBindings(p, collectedVariables)
                else -> {
                }
            }
        }

        data class PatternPrintResult(val patternString: String, val requiresParentheses: Boolean, val complexity: Int, val containsClassConstructor: Boolean)

        fun printPattern(pattern: Pattern,
                         location: ArendCompositeElement,
                         occupiedVariables: List<Variable>,
                         newVariables: MutableList<String>,
                         renamer: Renamer): PatternPrintResult {
            if (pattern.isAbsurd)
                return PatternPrintResult("()", false, 1, false)
            else {
                val binding = pattern.binding
                if (binding != null) {
                    val result = renamer.generateFreshName(binding, occupiedVariables + newVariables.map { VariableImpl(it) })
                    newVariables.add(result)
                    return PatternPrintResult(result, false, 1, false)
                } else {
                    val integralNumber = ImplementMissingClausesQuickFix.getIntegralNumber(pattern)
                    if (integralNumber != null && abs(integralNumber) < Concrete.NumberPattern.MAX_VALUE)
                        return PatternPrintResult(integralNumber.toString(), false, 1, false)
                    else {
                        val constructor = pattern.constructor
                        val tupleMode = constructor == null
                        var constructorArgument: DependentLink? = constructor?.parameters
                        var result = if (constructor != null) ResolveReferenceAction.getTargetName(PsiLocatedReferable.fromReferable(constructor.referable), location) + " " else "("
                        val patternIterator = pattern.subPatterns.iterator()
                        var complexity = 1
                        var containsClassConstructor = tupleMode && pattern.definition is ClassDefinition

                        while (patternIterator.hasNext()) {
                            val argumentPattern = patternIterator.next()
                            val argumentData = printPattern(argumentPattern, location, occupiedVariables, newVariables, renamer)
                            complexity += argumentData.complexity
                            if (argumentData.containsClassConstructor) containsClassConstructor = true

                            result += when {
                                constructorArgument != null && !constructorArgument.isExplicit -> "{${argumentData.patternString}}"
                                !tupleMode && argumentData.requiresParentheses -> "(${argumentData.patternString})"
                                else -> argumentData.patternString
                            }
                            if (patternIterator.hasNext()) result += if (tupleMode) ", " else " "

                            constructorArgument = if (constructorArgument != null && constructorArgument.hasNext()) constructorArgument.next else null
                        }

                        if (tupleMode) result += ")"
                        return PatternPrintResult(result, pattern.subPatterns.isNotEmpty() && !tupleMode, complexity, containsClassConstructor)
                    }
                }
            }
        }

        fun unify(pattern: ExpressionPattern, concretePattern: Concrete.Pattern,
                  processedSubstitutions: MutableMap<PsiElement, ExpressionPattern>,
                  newPrimers: MutableMap<ImplicitConstructorPattern, ExpressionPattern>,
                  mismatchedPatterns: MutableMap<PsiElement, Pair<ExpressionPattern, List<PsiElement>>>) {
            val data = concretePattern.data as PsiElement

            fun processMismatchedPattern() {
                val bindingsToRemove = ArrayList<PsiElement>()
                collectBindings(concretePattern, bindingsToRemove)
                mismatchedPatterns[data] = Pair(pattern, bindingsToRemove)
            }

            when (concretePattern) {
                is Concrete.NamePattern -> if (pattern !is BindingPattern) processedSubstitutions[data] = pattern
                is Concrete.ConstructorPattern -> { //TODO: Code too similar to "matchConcreteWithWellTyped". Could we somehow isolate common pieces of code for these functions?
                    val existingConstructor = concretePattern.constructor
                    val substitutedConstructor = (pattern.constructor)?.referable
                    if (pattern is ConstructorExpressionPattern && substitutedConstructor == existingConstructor) {
                        val bindingsIterator = DependentLink.Helper.toList(substitutedConstructor.typechecked.parameters).iterator()
                        val constructorExpressionParameters = pattern.subPatterns.iterator()
                        val concreteSubPatternsIterator = concretePattern.patterns.iterator()

                        var concreteSubPattern: Concrete.Pattern? = null
                        var skippedParameters = 0
                        fun nextConcretePattern() {
                            concreteSubPattern = if (concreteSubPatternsIterator.hasNext()) concreteSubPatternsIterator.next() else null
                            skippedParameters = 0
                        }
                        nextConcretePattern()

                        while (bindingsIterator.hasNext() && constructorExpressionParameters.hasNext()) {
                            val currentBinding = bindingsIterator.next()
                            val currentSubpattern = constructorExpressionParameters.next()
                            val concreteSubPatternVal = concreteSubPattern

                            if (concreteSubPatternVal != null && concreteSubPatternVal.isExplicit == currentBinding.isExplicit) {
                                if (currentSubpattern !is BindingPattern) unify(currentSubpattern, concreteSubPatternVal, processedSubstitutions, newPrimers, mismatchedPatterns)
                                nextConcretePattern()
                            } else if (concreteSubPatternVal == null || concreteSubPatternVal.isExplicit && !currentBinding.isExplicit) {
                                val enclosingNode = (concretePattern.data as PsiElement)

                                if (currentSubpattern !is BindingPattern) newPrimers[ImplicitConstructorPattern(enclosingNode, concreteSubPatternVal?.data as? PsiElement, skippedParameters)] = currentSubpattern
                                skippedParameters++
                            }
                        }
                    } else processMismatchedPattern()

                }
                is Concrete.NumberPattern -> processMismatchedPattern()
                is Concrete.TuplePattern -> if (pattern is ConstructorExpressionPattern && pattern.constructor == null) for (pair in concretePattern.patterns.zip(pattern.subPatterns)) {
                    unify(pair.second, pair.first, processedSubstitutions, newPrimers, mismatchedPatterns)
                } else processMismatchedPattern()
            }
        }

        fun prepareExplicitnessMask(sampleParameters: List<DependentLink>, elimParams: List<DependentLink>?): List<Boolean> =
                if (elimParams != null && elimParams.isNotEmpty()) sampleParameters.map { elimParams.contains(it) } else sampleParameters.map { it.isExplicit }

        private fun stripOfImplicitBraces(patternPsi: PsiElement): PsiElement {
            if (patternPsi is ArendPattern && patternPsi.let { it.defIdentifier == null && it.atomPatternOrPrefixList.size == 0 && it.atomPattern != null }) {
                val atom = patternPsi.atomPattern!!
                if (atom.lbrace != null && atom.rbrace != null && atom.patternList.size == 1)
                    return atom.patternList[0]
            }
            return patternPsi
        }

        fun insertPrimers(psiFactory: ArendPsiFactory, insertData: HashMap<Pair<PsiElement, PsiElement?>, MutableList<Pair<Int, Variable>>>, callback: (Variable, ArendPattern) -> Unit) {
            for (insertDataEntry in insertData) {
                val toInsertList = insertDataEntry.value
                val enclosingNode = stripOfImplicitBraces(insertDataEntry.key.first)
                val position = insertDataEntry.key.second

                var skippedParams = 0
                toInsertList.sortBy { it.first }

                var nullAnchor: PsiElement? = null
                for (entry in toInsertList) {
                    var patternLine = ""
                    for (i in 0 until entry.first - skippedParams) patternLine += " {_}"
                    patternLine += " {${entry.second.name}}"
                    val templateList = (psiFactory.createAtomPattern(patternLine).parent as ArendPattern).atomPatternOrPrefixList
                    if (position == null) {
                        if (enclosingNode is ArendPattern) {
                            if (nullAnchor == null) nullAnchor = enclosingNode.defIdentifier
                            enclosingNode.addRangeAfterWithNotification(templateList.first(), templateList.last(), nullAnchor!!)
                            nullAnchor = enclosingNode.atomPatternOrPrefixList.last()
                            nullAnchor.childOfType<ArendPattern>()?.let { callback.invoke(entry.second, it) }
                        }
                    } else {
                        enclosingNode.addRangeBeforeWithNotification(templateList.first(), templateList.last(), position)
                        position.prevSibling.childOfType<ArendPattern>()?.let { callback.invoke(entry.second, it) }
                    }
                    skippedParams = entry.first + 1
                }
            }
        }

        fun doInsertPrimers(psiFactory: ArendPsiFactory,
                            clause: Abstract.Clause,
                            typecheckedParams: List<DependentLink>,
                            eliminatedParams: List<Variable>,
                            eliminatedVars: HashSet<Variable>,
                            patternPrimers: HashMap<Variable, ArendPattern>?,
                            nameCalculator: (DependentLink) -> String) {
            if (clause !is PsiElement) return
            val patternsMap = HashMap<Variable, ArendPattern>()
            for (e in eliminatedParams.zip(clause.patterns)) (e.second as? ArendPattern)?.let { patternsMap[e.first] = it }

            var anchor: PsiElement? = null

            for (param in typecheckedParams) {
                if (eliminatedVars.contains(param)) {
                    val template = psiFactory.createClause("${nameCalculator.invoke(param)}, dummy").childOfType<ArendPattern>()!!
                    val comma = template.nextSibling
                    var commaInserted = false
                    if (anchor != null) {
                        anchor = clause.addAfter(comma, anchor)
                        commaInserted = true
                        anchor = clause.addAfterWithNotification(template, anchor ?: clause.firstChild)
                        clause.addBefore(psiFactory.createWhitespace(" "), anchor)
                    } else {
                        anchor = clause.addBeforeWithNotification(template, clause.firstChild)
                    }
                    if (patternPrimers != null) patternPrimers[param] = anchor as ArendPattern
                    if (!commaInserted) clause.addAfter(comma, anchor)
                } else {
                    anchor = patternsMap[param]
                }
            }
        }

        interface VariableLocationDescriptor
        data class ExplicitNamePattern(val bindingPsi: PsiElement) : VariableLocationDescriptor
        data class ImplicitConstructorPattern(val enclosingNode: PsiElement, val followingParameter: PsiElement?, val implicitArgCount: Int) : VariableLocationDescriptor

        fun matchConcreteWithWellTyped(enclosingNode: PsiElement, patterns: List<Concrete.Pattern>, explicitnessMask: List<Boolean>,
                                       patternParameterIterator: MutableIterator<DependentLink>,
                                       result: MutableMap<DependentLink, VariableLocationDescriptor>): Boolean {
            var concretePatternIndex = 0
            var skippedParameters = 0
            for (spExplicit in explicitnessMask) {
                if (!patternParameterIterator.hasNext()) return true
                val concretePattern = if (concretePatternIndex < patterns.size) patterns[concretePatternIndex] else null
                val data = concretePattern?.data as? PsiElement

                if (concretePattern != null && data != null && concretePattern.isExplicit == spExplicit) {
                    skippedParameters = 0
                    concretePatternIndex++

                    when (concretePattern) {
                        is Concrete.TuplePattern -> {
                            matchConcreteWithWellTyped(data, concretePattern.patterns, List(concretePattern.patterns.size) { true }, patternParameterIterator, result)
                        }
                        is Concrete.NamePattern -> {
                            val patternParameter = patternParameterIterator.next()
                            result[patternParameter] = ExplicitNamePattern(data)
                        }
                        is Concrete.ConstructorPattern -> {
                            val typechecked = (concretePattern.constructor as? DataLocatedReferable)?.typechecked as? Constructor
                                    ?: return false
                            if (!matchConcreteWithWellTyped(data, concretePattern.patterns, prepareExplicitnessMask(DependentLink.Helper.toList(typechecked.parameters), null), patternParameterIterator, result)) return false
                        }
                    }
                    continue
                }
                if (!spExplicit) {
                    val patternParameter = patternParameterIterator.next()
                    result[patternParameter] = ImplicitConstructorPattern(enclosingNode, data, skippedParameters)
                    skippedParameters += 1
                }
            }
            return true
        }
    }
}

