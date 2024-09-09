package org.arend.codeInsight.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.completion.CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PlatformPatterns
import com.intellij.patterns.StandardPatterns.*
import com.intellij.psi.*
import com.intellij.psi.TokenType.BAD_CHARACTER
import com.intellij.psi.util.elementType
import com.intellij.psi.util.startOffset
import com.intellij.util.ProcessingContext
import org.arend.psi.*
import org.arend.psi.ArendElementTypes.*
import org.arend.psi.ext.*
import org.arend.repl.CommandHandler
import org.arend.search.ArendWordScanner
import org.arend.term.abs.Abstract
import java.util.*
import kotlin.math.max
import kotlin.math.min

class ArendCompletionContributor : CompletionContributor() {

    init {
        basic(PREC_CONTEXT, FIXITY_KWS)

        basic(PlatformPatterns.psiElement(REPL_COMMAND), CommandHandler.INSTANCE.commandMap.keys)

        basic(after(and(ofType(LEVEL_KW), elementPattern { o -> (o.parent as? ArendDefFunction)?.functionKind?.isUse == true })), FIXITY_KWS)

        basic(and(withGrandParent(ArendNsId::class.java), withParents(ArendRefIdentifier::class.java, PsiErrorElement::class.java), not(afterLeaves(LPAREN, COMMA))), AS_KW_LIST)
        { parameters -> (parameters.position.parent.parent as ArendNsId).asKw == null }

        basic(NS_CMD_CONTEXT, HU_KW_LIST) { parameters ->
            parameters.originalPosition?.parent is ArendFile
        }

        fun noUsing(cmd: ArendStatCmd): Boolean = cmd.nsUsing?.usingKw == null
        fun noHiding(cmd: ArendStatCmd): Boolean = cmd.hidingKw == null
        fun noUsingAndHiding(cmd: ArendStatCmd): Boolean = noUsing(cmd) && noHiding(cmd)

        basic(NS_CMD_CONTEXT, USING_KW_LIST) { parameters ->
            noUsing(parameters.position.parent.parent as ArendStatCmd)
        }

        basic(NS_CMD_CONTEXT, HIDING_KW_LIST) { parameters ->
            noUsingAndHiding(parameters.position.parent.parent as ArendStatCmd)
        }

        basic(and(withAncestors(PsiErrorElement::class.java, ArendNsUsing::class.java, ArendStatCmd::class.java), not(afterLeaf(COMMA))), HIDING_KW_LIST) { parameters ->
            noHiding(parameters.position.parent.parent.parent as ArendStatCmd)
        }

        basic(STATEMENT_END_CONTEXT, JointOfStatementsProvider(STATEMENT_WT_KWS + ACCESS_MODIFIERS))

        basic(STATEMENT_END_CONTEXT, JointOfStatementsProvider(STATEMENT_WT_KWS + TRUNCATED_DATA_KW_LIST, {aCP ->
            val grandParent: PsiElement? = if (aCP.ancestors.size > 2) aCP.ancestors[2] else null
            val constructorOrClassField = grandParent?.hasChildOfType(PIPE) == true || grandParent is ArendConstructor
            aCP.prevElement.elementType in listOf(PROTECTED_KW, PRIVATE_KW) && !constructorOrClassField
         }, noCrlfRequired = true))

        // completion for \truncated keywords
        fun nextElementIsData(cP : ArendCompletionParameters) = cP.nextElement.elementType == DATA_KW
        basic(STATEMENT_END_CONTEXT, JointOfStatementsProvider(TRUNCATED_DATA_KW_LIST, { completionParameters -> !nextElementIsData(completionParameters) }))
        basic(STATEMENT_END_CONTEXT, JointOfStatementsProvider(TRUNCATED_KW_LIST, { completionParameters -> nextElementIsData(completionParameters) }))
        basic(afterLeaves(PRIVATE_KW, PROTECTED_KW), TRUNCATED_KW_LIST) { completionParameters -> ArendCompletionParameters(completionParameters).nextElement.elementType == DATA_KW }

        basic(STATEMENT_END_CONTEXT, JointOfStatementsProvider(IMPORT_KW_LIST, { parameters ->
            val noWhere = { seq: Sequence<PsiElement> -> seq.filter { it is ArendWhere || it is ArendDefClass }.none() }

            parameters.leftStatement?.ancestors.let { if (it != null) noWhere(it) else true } &&
                    parameters.rightStatement?.ancestors.let { if (it != null) noWhere(it) else true } &&
                    (!parameters.leftBrace || parameters.ancestorsPE.isEmpty() || noWhere(parameters.ancestorsPE.asSequence())) &&
                    (!parameters.rightBrace || parameters.ancestorsNE.isEmpty() || noWhere(parameters.ancestorsNE.asSequence()))
        }))

        val definitionWhereModulePattern = { allowData: Boolean, allowFunc: Boolean ->
            elementPattern { o ->
                var foundWhere = false
                var foundLbrace = false
                var result2 = false
                var ancestor: PsiElement? = o
                while (ancestor != null) {
                    if (ancestor is ArendFieldTele && ancestor.lbrace != null) foundLbrace = true
                    if (ancestor is ArendWhere) foundWhere = true
                    if ((allowData && ancestor is ArendDefData || allowFunc && ancestor is ArendDefFunction)) {
                        result2 = foundWhere
                        break
                    } else if (ancestor is ArendDefClass) {
                        if (ancestor.lbrace != null) foundLbrace = true
                        result2 = if (allowData) foundWhere else !foundWhere && foundLbrace
                        break
                    } else if (ancestor is ArendDefinition<*> && foundWhere) {
                        result2 = false
                        break
                    }
                    ancestor = ancestor.parent
                }
                result2
            }
        }

        basic(and(STATEMENT_END_CONTEXT, definitionWhereModulePattern(false, false)), JointOfStatementsProvider(CLASS_MEMBER_KWS, allowBeforeClassFields = true))

        basic(and(STATEMENT_END_CONTEXT, definitionWhereModulePattern(true, true)), JointOfStatementsProvider(USE_KW_LIST))

        basic(and(afterLeaf(USE_KW), definitionWhereModulePattern(true, false)), COERCE_KW_LIST)

        basic(afterLeaf(USE_KW), LEVEL_KW_LIST)

        basic(and(DATA_CONTEXT, afterLeaf(TRUNCATED_KW)), DATA_KW_LIST)//data after \truncated keyword

        val wherePattern = elementPattern { o ->
            var anc: PsiElement? = o
            while (anc != null && anc !is ArendGroup && anc !is ArendClassStat) anc = anc.parent
            anc is ArendGroup && anc !is ArendFile && anc.where == null
        }

        basic(and(WHERE_CONTEXT, after(wherePattern)),
                JointOfStatementsProvider(WHERE_KW_LIST, noCrlfRequired = true, allowInsideBraces = false, completionBehavior = KeywordCompletionBehavior.ADD_BRACES))

        val inDefClassPattern = or(and(ofType(ID), withAncestors(ArendDefIdentifier::class.java, ArendDefClass::class.java)),
                and(or(ofType(RPAREN), ofType(RBRACE)), withAncestors(ArendFieldTele::class.java, ArendDefClass::class.java)),
                and(ofType(ID), withAncestors(ArendAliasIdentifier::class.java, ArendAlias::class.java, ArendDefClass::class.java)))

        val noExtendsPattern = elementPattern {
            val dC = it.ancestor<ArendDefClass>()
            dC != null && dC.extendsKw == null
        }

        val notWithGrandparentFieldTelePattern = elementPattern { o -> o.parent?.parent?.findNextSibling() !is ArendFieldTele }

        basic(and(withAncestors(PsiErrorElement::class.java, ArendDefClass::class.java),
                afterLeaf(ID),
                noExtendsPattern,
                after(inDefClassPattern)),
                EXTENDS_KW_LIST)

        basic(and(withAncestors(PsiErrorElement::class.java, ArendFieldTele::class.java, ArendDefClass::class.java),
                noExtendsPattern,
                after(inDefClassPattern),
                notWithGrandparentFieldTelePattern),
                EXTENDS_KW_LIST)

        basic(and(DATA_CONTEXT, afterLeaf(COLON)), DATA_UNIVERSE_KW, completionBehavior = KeywordCompletionBehavior.DEFAULT)

        basic(and(EXPRESSION_CONTEXT, expressionPattern.invoke(true, true)), DATA_UNIVERSE_KW, completionBehavior = KeywordCompletionBehavior.DEFAULT)
        basic(or(TELE_CONTEXT, FIRST_TYPE_TELE_CONTEXT), DATA_UNIVERSE_KW, completionBehavior = KeywordCompletionBehavior.DEFAULT)

        basic(BASIC_EXPRESSION_KW_PATTERN, BASIC_EXPRESSION_KW)
        basic(and(or(EXPRESSION_CONTEXT, TELE_CONTEXT), expressionPattern.invoke(false, true)), NEW_KW_LIST)
        basic(or(withAncestors(ArendTypeTele::class.java, ArendSigmaExpr::class.java),
                 withAncestors(*(NEW_EXPR_PREFIX + listOf(ArendTypedExpr::class.java, ArendTypeTele::class.java, ArendSigmaExpr::class.java)))), SIGMA_TELE_START_KWS)

        val truncatedTypeInsertHandler = InsertHandler<LookupElement> { insertContext, _ ->
            val document = insertContext.document
            document.insertString(insertContext.tailOffset, " ") // add tail whitespace
            insertContext.commitDocument()
            insertContext.editor.caretModel.moveToOffset(insertContext.startOffset + 1)
            document.replaceString(insertContext.startOffset + 1, insertContext.startOffset + 2, "1") //replace letter n by 1 so that the keyword would be highlighted correctly
            insertContext.editor.selectionModel.setSelection(insertContext.startOffset + 1, insertContext.startOffset + 2)
        }

        val truncatedTypeCompletionProvider = object : KeywordCompletionProvider(FAKE_NTYPE_LIST) {
            override fun insertHandler(keyword: String): InsertHandler<LookupElement> = truncatedTypeInsertHandler
        }

        basic(and(DATA_CONTEXT, afterLeaf(COLON)), truncatedTypeCompletionProvider)
        basic(and(EXPRESSION_CONTEXT, expressionPattern(true, true)), object : KeywordCompletionProvider(FAKE_NTYPE_LIST) {
            override fun insertHandler(keyword: String): InsertHandler<LookupElement> = truncatedTypeInsertHandler
        })
        basic(or(TELE_CONTEXT, FIRST_TYPE_TELE_CONTEXT), truncatedTypeCompletionProvider)

        val numberCondition = { parameters: CompletionParameters ->
            val originalPositionParent = parameters.originalPosition?.parent
            originalPositionParent != null && originalPositionParent.text == "\\" && originalPositionParent.nextSibling?.node?.elementType == NUMBER
        }

        basic(and(DATA_OR_EXPRESSION_CONTEXT), object : ConditionalProvider(listOf("-Type"), numberCondition, completionBehavior = KeywordCompletionBehavior.DEFAULT) {
            override fun computePrefix(parameters: CompletionParameters, resultSet: CompletionResultSet): String = ""
        })

        basic(DATA_OR_EXPRESSION_CONTEXT, object : ConditionalProvider(listOf("Type"), { parameters ->
            parameters.originalPosition?.text?.matches(Regex("\\\\[0-9]+(-(T(y(pe?)?)?)?)?")) ?: false
        }, completionBehavior = KeywordCompletionBehavior.DEFAULT) {
            override fun computePrefix(parameters: CompletionParameters, resultSet: CompletionResultSet): String =
                    super.computePrefix(parameters, resultSet).replace(Regex("\\\\[0-9]+-?"), "")
        })

        basic(LPH_CONTEXT, LPH_KW_LIST) { parameters ->
            when (val pp = parameters.position.parent.parent) {
                is ArendSetUniverseAppExpr, is ArendTruncatedUniverseAppExpr ->
                    pp.children.filterIsInstance<ArendMaybeAtomLevelExpr>().isEmpty()
                else -> pp.children.filterIsInstance<ArendMaybeAtomLevelExpr>().size <= 1
            }
        }

        basic(withParent(ArendLevelExpr::class.java), LPH_KW_LIST) { parameters ->
            when (parameters.position.parent?.firstChild?.node?.elementType) {
                MAX_KW, SUC_KW -> true
                else -> false
            }
        }

        basic(LPH_LEVEL_CONTEXT, LPH_LEVEL_KWS)

        fun pairingWordCondition(condition: (PsiElement?) -> Boolean, position: PsiElement): Boolean {
            var pos: PsiElement? = position
            var exprFound = false
            while (pos != null) {
                if (condition.invoke(pos)) {
                    exprFound = true
                    break
                }
                if (!(pos.nextSibling == null || pos.nextSibling is PsiErrorElement)) break
                pos = pos.parent
            }
            return exprFound
        }

        val pairingInPattern = elementPattern { o -> pairingWordCondition({ position: PsiElement? -> position is ArendLetExpr && position.inKw == null }, o) }
        val pairingWithPattern = elementPattern { o -> pairingWordCondition({ position: PsiElement? -> position is ArendCaseExpr && position.withBody == null }, o) }

        val returnPattern = elementPattern { o ->
            pairingWordCondition({ position: PsiElement? ->
                if (position is ArendCaseArg) {
                    val pp = position.parent as? ArendCaseExpr
                    pp != null && pp.caseArguments.lastOrNull() == position && pp.returnKw == null
                } else false
            }, o)
        }

        val asCondition1 = { position: PsiElement? -> position is ArendCaseArg && position.asKw == null }

        val asCondition2 = { position: PsiElement? ->
            //Alternative condition needed to ensure that as is added before semicolon
            var p = position
            if (p != null && p.nextSibling is PsiWhiteSpace) p = p.nextSibling.nextSibling
            p != null && p.node.elementType == COLON
        }

        val argEndPattern = elementPattern { o ->
            pairingWordCondition({ position: PsiElement? ->
                asCondition1.invoke(position) || (position != null && asCondition2.invoke(position) && asCondition1.invoke(position.parent))
            }, o)
        }

        basic(and(EXPRESSION_CONTEXT, not(or(afterLeaf(IN_KW), afterLeaf(HAVE_KW), afterLeaf(LET_KW), afterLeaf(HAVES_KW), afterLeaf(LETS_KW))), pairingInPattern), IN_KW_LIST)

        val caseContext = and(
                or(EXPRESSION_CONTEXT,
                        withAncestors(PsiErrorElement::class.java, ArendCaseArg::class.java, ArendCaseExpr::class.java)),
                not(afterLeaves(WITH_KW, CASE_KW, SCASE_KW, COLON)))

        basic(and(caseContext, pairingWithPattern), KeywordCompletionProvider(WITH_KW_LIST, completionBehavior = KeywordCompletionBehavior.ADD_BRACES))

        basic(and(caseContext, argEndPattern), AS_KW_LIST)

        basic(and(caseContext, returnPattern), RETURN_KW_LIST)

        val emptyTeleList = { l: List<Abstract.Parameter> ->
            l.isEmpty() || l.size == 1 && (l[0].type == null || (l[0].type as PsiElement).text == DUMMY_IDENTIFIER_TRIMMED) &&
                    (l[0].referableList.size == 0 || l[0].referableList[0] == null || (l[0].referableList[0] as PsiElement).text == DUMMY_IDENTIFIER_TRIMMED)
        }
        val elimOrCoWithCondition = { coWithMode: Boolean ->
            { cP: CompletionParameters ->
                var pos2: PsiElement? = cP.position
                var exprFound = false
                while (pos2 != null) {
                    if (pos2.nextSibling is PsiWhiteSpace) {
                        val nextElement = pos2.findNextSibling()
                        if (nextElement is ArendFunctionBody || nextElement is ArendDataBody || nextElement is ArendWhere) pos2 = nextElement.parent
                    }

                    if ((pos2 is ArendDefFunction)) {
                        val fBody = pos2.body
                        exprFound = fBody == null || fBody.fatArrow == null && fBody.elim?.elimKw == null
                        exprFound = exprFound &&
                                if (!coWithMode) !emptyTeleList(pos2.parameters)  // No point of writing elim keyword if there are no arguments
                                else {
                                    val returnExpr = pos2.returnExpr
                                    returnExpr != null && returnExpr.levelKw == null
                                } // No point of writing cowith keyword if there is no result type or there is already \level keyword in result type
                        exprFound = exprFound && (fBody == null || fBody.cowithKw == null && fBody.elim.let { it == null || it.elimKw == null && it.withKw == null })
                        break
                    }
                    if ((pos2 is ArendDefData) && !coWithMode) {
                        val dBody = pos2.dataBody
                        exprFound = dBody == null || (dBody.elim?.elimKw == null && dBody.constructorList.isEmpty() && dBody.constructorClauseList.isEmpty())
                        exprFound = exprFound && !emptyTeleList(pos2.parameters)
                        break
                    }

                    if (pos2 is ArendConstructor && pos2.elim == null && !coWithMode) {
                        exprFound = !emptyTeleList(pos2.parameters)
                        break
                    }

                    if (pos2 is ArendClause || pos2 is ArendCoClause) break

                    if (pos2?.nextSibling == null) pos2 = pos2?.parent else break
                }
                exprFound
            }
        }

        val coClauseDefCondition = {coWithMode: Boolean ->
            { cP: CompletionParameters ->
                var pos3: PsiElement? = cP.position
                var exprFound = false
                while (pos3 != null) {
                    if (pos3.nextSibling is PsiWhiteSpace) {
                        val nextElement = pos3.findNextSibling()
                        if (nextElement is ArendFunctionBody || nextElement is ArendDataBody || nextElement is ArendWhere) pos3 = nextElement.parent
                    }

                    if (pos3 is ArendCoClauseDef) {
                        exprFound = !coWithMode && pos3.parameters.isNotEmpty() || coWithMode && pos3.returnExpr != null
                        break
                    }

                    if (pos3?.nextSibling == null) pos3 = pos3?.parent else break
                }
                exprFound
            }
        }

        fun conditionOr (a : (CompletionParameters)-> Boolean, b : (CompletionParameters)-> Boolean): (CompletionParameters) -> Boolean = { cp -> a.invoke(cp) || b.invoke(cp)}

        basic(ELIM_CONTEXT, ELIM_KW_LIST, conditionOr(elimOrCoWithCondition.invoke(false), coClauseDefCondition.invoke(false)))
        basic(ELIM_CONTEXT, ConditionalProvider(COWITH_KW_LIST, conditionOr(elimOrCoWithCondition.invoke(true), coClauseDefCondition.invoke(true)), completionBehavior = KeywordCompletionBehavior.DEFAULT))

        basic(ELIM_CONTEXT, ConditionalProvider(WITH_KW_LIST, elimOrCoWithCondition.invoke(false), completionBehavior = KeywordCompletionBehavior.DEFAULT))
        basic(ELIM_CONTEXT, ConditionalProvider(WITH_KW_LIST, coClauseDefCondition.invoke(false), completionBehavior = KeywordCompletionBehavior.ADD_BRACES))

        basic(and(
                afterLeaves(COMMA, CASE_KW),
                or(withAncestors(PsiErrorElement::class.java, ArendCaseExpr::class.java),
                        withAncestors(*(NEW_EXPR_PREFIX + arrayOf(ArendCaseArg::class.java, ArendCaseExpr::class.java))))), ELIM_KW_LIST)

        val isLiteralApp = { argumentAppExpr: ArendArgumentAppExpr ->
            argumentAppExpr.longNameExpr != null ||
                    ((argumentAppExpr.children[0] as? ArendAtomFieldsAcc)?.atom?.literal?.longName != null)
        }

        val unifiedLevelCondition = { atomIndex: Int?, forbidLevelExprs: Boolean, threshold: Int ->
            { cP: CompletionParameters ->
                var anchor: PsiElement? = if (atomIndex != null) cP.position.ancestors.filterIsInstance<ArendAtomFieldsAcc>().elementAtOrNull(atomIndex) else null
                var argumentAppExpr: ArendArgumentAppExpr? =
                        (anchor?.parent as? ArendAtomArgument)?.parent as? ArendArgumentAppExpr
                                ?: anchor?.parent as? ArendArgumentAppExpr
                if (anchor == null) {
                    anchor = cP.position.parent
                    argumentAppExpr = anchor?.parent as? ArendArgumentAppExpr
                    if (argumentAppExpr == null) {
                        anchor = null
                    }
                }

                if (anchor == null) {
                    argumentAppExpr = cP.position.parent as? ArendArgumentAppExpr
                }

                if (argumentAppExpr != null && anchor != null && isLiteralApp(argumentAppExpr)) {
                    val longNameExpr = argumentAppExpr.longNameExpr
                    var counter = 0
                    if (longNameExpr?.pLevelExpr != null) counter++
                    if (longNameExpr?.hLevelExpr != null) counter++
                    var forbidden = false
                    val levelsExpr = argumentAppExpr.longNameExpr?.levelsExpr
                    if (levelsExpr != null) {
                        if (levelsExpr.pLevelExprs != null) counter++
                        if (levelsExpr.hLevelExprs != null) counter++
                        if (forbidLevelExprs) forbidden = true
                    }
                    for (ch in argumentAppExpr.children) {
                        if (ch == anchor || ch == anchor.parent) break
                        if (ch is ArendAtomArgument) forbidden = true
                    }
                    counter < threshold && !forbidden
                } else (argumentAppExpr?.longNameExpr?.levelsExpr?.levelsKw != null && isLiteralApp(argumentAppExpr)) || (ATOM_LEVEL_CONTEXT.accepts(cP.position))
            }
        }

        basic(or(ARGUMENT_EXPRESSION, ATOM_LEVEL_CONTEXT), LPH_KW_LIST, unifiedLevelCondition.invoke(0, false, 2))

        fun trueForPsiOrFragmentContext (condition: (PsiElement) -> Boolean, psi : PsiElement) : Boolean =
            condition(psi) || psi.ancestor<ArendExpressionCodeFragment>()?.context?.let{ condition(it) } == true

        basic(or(ARGUMENT_EXPRESSION, EXPRESSION_CONTEXT,
            withAncestors(PsiErrorElement::class.java, ArendAtomFieldsAcc::class.java, ArendAtomArgument::class.java, ArendArgumentAppExpr::class.java),
            withAncestors(PsiErrorElement::class.java, ArendNewExpr::class.java)), THIS_KW_LIST) { cP: CompletionParameters ->
            trueForPsiOrFragmentContext({psi -> ((psi.parent?.parent as? ArendNewExpr)?.let { it.lbrace != null } ?: true) && (isInDynamicPart(psi) != null)}, cP.position)
        }

        basic(ARGUMENT_EXPRESSION, LEVELS_KW_LIST, unifiedLevelCondition.invoke(0, true, 1))
        basic(or(ARGUMENT_EXPRESSION_IN_BRACKETS, ATOM_LEVEL_CONTEXT), LPH_LEVEL_KWS, unifiedLevelCondition.invoke(1, false, 2))

        basic(withAncestors(PsiErrorElement::class.java, ArendArgumentAppExpr::class.java), LPH_LEVEL_KWS, unifiedLevelCondition.invoke(null, false, 2))

        basic(withParent(ArendArgumentAppExpr::class.java), LPH_LEVEL_KWS) { parameters ->
            val argumentAppExpr: ArendArgumentAppExpr = parameters.position.parent as ArendArgumentAppExpr
            argumentAppExpr.longNameExpr?.levelsExpr?.levelsKw != null && isLiteralApp(argumentAppExpr)
        }

        basic(afterLeaf(NEW_KW), listOf(EVAL_KW.toString()))
        basic(afterLeaves(EVAL_KW, PEVAL_KW, SCASE_KW), listOf(SCASE_KW.toString()))

        basic(NO_CLASSIFYING_CONTEXT, NO_CLASSIFYING_KW_LIST) { parameters ->
            parameters.position.ancestor<ArendDefClass>().let { defClass ->
                defClass != null && defClass.noClassifyingKw == null && !defClass.fieldTeleList.any { it.isClassifying }
            }
        }

        basic(CLASSIFYING_CONTEXT, CLASSIFYING_KW_LIST) { parameters ->
            parameters.position.ancestor<ArendDefClass>().let { defClass ->
                defClass != null && !defClass.isRecord && defClass.noClassifyingKw == null && !defClass.fieldTeleList.any { it.isClassifying }
            }
        }

        basic(or(
            and(afterLeaf(LPAREN), CLASSFIELD_CONTEXT),
            and(afterLeaves(PIPE), CLASS_CONTEXT),
            and(not(afterLeaves(FIELD_KW, PROTECTED_KW, PRIVATE_KW, COERCE_KW)),
                or(withAncestors(ArendDefIdentifier::class.java,  ArendConstructor::class.java),
                    withAncestors(PsiErrorElement::class.java, ArendDataBody::class.java),
                    withAncestors(PsiErrorElement::class.java, ArendConstructorClause::class.java),
                    withAncestors(PsiErrorElement::class.java, ArendConstructor::class.java)))), ACCESS_MODIFIERS)

        basic(or(CLASSIFYING_CONTEXT, and(afterLeaves(PIPE, PRIVATE_KW, PROTECTED_KW),
            or(withAncestors(ArendDefData::class.java), withAncestors(PsiErrorElement::class.java, ArendDataBody::class.java),
               withAncestors(ArendDefIdentifier::class.java, ArendConstructor::class.java, ArendDataBody::class.java),
               withAncestors(PsiErrorElement::class.java, ArendConstructor::class.java, ArendDataBody::class.java)))), COERCE_KW_LIST)

        basic(and(LEVEL_CONTEXT, allowedInReturnPattern), LEVEL_KW_LIST)

        basic(and(afterLeaf(ID), or(
                withAncestors(PsiErrorElement::class.java, ArendDefModule::class.java),
                after(and(withParent(ArendDefIdentifier::class.java), withGrandParents(ArendDefData::class.java, ArendDefInstance::class.java,
                        ArendDefFunction::class.java, ArendConstructor::class.java, ArendDefClass::class.java, ArendClassField::class.java))),
                after(withAncestors(ArendRefIdentifier::class.java, ArendLongName::class.java, ArendClassImplement::class.java)))), ALIAS_KW_LIST) {cP ->
            cP.position.ancestor<ReferableBase<*>>()?.alias == null
        }

        basic(and(afterLeaf(ID), after(and(withParent(ArendDefIdentifier::class.java), withGrandParents(ArendDefData::class.java, ArendDefInstance::class.java,
                ArendDefFunction::class.java, ArendDefClass::class.java)))), PH_LEVELS_KW_LIST)

        val levelParamsPattern = withAncestors(ArendLevelIdentifier::class.java, ArendLevelParamsSeq::class.java)

        basic(or(afterLeaf(PLEVELS_KW), after(levelParamsPattern)), HLEVELS_KW_LIST) { cP: CompletionParameters ->
            val jointData = ArendCompletionParameters(cP)
            val prevKeyword = jointData.prevElement?.parent?.parent?.findPrevSibling()
            if (prevKeyword != null) prevKeyword.elementType == PLEVELS_KW else true
        }

        basic(and(afterLeaf(LPAREN), or(withAncestors(ArendNameTele::class.java, ArendDefFunction::class.java),
                withAncestors(ArendTypeTele::class.java, ArendConstructor::class.java),
                withAncestors(*(DEF_IDENTIFIER_PREFIX + arrayOf(ArendDefFunction::class.java))),
                withAncestors(ArendDefIdentifier::class.java, ArendIdentifierOrUnknown::class.java,
                              ArendTypedExpr::class.java, ArendTypeTele::class.java, ArendConstructor::class.java),
                withAncestors(ArendRefIdentifier::class.java, ArendLongName::class.java, ArendLiteral::class.java,
                              ArendAtom::class.java, ArendAtomFieldsAcc::class.java, ArendArgumentAppExpr::class.java,
                              ArendNewExpr::class.java, ArendTypedExpr::class.java, ArendTypeTele::class.java, ArendConstructor::class.java))), PARAM_ATTR_LIST)

        basic(and(or(withAncestors(*(ATOM_FIELDS_ACC_PREFIX + arrayOf<Class<out PsiElement>>(ArendAtomArgument::class.java, ArendArgumentAppExpr::class.java, ArendNewExpr::class.java))),
                withAncestors(PsiErrorElement::class.java, ArendAtomFieldsAcc::class.java, ArendAtomArgument::class.java, ArendArgumentAppExpr::class.java, ArendNewExpr::class.java),
                withAncestors(PsiErrorElement::class.java, ArendAtomFieldsAcc::class.java, ArendArgumentAppExpr::class.java, ArendNewExpr::class.java),
                and(withAncestors(PsiErrorElement::class.java, ArendArgumentAppExpr::class.java, ArendNewExpr::class.java), after(withParent(ArendAtomLevelExpr::class.java))),
                after(and(ofType(RBRACE), withParent(ArendNewExpr::class.java)))),
                elementPattern { o -> o.parentOfType<ArendNewExpr>().let {
                    it != null && it.withBody == null && (it.argumentAppExpr?.atomFieldsAcc?.atom?.literal?.longName != null || it.argumentAppExpr?.longNameExpr != null)
                }}), WITH_KW_LIST, KeywordCompletionBehavior.ADD_BRACES)

        basic(after(elementPattern { it.parentOfType<ArendPattern>() != null }), AS_KW_LIST)

        //basic(PlatformPatterns.psiElement(), Logger())
    }

    private fun basic(pattern: ElementPattern<PsiElement>, provider: CompletionProvider<CompletionParameters>) {
        extend(CompletionType.BASIC, pattern, provider)
    }

    private fun basic(place: ElementPattern<PsiElement>, keywords: Collection<String>, completionBehavior: KeywordCompletionBehavior = KeywordCompletionBehavior.ADD_WHITESPACE) {
        basic(place, KeywordCompletionProvider(keywords, completionBehavior))
    }

    private fun basic(place: ElementPattern<PsiElement>, keywords: Collection<String>, condition: (CompletionParameters) -> Boolean) {
        basic(place, ConditionalProvider(keywords, condition))
    }

    companion object {
        const val KEYWORD_PRIORITY = 0.0
        private val LITERAL_PREFIX = arrayOf<Class<out PsiElement>>(ArendRefIdentifier::class.java, ArendLongName::class.java, ArendLiteral::class.java)
        private val ATOM_PREFIX = LITERAL_PREFIX + arrayOf(ArendAtom::class.java)
        private val ATOM_FIELDS_ACC_PREFIX = ATOM_PREFIX + arrayOf(ArendAtomFieldsAcc::class.java)
        private val ARGUMENT_APP_EXPR_PREFIX = ATOM_FIELDS_ACC_PREFIX + arrayOf(ArendArgumentAppExpr::class.java)
        private val NEW_EXPR_PREFIX = ARGUMENT_APP_EXPR_PREFIX + arrayOf(ArendNewExpr::class.java)
        private val RETURN_EXPR_PREFIX = ATOM_FIELDS_ACC_PREFIX + arrayOf(ArendReturnExpr::class.java)
        private val DEF_IDENTIFIER_PREFIX = arrayOf<Class<out PsiElement>>(ArendDefIdentifier::class.java, ArendIdentifierOrUnknown::class.java, ArendNameTele::class.java)
        private val ATOM_LEVEL_PREFIX = arrayOf<Class<out PsiElement>>(ArendRefIdentifier::class.java, ArendAtomLevelExpr::class.java)

        private val PREC_CONTEXT = or(
                afterLeaves(FUNC_KW, SFUNC_KW, LEMMA_KW, TYPE_KW, CONS_KW, DATA_KW, CLASS_KW, RECORD_KW, AXIOM_KW, ALIAS_KW),
                and(afterLeaf(AS_KW), withGrandParent(ArendNsId::class.java)),
                and(afterLeaf(FAT_ARROW), withGrandParents(ArendConstructor::class.java, ArendConstructorClause::class.java)), //data type constructors with patterns
                and(afterLeaves(PIPE, FIELD_KW, PROPERTY_KW, COERCE_KW, CLASSIFYING_KW, PRIVATE_KW, PROTECTED_KW),
                        withGrandParents(ArendClassField::class.java, ArendClassStat::class.java, ArendDefClass::class.java, ArendFieldDefIdentifier::class.java)), //class field
                and(afterLeaves(COERCE_KW, PRIVATE_KW, PROTECTED_KW),
                    or(withAncestors(ArendDefData::class.java), withAncestors(PsiErrorElement::class.java, ArendConstructor::class.java),
                       withAncestors(ArendDefIdentifier::class.java, ArendConstructor::class.java, ArendDataBody::class.java, ArendDefData::class.java))),
                and(afterLeaves(PIPE, PRIVATE_KW, PROTECTED_KW), or(withGrandParents(ArendConstructor::class.java, ArendDataBody::class.java),
                                withParents(ArendDefData::class.java))), //simple data type constructor
                and(afterLeaves(CLASSIFYING_KW, COERCE_KW, PROPERTY_KW, PRIVATE_KW, PROTECTED_KW), or(
                        withParents(ArendDefClass::class.java, ArendClassStat::class.java),
                        withAncestors(PsiErrorElement::class.java, ArendDefClass::class.java),
                        withAncestors(PsiErrorElement::class.java, ArendClassStat::class.java, ArendDefClass::class.java))),
                and(afterLeaf(PIPE), or(withAncestors(ArendRefIdentifier::class.java, ArendLongName::class.java, ArendClassImplement::class.java, ArendClassStat::class.java, ArendDefClass::class.java),
                        withAncestors(ArendRefIdentifier::class.java, ArendLongName::class.java, ArendClassImplement::class.java, ArendDefClass::class.java))))

        private val NS_CMD_CONTEXT = withAncestors(PsiErrorElement::class.java, ArendStatCmd::class.java)

        val STATEMENT_END_CONTEXT = or(
            withParents(PsiErrorElement::class.java, ArendRefIdentifier::class.java),
            withAncestors(ArendDefIdentifier::class.java, ArendFieldDefIdentifier::class.java) //Needed for correct completion inside empty classes
        )

        private val INSIDE_RETURN_EXPR_CONTEXT = or(withAncestors(*RETURN_EXPR_PREFIX), withAncestors(PsiErrorElement::class.java, ArendAtomFieldsAcc::class.java, ArendReturnExpr::class.java))

        private val WHERE_CONTEXT = and(
                or(STATEMENT_END_CONTEXT, withAncestors(*DEF_IDENTIFIER_PREFIX), withAncestors(ArendLevelIdentifier::class.java, ArendLevelParamsSeq::class.java)),
                not(PREC_CONTEXT),
                not(INSIDE_RETURN_EXPR_CONTEXT),
                not(afterLeaves(COLON, TRUNCATED_KW, FAT_ARROW, WITH_KW, ARROW, IN_KW, INSTANCE_KW, EXTENDS_KW, DOT, NEW_KW, EVAL_KW, PEVAL_KW, CASE_KW, SCASE_KW, HAVE_KW, LET_KW, HAVES_KW, LETS_KW, WHERE_KW, USE_KW, PIPE, LEVEL_KW, COERCE_KW, PRIVATE_KW, PROTECTED_KW)),
                not(withAncestors(PsiErrorElement::class.java, ArendDefInstance::class.java)), // don't allow \where in incomplete instance expressions
                not(withAncestors(ArendDefIdentifier::class.java, ArendIdentifierOrUnknown::class.java, ArendNameTele::class.java, ArendDefInstance::class.java)))

        private val DATA_CONTEXT = withAncestors(PsiErrorElement::class.java, ArendDefData::class.java, ArendStat::class.java)

        private val TELE_CONTAINERS = arrayOf<Class<out PsiElement>>(ArendClassField::class.java, ArendConstructor::class.java, ArendDefData::class.java, ArendPiExpr::class.java, ArendSigmaExpr::class.java, ArendFunctionDefinition::class.java, ArendCoClauseDef::class.java)
        private val TELE_CONTEXT = or(
                and(withParent(PsiErrorElement::class.java), withGrandParents(ArendTypeTele::class.java, ArendNameTele::class.java), withGreatGrandParents(*TELE_CONTAINERS)),
                and(withParents(ArendTypeTele::class.java, ArendNameTele::class.java), withGrandParents(*TELE_CONTAINERS)),
                withAncestors(*(LITERAL_PREFIX + arrayOf(ArendTypeTele::class.java))))

        private val EXPRESSION_CONTEXT = and(
                or(withAncestors(*ATOM_PREFIX),
                        withParentOrGrandParent(ArendFunctionBody::class.java),
                        and(withParentOrGrandParent(ArendExpr::class.java), not(INSIDE_RETURN_EXPR_CONTEXT)),
                        withAncestors(PsiErrorElement::class.java, ArendClause::class.java),
                        withAncestors(PsiErrorElement::class.java, ArendTupleExpr::class.java),
                        and(afterLeaf(LPAREN), withAncestors(PsiErrorElement::class.java, ArendReturnExpr::class.java)),
                        and(afterLeaf(COLON), withAncestors(PsiErrorElement::class.java, ArendDefFunction::class.java)),
                        and(afterLeaf(COLON), withParent(ArendDefClass::class.java)),
                        and(not(afterLeaves(PRIVATE_KW, PROTECTED_KW)), or(withParent(ArendClassStat::class.java), withAncestors(PsiErrorElement::class.java, ArendClassStat::class.java))),
                        and(ofType(INVALID_KW), withAncestors(ArendFunctionBody::class.java, ArendDefInstance::class.java)),
                        and(not(afterLeaf(LPAREN)), not(afterLeaf(ID)), withAncestors(PsiErrorElement::class.java, ArendFieldTele::class.java)),
                        withAncestors(ArendNameTele::class.java, ArendDefFunction::class.java),
                        withAncestors(ArendTypeTele::class.java, ArendDefData::class.java)),
                not(afterLeaves(PIPE, COWITH_KW, COERCE_KW, CLASSIFYING_KW, FIELD_KW, PROPERTY_KW))) // no expression keywords after pipe

        private val FIRST_TYPE_TELE_CONTEXT = and(afterLeaf(ID), withParent(PsiErrorElement::class.java),
                withGrandParents(ArendDefData::class.java, ArendClassField::class.java, ArendConstructor::class.java))

        private val DATA_OR_EXPRESSION_CONTEXT = or(DATA_CONTEXT, EXPRESSION_CONTEXT, TELE_CONTEXT, FIRST_TYPE_TELE_CONTEXT)

        private val ARGUMENT_EXPRESSION = or(withAncestors(*(ATOM_FIELDS_ACC_PREFIX + arrayOf(ArendAtomArgument::class.java))),
                withAncestors(PsiErrorElement::class.java, ArendAtomFieldsAcc::class.java, ArendArgumentAppExpr::class.java),
                withAncestors(*(NEW_EXPR_PREFIX + listOf(ArendTupleExpr::class.java, ArendImplicitArgument::class.java, ArendArgumentAppExpr::class.java))))

        private val ATOM_LEVEL_CONTEXT =
               or(withAncestors(*(ATOM_LEVEL_PREFIX + arrayOf(ArendLevelExpr::class.java))),
                  withAncestors(*(ATOM_LEVEL_PREFIX + arrayOf(ArendMaybeAtomLevelExpr::class.java, ArendExpr::class.java))),
                  withAncestors(*(ATOM_LEVEL_PREFIX + arrayOf(ArendMaybeAtomLevelExpr::class.java, ArendMaybeAtomLevelExprs::class.java, ArendLevelsExpr::class.java))),
                  withAncestors(*(ATOM_LEVEL_PREFIX + arrayOf(ArendMaybeAtomLevelExprs::class.java, ArendLevelsExpr::class.java))))

        private val LPH_CONTEXT = and(withParent(PsiErrorElement::class.java), withGrandParents(ArendSetUniverseAppExpr::class.java, ArendUniverseAppExpr::class.java, ArendTruncatedUniverseAppExpr::class.java))

        private val LPH_LEVEL_CONTEXT = and(withAncestors(PsiErrorElement::class.java, ArendAtomLevelExpr::class.java))

        private val ELIM_CONTEXT = and(
                not(afterLeaves(DATA_KW, FUNC_KW, SFUNC_KW, LEMMA_KW, TYPE_KW, CONS_KW, COERCE_KW, TRUNCATED_KW, COLON)),
                or(EXPRESSION_CONTEXT, TELE_CONTEXT, ARGUMENT_EXPRESSION,
                        withAncestors(ArendDefIdentifier::class.java, ArendIdentifierOrUnknown::class.java, ArendNameTele::class.java/*, ArendDefFunction::class.java*/),
                        withAncestors(PsiErrorElement::class.java, ArendNameTele::class.java, ArendDefFunction::class.java),
                        withAncestors(PsiErrorElement::class.java, ArendDefData::class.java)))

        private val ARGUMENT_EXPRESSION_IN_BRACKETS = withAncestors(*(NEW_EXPR_PREFIX +
                arrayOf<Class<out PsiElement>>(ArendTupleExpr::class.java, ArendTuple::class.java, ArendAtom::class.java, ArendAtomFieldsAcc::class.java, ArendAtomArgument::class.java, ArendArgumentAppExpr::class.java)))

        private val CLASS_CONTEXT = or(withAncestors(PsiErrorElement::class.java, ArendDefClass::class.java),
            withAncestors(ArendClassStat::class.java, ArendDefClass::class.java),
            withAncestors(ArendDefClass::class.java),
            withAncestors(ArendDefIdentifier::class.java, ArendClassField::class.java, ArendDefClass::class.java),
            withAncestors(PsiErrorElement::class.java, ArendClassStat::class.java, ArendDefClass::class.java),
            withAncestors(ArendDefIdentifier::class.java, ArendClassField::class.java,  ArendClassStat::class.java, ArendDefClass::class.java),
            withAncestors(ArendRefIdentifier::class.java, ArendLongName::class.java, ArendClassImplement::class.java, ArendDefClass::class.java),
            withAncestors(ArendRefIdentifier::class.java, ArendLongName::class.java, ArendClassImplement::class.java, ArendClassStat::class.java, ArendDefClass::class.java))

        private val CLASSFIELD_CONTEXT =
            or(withAncestors(ArendDefIdentifier::class.java, ArendFieldDefIdentifier::class.java, ArendFieldTele::class.java, ArendDefClass::class.java),
                withAncestors(PsiErrorElement::class.java, ArendFieldTele::class.java, ArendDefClass::class.java))

        private val CLASSIFYING_CONTEXT =
            or(
                and(afterLeaves(LPAREN, PRIVATE_KW, PROTECTED_KW), CLASSFIELD_CONTEXT),
                and(afterLeaves(PIPE, FIELD_KW, PROPERTY_KW, PRIVATE_KW, PROTECTED_KW), CLASS_CONTEXT))

        private val NO_CLASSIFYING_CONTEXT = and(afterLeaf(ID),
                or(withAncestors(ArendFieldTele::class.java, ArendDefClass::class.java),
                    withAncestors(PsiErrorElement::class.java, ArendFieldTele::class.java, ArendDefClass::class.java)))

        private val LEVEL_CONTEXT_0 = withAncestors(*(NEW_EXPR_PREFIX + arrayOf(ArendReturnExpr::class.java)))

        private val LEVEL_CONTEXT = or(
                and(afterLeaf(COLON), or(LEVEL_CONTEXT_0, withAncestors(PsiErrorElement::class.java, ArendDefFunction::class.java),
                        withAncestors(ArendClassStat::class.java, ArendDefClass::class.java), withParent(ArendDefClass::class.java))),
                and(afterLeaf(RETURN_KW), or(LEVEL_CONTEXT_0, withAncestors(PsiErrorElement::class.java, ArendCaseExpr::class.java))),
                withAncestors(PsiErrorElement::class.java, ArendAtomFieldsAcc::class.java, ArendArgumentAppExpr::class.java, ArendNewExpr::class.java, ArendReturnExpr::class.java),
                withAncestors(ArendRefIdentifier::class.java, ArendLongName::class.java, ArendLiteral::class.java, ArendAtom::class.java,
                        ArendAtomFieldsAcc::class.java, ArendAtomArgument::class.java, ArendArgumentAppExpr::class.java, ArendNewExpr::class.java, ArendReturnExpr::class.java))

        private val bareSigmaOrPiPattern = elementPattern { o ->
            var result: PsiElement? = o

            val context = ofType(PI_KW, SIGMA_KW)

            var tele: ArendCompositeElement? = null
            while (result != null) {
                if (result is ArendTypeTele) tele = result
                if (result is ArendExpr && result !is ArendUniverseAtom) break
                result = result.parent
            }

            if (context.accepts(o)) true else
                if (tele == null || tele.text.startsWith("(")) false else //Not Bare \Sigma or \Pi -- should display all expression keywords in completion
                    result is ArendSigmaExpr || result is ArendPiExpr
        }

        private val allowedInReturnPattern = elementPattern { o ->
            var result: PsiElement? = o
            while (result != null) {
                if (result is ArendReturnExpr) break
                result = result.parent
            }
            val resultC = result
            val resultParent = resultC?.parent
            when (resultC) {
                is ArendReturnExpr -> when {
                    resultC.levelKw != null -> false
                    resultParent is ArendDefInstance -> false
                    resultParent is ArendDefFunction -> !resultParent.isCowith
                    else -> true
                }
                else -> true
            }
        }

        private val expressionPattern = { allowInBareSigmaOrPiExpressions: Boolean, allowInArgumentExpressionContext: Boolean ->
            not(or(after(withAncestors(ArendAtomFieldsAcc::class.java)), //No keyword completion after field
                and(or(withAncestors(*RETURN_EXPR_PREFIX), withAncestors(*(NEW_EXPR_PREFIX + arrayOf(ArendReturnExpr::class.java)))), not(allowedInReturnPattern)),
                after(and(ofType(RBRACE), withParent(ArendWithBody::class.java))), //No keyword completion after \with or } in case expr
                after(ofType(LAM_KW, HAVE_KW, LET_KW, HAVES_KW, LETS_KW, WITH_KW)), //No keyword completion after \lam or \let
                after(ofType(SET, PROP_KW, UNIVERSE, TRUNCATED_UNIVERSE, NEW_KW, EVAL_KW, PEVAL_KW)), //No expression keyword completion after universe literals or \new keyword
                or(LPH_CONTEXT, LPH_LEVEL_CONTEXT), //No expression keywords when completing levels in universes
                after(and(ofType(ID), withAncestors(ArendRefIdentifier::class.java, ArendElim::class.java))), //No expression keywords in \elim expression
                if (allowInBareSigmaOrPiExpressions) PlatformPatterns.alwaysFalse() else after(bareSigmaOrPiPattern), //Only universe expressions allowed inside Sigma or Pi expressions
                if (allowInArgumentExpressionContext) PlatformPatterns.alwaysFalse() else
                    or(ARGUMENT_EXPRESSION, withAncestors(PsiErrorElement::class.java, ArendAtomFieldsAcc::class.java, ArendAtomArgument::class.java, ArendArgumentAppExpr::class.java))))
        }

        val BASIC_EXPRESSION_KW_PATTERN = and(or(EXPRESSION_CONTEXT), expressionPattern.invoke(false, false))

        // Contribution to LookupElementBuilder
        fun LookupElementBuilder.withPriority(priority: Double): LookupElement = PrioritizedLookupElement.withPriority(this, priority)

        fun jointOfStatementsCondition(arendCompletionParameters: ArendCompletionParameters,
                                       additionalCondition: (ArendCompletionParameters) -> Boolean = { true },
                                       noCrlfRequired: Boolean = false,
                                       allowInsideBraces: Boolean = true,
                                       allowBeforeClassFields: Boolean = false): Boolean {
            val leftSideOk = (arendCompletionParameters.leftStatement == null || arendCompletionParameters.leftBrace && allowInsideBraces)
            val rightSideOk = (arendCompletionParameters.rightStatement == null || arendCompletionParameters.rightBrace && !arendCompletionParameters.leftBrace)
            val correctStatements = rightSideOk || (leftSideOk || arendCompletionParameters.betweenStatementsOk) && (allowBeforeClassFields || !arendCompletionParameters.isBeforeClassFields)

            return (arendCompletionParameters.delimiterBeforeCaret || noCrlfRequired) && additionalCondition.invoke(arendCompletionParameters) && correctStatements
        }
    }

    @Suppress("unused")
    private class Logger : CompletionProvider<CompletionParameters>() {
        override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
            val text = parameters.position.containingFile.text

            val mn = max(0, parameters.position.node.startOffset - 15)
            val mx = min(text.length, parameters.position.node.startOffset + parameters.position.node.textLength + 15)
            println("")
            println("surround text: ${text.substring(mn, mx).replace("\n", "\\n")}")
            println("kind: " + parameters.position.javaClass + " text: " + parameters.position.text)
            var i = 0
            var pp: PsiElement? = parameters.position
            while (i < 13 && pp != null) {
                System.out.format("kind.parent(%2d): %-40s text: %-50s\n", i, pp.javaClass.simpleName, pp.text)
                pp = pp.parent
                i++
            }
            println("originalPosition.parent: " + parameters.originalPosition?.parent?.javaClass)
            println("originalPosition.grandparent: " + parameters.originalPosition?.parent?.parent?.javaClass)
            val jointData = ArendCompletionParameters(parameters)
            println("prevElement: ${jointData.prevElement} text: ${jointData.prevElement?.text}")
            println("prevElement.parent: ${jointData.prevElement?.parent?.javaClass}")
            println("prevElement.grandparent: ${jointData.prevElement?.parent?.parent?.javaClass}")
            println("nextElement: ${jointData.nextElement} text: ${jointData.nextElement?.text}")
            println("nextElement.parent: ${jointData.nextElement?.parent?.javaClass}")
            if (parameters.position.parent is PsiErrorElement) println("errorDescription: " + (parameters.position.parent as PsiErrorElement).errorDescription)
            println("")
            System.out.flush()
        }
    }

    private enum class KeywordCompletionBehavior {DEFAULT, ADD_WHITESPACE, ADD_BRACES}

    private open class KeywordCompletionProvider(private val keywords: Collection<String>,
                                                 private val completionBehavior: KeywordCompletionBehavior = KeywordCompletionBehavior.DEFAULT,
                                                 private val disableAfter2Crlfs: Boolean = true) : CompletionProvider<CompletionParameters>() {

        open fun insertHandler(keyword: String): InsertHandler<LookupElement> = InsertHandler { insertContext, _ ->
            val document = insertContext.document
            if (completionBehavior == KeywordCompletionBehavior.ADD_WHITESPACE ) {
                document.insertString(insertContext.tailOffset, " ")
            }
            insertContext.commitDocument()
            when (completionBehavior) {
                KeywordCompletionBehavior.ADD_BRACES -> insertContext.editor.caretModel.moveToOffset(insertContext.tailOffset - 1)
                else -> insertContext.editor.caretModel.moveToOffset(insertContext.tailOffset)
            }
        }

        open fun lookupElement(keyword: String): LookupElementBuilder {
            val element = LookupElementBuilder.create(if (completionBehavior == KeywordCompletionBehavior.ADD_BRACES) "$keyword {}" else keyword)
            return if (completionBehavior == KeywordCompletionBehavior.ADD_BRACES) element.withPresentableText(keyword) else element
        }

        open fun computePrefix(parameters: CompletionParameters, resultSet: CompletionResultSet): String {
            var prefix = resultSet.prefixMatcher.prefix
            val lastInvalidIndex = prefix.mapIndexedNotNull { i, c -> if (!ArendWordScanner.isArendIdentifierPart(c)) i else null }.lastOrNull()
            if (lastInvalidIndex != null) prefix = prefix.substring(lastInvalidIndex + 1, prefix.length)
            val pos = parameters.offset - prefix.length - 1
            if (pos >= 0 && pos < parameters.originalFile.textLength)
                prefix = (if (parameters.originalFile.text[pos] == '\\') "\\" else "") + prefix
            return prefix
        }

        override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, resultSet: CompletionResultSet) {
            if (parameters.position is PsiComment || afterLeaf(DOT).accepts(parameters.position) ||
                    disableAfter2Crlfs && ArendCompletionParameters.findPrevAnchor(parameters.offset, parameters.originalFile).first > 1) // Prevents showing kw completions in comments and after dot expression
                return

            val prefix = computePrefix(parameters, resultSet)

            val prefixMatcher = object : PlainPrefixMatcher(prefix) {
                override fun prefixMatches(name: String): Boolean = isStartMatch(name)
            }

            for (keyword in keywords)
                resultSet.withPrefixMatcher(prefixMatcher).addElement(lookupElement(keyword).withInsertHandler(insertHandler(keyword)).withPriority(KEYWORD_PRIORITY))
        }
    }

    private open class ConditionalProvider(keywords: Collection<String>, val condition: (CompletionParameters) -> Boolean,
                                           completionBehavior: KeywordCompletionBehavior = KeywordCompletionBehavior.ADD_WHITESPACE,
                                           disableAfter2Crlfs: Boolean = true) :
            KeywordCompletionProvider(keywords, completionBehavior, disableAfter2Crlfs) {
        override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, resultSet: CompletionResultSet) {
            if (condition.invoke(parameters)) {
                super.addCompletions(parameters, context, resultSet)
            }
        }
    }

    private open class JointOfStatementsProvider(keywords: List<String>,
                                                 additionalCondition: (ArendCompletionParameters) -> Boolean = { true },
                                                 completionBehavior: KeywordCompletionBehavior = KeywordCompletionBehavior.ADD_WHITESPACE,
                                                 noCrlfRequired: Boolean = false,
                                                 allowInsideBraces: Boolean = true,
                                                 allowBeforeClassFields: Boolean = false) :
            ConditionalProvider(keywords, { parameters -> parameters.originalFile !is PsiCodeFragment && jointOfStatementsCondition(ArendCompletionParameters(parameters), additionalCondition, noCrlfRequired, allowInsideBraces, allowBeforeClassFields)}, completionBehavior, disableAfter2Crlfs = false)

    class ArendCompletionParameters(val position: PsiElement?, caretOffset: Int, file: PsiFile) {
        val prevElement: PsiElement?
        val delimiterBeforeCaret: Boolean
        val nextElement: PsiElement?
        val ancestors: List<PsiElement>
        val ancestorsNE: List<PsiElement>
        val ancestorsPE: List<PsiElement>
        val leftBrace: Boolean
        val rightBrace: Boolean
        val leftStatement: PsiElement?
        val rightStatement: PsiElement?
        val isBeforeClassFields: Boolean
        val betweenStatementsOk: Boolean

        constructor(completionParameters: CompletionParameters) : this(completionParameters.position, completionParameters.offset, completionParameters.originalFile)
        init {
            var ofs = 0
            var next: PsiElement?

            do {
                val pos = caretOffset + (ofs++)
                next = if (pos > file.textLength) null else file.findElementAt(pos)
            } while (next is PsiWhiteSpace || next is PsiComment)

            val p = findPrevAnchor(caretOffset, file)
            val prev = p.second

            delimiterBeforeCaret = p.first > 0
            nextElement = next
            prevElement = prev

            val statementCondition = { psi: PsiElement ->
                if (psi is ArendStat) {
                    val psiParent = psi.parent
                    !(psiParent is ArendWhere && psiParent.lbrace == null)
                } else psi is ArendClassStat
            }

            ancestorsNE = ancestorsUntil(statementCondition, next)
            ancestorsPE = ancestorsUntil(statementCondition, prev)
            ancestors = ancestorsUntil(statementCondition, position)

            leftBrace = prev?.node?.elementType == LBRACE && parentIsStatementHolder(prev)
            rightBrace = nextElement?.node?.elementType == RBRACE && parentIsStatementHolder(nextElement)
            leftStatement = ancestorsPE.lastOrNull()
            rightStatement = ancestorsNE.lastOrNull()
            isBeforeClassFields = rightStatement is ArendClassStat && rightStatement.definition == null
            betweenStatementsOk = leftStatement != null && rightStatement != null && ancestorsNE.intersect(ancestorsPE.toSet()).isEmpty()
        }

        companion object {

            fun textBeforeCaret(whiteSpace: PsiWhiteSpace, caretOffset: Int): String = when {
                whiteSpace.textRange.contains(caretOffset) -> whiteSpace.text.substring(0, caretOffset - whiteSpace.textRange.startOffset)
                caretOffset < whiteSpace.textRange.startOffset -> ""
                else -> whiteSpace.text
            }

            fun parentIsStatementHolder(p: PsiElement?) = when (p?.parent) {
                is ArendWhere -> true
                is ArendDefClass -> true
                else -> false
            }

            fun ancestorsUntil(condition: (PsiElement) -> Boolean, element: PsiElement?): List<PsiElement> {
                val ancestors = ArrayList<PsiElement>()
                var elem: PsiElement? = element
                if (elem != null) ancestors.add(elem)
                while (elem != null && !condition.invoke(elem)) {
                    elem = elem.parent
                    if (elem != null) ancestors.add(elem)
                }
                return ancestors
            }

            fun findPrevAnchor(caretOffset: Int, file: PsiFile) : Pair<Int, PsiElement?> {
                var pos = caretOffset - 1
                var prev: PsiElement? = file.findElementAt(caretOffset)
                if (prev != null && STATEMENT_WT_KWS.contains(prev.text)) pos = prev.startOffset - 1

                var skippedFirstErrorExpr: PsiElement? = null
                var numberOfCrlfs = 0

                do {
                    prev = if (pos <= 0) {
                        numberOfCrlfs = 1
                        break
                    } else file.findElementAt(pos)

                    numberOfCrlfs += if (prev is PsiWhiteSpace) textBeforeCaret(prev, caretOffset).count { it == '\n' } else 0

                    var skipFirstErrorExpr = (prev?.node?.elementType == BAD_CHARACTER || (prev?.node?.elementType == INVALID_KW && prev?.parent is PsiErrorElement && prev.text.startsWith("\\")))
                    if (skipFirstErrorExpr && skippedFirstErrorExpr != null && skippedFirstErrorExpr != prev) skipFirstErrorExpr = false else skippedFirstErrorExpr = prev

                    pos = (prev?.textRange?.startOffset ?: pos) - 1
                } while (prev is PsiWhiteSpace || prev is PsiComment || skipFirstErrorExpr)

                return Pair(numberOfCrlfs, prev)
            }
        }
    }
}
