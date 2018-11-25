package org.arend.codeInsight.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.completion.CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PlatformPatterns
import com.intellij.patterns.PsiElementPattern
import com.intellij.patterns.StandardPatterns.*
import com.intellij.psi.*
import com.intellij.psi.TokenType.BAD_CHARACTER
import com.intellij.psi.tree.IElementType
import com.intellij.util.ProcessingContext
import com.intellij.util.containers.isNullOrEmpty
import org.arend.psi.*
import org.arend.psi.ArendElementTypes.*
import org.arend.psi.ext.impl.DefinitionAdapter
import org.arend.search.ArendWordScanner
import org.arend.term.abs.Abstract
import java.util.*

class ArendCompletionContributor : CompletionContributor() {

    init {
        extend(CompletionType.BASIC, PREC_CONTEXT, KeywordCompletionProvider(FIXITY_KWS))

        extend(CompletionType.BASIC, afterLeaf(FAT_ARROW), originalPositionCondition(withParentOrGrandParent(ArendClassFieldSyn::class.java),
                KeywordCompletionProvider(FIXITY_KWS))) // fixity kws for class field synonym (2nd part)
        extend(CompletionType.BASIC, AS_CONTEXT, ProviderWithCondition({ parameters, _ -> (parameters.position.parent.parent as ArendNsId).asKw == null },
                KeywordCompletionProvider(AS_KW_LIST)))

        extend(CompletionType.BASIC, NS_CMD_CONTEXT, originalPositionCondition(withParent(ArendFile::class.java),
                KeywordCompletionProvider(HU_KW_LIST)))
        extend(CompletionType.BASIC, NS_CMD_CONTEXT, ProviderWithCondition({ parameters, _ -> noUsing(parameters.position.parent.parent as ArendStatCmd) },
                KeywordCompletionProvider(USING_KW_LIST)))
        extend(CompletionType.BASIC, NS_CMD_CONTEXT, ProviderWithCondition({ parameters, _ -> noUsingAndHiding(parameters.position.parent.parent as ArendStatCmd) },
                KeywordCompletionProvider(HIDING_KW_LIST)))
        extend(CompletionType.BASIC, withAncestors(PsiErrorElement::class.java, ArendNsUsing::class.java, ArendStatCmd::class.java),
                ProviderWithCondition({ parameters, _ -> noHiding(parameters.position.parent.parent.parent as ArendStatCmd) },
                        KeywordCompletionProvider(HIDING_KW_LIST)))

        val statementCondition = { psi: PsiElement -> psi is ArendStatement || psi is ArendClassStat }

        extend(CompletionType.BASIC, STATEMENT_END_CONTEXT,
                onJointOfStatementsCondition(statementCondition,
                        KeywordCompletionProvider(STATEMENT_WT_KWS)))

        extend(CompletionType.BASIC, STATEMENT_END_CONTEXT,
                onJointOfStatementsCondition(statementCondition,
                        object : KeywordCompletionProvider(TRUNCATED_KW_LIST) {
                            override fun insertHandler(keyword: String): InsertHandler<LookupElement> = InsertHandler { insertContext, _ ->
                                val document = insertContext.document
                                document.insertString(insertContext.tailOffset, " \\data ")
                                insertContext.commitDocument()
                                insertContext.editor.caretModel.moveToOffset(insertContext.tailOffset)
                            }

                            override fun lookupElement(keyword: String): LookupElementBuilder =
                                    LookupElementBuilder.create(keyword).withPresentableText("\\truncated \\data")
                        }))

        extend(CompletionType.BASIC, STATEMENT_END_CONTEXT, onJointOfStatementsCondition(statementCondition,
                ProviderWithCondition({ parameters, _ ->
                    val pp = parameters.position
                    val ppp = pp.parent
                    pp.ancestors.filter { it is ArendWhere || it is ArendDefClass }.none() ||
                            (ppp is PsiErrorElement && ppp.parent is ArendDefClass && ppp.nextSibling == null)
                },
                        KeywordCompletionProvider(IMPORT_KW_LIST))))

        val classOrDataPositionMatcher = { position : PsiElement, insideWhere : Boolean, dataAllowed : Boolean ->
            var foundWhere = false
            var ancestor: PsiElement? = position
            var result2 = false
            while (ancestor != null) {
                if (ancestor is ArendWhere) foundWhere = true
                if ((dataAllowed && ancestor is ArendDefData) || ancestor is ArendDefClass) {
                    result2 = !(insideWhere xor foundWhere)
                    break
                } else if (ancestor is ArendDefinition && foundWhere) {
                    result2 = false
                    break
                }
                ancestor = ancestor.parent
            }
            result2
        }

        extend(CompletionType.BASIC, STATEMENT_END_CONTEXT, onJointOfStatementsCondition(statementCondition,
                ProviderWithCondition({ parameters, _ -> classOrDataPositionMatcher(parameters.position, false, false) },
                        KeywordCompletionProvider(CLASS_MEMBER_KWS))))

        extend(CompletionType.BASIC, STATEMENT_END_CONTEXT, onJointOfStatementsCondition(statementCondition,
                ProviderWithCondition({ parameters, _ -> classOrDataPositionMatcher(parameters.position, true, true) },
                        KeywordCompletionProvider(USE_KW_LIST))))
        extend(CompletionType.BASIC, afterLeaf(USE_KW), KeywordCompletionProvider(COERCE_LEVEL_KWS))

        extend(CompletionType.BASIC, and(DATA_CONTEXT, afterLeaf(TRUNCATED_KW)), KeywordCompletionProvider(DATA_KW_LIST))//data after \truncated keyword

        extend(CompletionType.BASIC, WHERE_CONTEXT, onJointOfStatementsCondition(statementCondition, KeywordCompletionProvider(WHERE_KW_LIST), true, false) { jD: JointData ->
            var anc = jD.prevElement
            while (anc != null && anc !is ArendDefinition && anc !is ArendDefModule && anc !is ArendClassStat) anc = anc.parent
            if (anc != null) {
                val da: ArendDefinition? = anc as? ArendDefinition /* TODO: create WhereHolder concept */
                val dm: ArendDefModule? = anc as? ArendDefModule
                (when {
                    da is DefinitionAdapter<*> -> da.getWhere() == null
                    dm != null -> dm.where == null
                    else -> false
                })
            } else false
        })

        val noExtendsCondition = genericJointCondition({cP, _, jD ->
            val condition = and(ofType(ID), withParent(ArendRefIdentifier::class.java))
            val dC = cP.position.ancestors.filterIsInstance<ArendDefClass>().firstOrNull()
            if (dC != null) dC.extendsKw == null && (!condition.accepts(jD.prevElement)) else false
        }, KeywordCompletionProvider(EXTENDS_KW_LIST))

        extend(CompletionType.BASIC, and(withAncestors(PsiErrorElement::class.java, ArendDefClass::class.java), afterLeaf(ID)), noExtendsCondition)
        extend(CompletionType.BASIC, withAncestors(PsiErrorElement::class.java, ArendFieldTele::class.java, ArendDefClass::class.java),
                ProviderWithCondition({ parameters, _ ->
                    var nS = parameters.position.parent?.parent?.nextSibling
                    while (nS is PsiWhiteSpace) nS = nS.nextSibling
                    nS !is ArendFieldTele
                }, noExtendsCondition))

        extend(CompletionType.BASIC, and(DATA_CONTEXT, afterLeaf(COLON)), KeywordCompletionProvider(DATA_UNIVERSE_KW))

        val bareSigmaOrPi = { expression: PsiElement ->
            var result: PsiElement? = expression

            val context = ofType(PI_KW, SIGMA_KW)

            var tele: ArendTypeTele? = null
            while (result != null) {
                if (result is ArendTypeTele) tele = result
                if (result is ArendExpr && result !is ArendUniverseAtom) break
                result = result.parent
            }

            if (context.accepts(expression)) true else
                if (tele?.text == null || tele.text.startsWith("(")) false else //Not Bare \Sigma or \Pi -- should display all expression keywords in completion
                    result is ArendSigmaExpr || result is ArendPiExpr
        }
        val noExpressionKwsAfter = ofType(SET, PROP_KW, UNIVERSE, TRUNCATED_UNIVERSE, NEW_KW)
        val afterElimVar = and(ofType(ID), withAncestors(ArendRefIdentifier::class.java, ArendElim::class.java))

        val expressionFilter = { basicCompletionProvider: CompletionProvider<CompletionParameters>, allowInBareSigmaOrPiExpressions: Boolean, allowInArgumentExpressionContext: Boolean ->
            genericJointCondition({ cP, _, jD ->

                !FIELD_CONTEXT.accepts(jD.prevElement) && //No keyword completion after field
                        !INSTANCE_CONTEXT.accepts(cP.position) && //No keyword completion in instance type
                        !(ofType(RBRACE).accepts(jD.prevElement) && withParent(ArendCaseExpr::class.java).accepts(jD.prevElement)) && //No keyword completion after \with or } in case expr
                        !(ofType(LAM_KW, LET_KW, WITH_KW).accepts(jD.prevElement)) && //No keyword completion after \lam or \let
                        !(noExpressionKwsAfter.accepts(jD.prevElement)) && //No expression keyword completion after universe literals or \new keyword
                        !(or(LPH_CONTEXT, LPH_LEVEL_CONTEXT).accepts(cP.position)) && //No expression keywords when completing levels in universes
                        !(afterElimVar.accepts(jD.prevElement)) && //No expression keywords in \elim expression
                        (allowInBareSigmaOrPiExpressions || jD.prevElement == null || !bareSigmaOrPi(jD.prevElement)) &&  //Only universe expressions allowed inside Sigma or Pi expressions
                        (allowInArgumentExpressionContext || !ARGUMENT_EXPRESSION.accepts(cP.position)) // New expressions & universe expressions are allowed in applications
            }, basicCompletionProvider)
        }

        extend(CompletionType.BASIC, EXPRESSION_CONTEXT, expressionFilter.invoke(KeywordCompletionProvider(DATA_UNIVERSE_KW), true, true))
        extend(CompletionType.BASIC, or(TELE_CONTEXT, FIRST_TYPE_TELE_CONTEXT), KeywordCompletionProvider(DATA_UNIVERSE_KW))

        extend(CompletionType.BASIC, EXPRESSION_CONTEXT, expressionFilter.invoke(KeywordCompletionProvider(BASIC_EXPRESSION_KW), false, false))
        extend(CompletionType.BASIC, EXPRESSION_CONTEXT, expressionFilter.invoke(KeywordCompletionProvider(NEW_KW_LIST), false, true))

        val truncatedTypeCompletionProvider = object : KeywordCompletionProvider(FAKE_NTYPE_LIST) {
            override fun insertHandler(keyword: String): InsertHandler<LookupElement> = InsertHandler { insertContext, _ ->
                val document = insertContext.document
                document.insertString(insertContext.tailOffset, " ") // add tail whitespace
                insertContext.commitDocument()
                insertContext.editor.caretModel.moveToOffset(insertContext.startOffset + 1)
                document.replaceString(insertContext.startOffset + 1, insertContext.startOffset + 2, "1") //replace letter n by 1 so that the keyword would be highlighted correctly
                insertContext.editor.selectionModel.setSelection(insertContext.startOffset + 1, insertContext.startOffset + 2)
            }
        }

        extend(CompletionType.BASIC, and(DATA_CONTEXT, afterLeaf(COLON)), truncatedTypeCompletionProvider)
        extend(CompletionType.BASIC, EXPRESSION_CONTEXT, expressionFilter.invoke(truncatedTypeCompletionProvider, true, true))
        extend(CompletionType.BASIC, or(TELE_CONTEXT, FIRST_TYPE_TELE_CONTEXT), truncatedTypeCompletionProvider)

        fun isAfterNumber(element: PsiElement?): Boolean = element?.prevSibling?.text == "\\" && element.node?.elementType == NUMBER

        extend(CompletionType.BASIC, DATA_OR_EXPRESSION_CONTEXT, genericJointCondition({ _, _, jD -> isAfterNumber(jD.prevElement) }, object : KeywordCompletionProvider(listOf("-Type")) {
            override fun computePrefix(parameters: CompletionParameters, resultSet: CompletionResultSet): String = ""
        }))

        extend(CompletionType.BASIC, DATA_OR_EXPRESSION_CONTEXT, ProviderWithCondition({ cP, _ ->
            cP.originalPosition?.text?.matches(Regex("\\\\[0-9]+(-(T(y(pe?)?)?)?)?")) ?: false
        }, object : KeywordCompletionProvider(listOf("Type")) {
            override fun computePrefix(parameters: CompletionParameters, resultSet: CompletionResultSet): String =
                    super.computePrefix(parameters, resultSet).replace(Regex("\\\\[0-9]+-?"), "")
        }))

        extend(CompletionType.BASIC, LPH_CONTEXT, ProviderWithCondition({ cP, _ ->
            val pp = cP.position.parent.parent
            when (pp) {
                is ArendSetUniverseAppExpr, is ArendTruncatedUniverseAppExpr ->
                    pp.children.filterIsInstance<ArendAtomLevelExpr>().isEmpty()
                else -> pp.children.filterIsInstance<ArendAtomLevelExpr>().size <= 1
            }
        }, KeywordCompletionProvider(LPH_KW_LIST)))

        extend(CompletionType.BASIC, withParent(ArendLevelExpr::class.java), ProviderWithCondition({ cP, _ ->
            when (cP.position.parent?.firstChild?.node?.elementType) {
                MAX_KW, SUC_KW -> true
                else -> false
            }
        }, KeywordCompletionProvider(LPH_KW_LIST)))

        extend(CompletionType.BASIC, LPH_LEVEL_CONTEXT, KeywordCompletionProvider(LPH_LEVEL_KWS))

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

        val pairingInCondition = { pos: PsiElement -> pairingWordCondition({ position: PsiElement? -> position is ArendLetExpr && position.inKw == null }, pos) }
        val pairingWithCondition = { pos: PsiElement -> pairingWordCondition({ position: PsiElement? -> position is ArendCaseExpr && position.withKw == null }, pos) }

        fun pairingWordProvider(condition: (PsiElement) -> Boolean, cp: CompletionProvider<CompletionParameters>) = ProviderWithCondition({ cP, _ -> condition(cP.position) }, cp)

        extend(CompletionType.BASIC, and(EXPRESSION_CONTEXT, not(or(afterLeaf(IN_KW), afterLeaf(LET_KW)))), pairingWordProvider(pairingInCondition, KeywordCompletionProvider(IN_KW_LIST)))

        val caseContext = and(CASE_CONTEXT, not(or(afterLeaf(WITH_KW), afterLeaf(CASE_KW), afterLeaf(COLON))))

        extend(CompletionType.BASIC, caseContext, pairingWordProvider(pairingWithCondition, KeywordCompletionProvider(WITH_KW_LIST, false)))

        val asCondition1 = { position: PsiElement? -> position is ArendCaseArg && position.asKw == null }
        val asCondition2 = { position: PsiElement? ->
            //Alternative condition needed to ensure that as is added before semicolon
            var p = position
            if (p != null && p.nextSibling is PsiWhiteSpace) p = p.nextSibling.nextSibling
            p != null && p.node.elementType == COLON
        }
        val returnCondition = { pos: PsiElement ->
            pairingWordCondition({ position: PsiElement? ->
                if (position is ArendCaseArg) {
                    val pp = position.parent as? ArendCaseExpr
                    pp != null && pp.caseArgList.lastOrNull() == position && pp.returnKw == null
                } else false
            }, pos)
        }

        val argEndCondition = { pos: PsiElement ->
            pairingWordCondition({ position: PsiElement? ->
                asCondition1.invoke(position) || (position != null && asCondition2.invoke(position) && asCondition1.invoke(position.parent))
            }, pos)
        }

        extend(CompletionType.BASIC, caseContext, pairingWordProvider(argEndCondition, KeywordCompletionProvider(AS_KW_LIST)))
        extend(CompletionType.BASIC, caseContext, pairingWordProvider(returnCondition, KeywordCompletionProvider(RETURN_KW_LIST)))

        val emptyTeleList = { l: List<Abstract.Parameter> ->
            l.isEmpty() || l.size == 1 && (l[0].type == null || (l[0].type as PsiElement).text == DUMMY_IDENTIFIER_TRIMMED) &&
                    (l[0].referableList.size == 0 || l[0].referableList[0] == null || (l[0].referableList[0] as PsiElement).text == DUMMY_IDENTIFIER_TRIMMED)
        }
        val elimOrCoWithCondition = { coWithMode: Boolean ->
            { cP: CompletionParameters, _: ProcessingContext? ->
                var pos2: PsiElement? = cP.position
                var exprFound = false
                while (pos2 != null) {
                    if (pos2.nextSibling is PsiWhiteSpace) {
                        var body = pos2.nextSibling
                        while (body is PsiWhiteSpace || body is PsiComment) body = body.nextSibling
                        if (body is ArendFunctionBody || body is ArendDataBody) pos2 = body.parent
                    }

                    if ((pos2 is ArendDefFunction)) {
                        val fBody = pos2.functionBody
                        exprFound = fBody == null || fBody.fatArrow == null && fBody.elim?.elimKw == null
                        exprFound = exprFound &&
                                if (!coWithMode) !emptyTeleList(pos2.nameTeleList)  // No point of writing elim keyword if there are no arguments
                                else pos2.expr != null // No point of writing cowith keyword if there is no result type
                        break
                    }
                    if ((pos2 is ArendDefData) && !coWithMode) {
                        val dBody = pos2.dataBody
                        exprFound = dBody == null || (dBody.elim?.elimKw == null && dBody.constructorList.isNullOrEmpty() && dBody.constructorClauseList.isNullOrEmpty())
                        exprFound = exprFound && !emptyTeleList(pos2.typeTeleList)
                        break
                    }

                    if (pos2 is ArendConstructor && pos2.elim == null && !coWithMode) {
                        exprFound = !emptyTeleList(pos2.typeTeleList)
                        break
                    }

                    if (pos2?.nextSibling == null) pos2 = pos2?.parent else break
                }
                exprFound
            }
        }

        extend(CompletionType.BASIC, ELIM_CONTEXT, ProviderWithCondition(elimOrCoWithCondition.invoke(false), KeywordCompletionProvider(ELIM_KW_LIST)))
        extend(CompletionType.BASIC, ELIM_CONTEXT, ProviderWithCondition(elimOrCoWithCondition.invoke(false), KeywordCompletionProvider(WITH_KW_LIST, false)))
        extend(CompletionType.BASIC, ELIM_CONTEXT, ProviderWithCondition(elimOrCoWithCondition.invoke(true), KeywordCompletionProvider(COWITH_KW_LIST, false)))

        val isLiteralApp = { argumentAppExpr: ArendArgumentAppExpr ->
            argumentAppExpr.longNameExpr != null ||
                    ((argumentAppExpr.children[0] as? ArendAtomFieldsAcc)?.atom?.literal?.longName != null)
        }

        val unifiedLevelCondition = { atomIndex: Int?, forbidLevelExprs: Boolean, threshold: Int ->
            { cP: CompletionParameters, _: ProcessingContext? ->
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
                    var counter = argumentAppExpr.longNameExpr?.atomOnlyLevelExprList?.size ?: 0
                    var forbidden = false
                    val levelsExpr = argumentAppExpr.longNameExpr?.levelsExpr
                    if (levelsExpr != null) {
                        counter += levelsExpr.atomLevelExprList.size
                        if (forbidLevelExprs) forbidden = true
                    }
                    for (ch in argumentAppExpr.children) {
                        if (ch == anchor || ch == anchor.parent) break
                        if (ch is ArendAtomArgument) forbidden = true
                    }
                    counter < threshold && !forbidden
                } else argumentAppExpr?.longNameExpr?.levelsExpr?.levelKw != null && isLiteralApp(argumentAppExpr)
            }
        }

        extend(CompletionType.BASIC, ARGUMENT_EXPRESSION, ProviderWithCondition(unifiedLevelCondition.invoke(0, false, 2), KeywordCompletionProvider(LPH_KW_LIST)))

        extend(CompletionType.BASIC, ARGUMENT_EXPRESSION, ProviderWithCondition(unifiedLevelCondition.invoke(0, true, 1), KeywordCompletionProvider(LEVEL_KW_LIST)))

        extend(CompletionType.BASIC, ARGUMENT_EXPRESSION_IN_BRACKETS, ProviderWithCondition(unifiedLevelCondition.invoke(1, false, 2), KeywordCompletionProvider(LPH_LEVEL_KWS)))

        extend(CompletionType.BASIC, withAncestors(PsiErrorElement::class.java, ArendArgumentAppExpr::class.java),
                ProviderWithCondition(unifiedLevelCondition.invoke(null, false, 2), KeywordCompletionProvider(LPH_LEVEL_KWS)))

        extend(CompletionType.BASIC, withParent(ArendArgumentAppExpr::class.java), ProviderWithCondition({ cP, _ ->
            val argumentAppExpr: ArendArgumentAppExpr? = cP.position.parent as ArendArgumentAppExpr
            argumentAppExpr?.longNameExpr?.levelsExpr?.levelKw != null && isLiteralApp(argumentAppExpr)
        }, KeywordCompletionProvider(LPH_LEVEL_KWS)))


        //extend(CompletionType.BASIC, ANY, LoggerCompletionProvider())
    }

    companion object {
        val FIXITY_KWS = listOf(INFIX_LEFT_KW, INFIX_RIGHT_KW, INFIX_NON_KW, NON_ASSOC_KW, LEFT_ASSOC_KW, RIGHT_ASSOC_KW).map { it.toString() }
        val STATEMENT_WT_KWS = listOf(FUNCTION_KW, DATA_KW, CLASS_KW, RECORD_KW, INSTANCE_KW, OPEN_KW, MODULE_KW).map { it.toString() }
        val CLASS_MEMBER_KWS = listOf(FIELD_KW, PROPERTY_KW).map { it.toString() }
        val DATA_UNIVERSE_KW = listOf("\\Type", "\\Set", PROP_KW.toString(), "\\oo-Type")
        val BASIC_EXPRESSION_KW = listOf(PI_KW, SIGMA_KW, LAM_KW, LET_KW, CASE_KW).map { it.toString() }
        val LEVEL_KWS = listOf(MAX_KW, SUC_KW).map { it.toString() }
        val LPH_KW_LIST = listOf(LP_KW, LH_KW).map { it.toString() }
        val ELIM_WITH_KW_LIST = listOf(ELIM_KW, WITH_KW).map { it.toString() }
        val COERCE_LEVEL_KWS = listOf(COERCE_KW, LEVEL_KW).map{ it.toString() }

        val AS_KW_LIST = listOf(AS_KW.toString())
        val USING_KW_LIST = listOf(USING_KW.toString())
        val HIDING_KW_LIST = listOf(HIDING_KW.toString())
        val EXTENDS_KW_LIST = listOf(EXTENDS_KW.toString())
        val DATA_KW_LIST = listOf(DATA_KW.toString())
        val IMPORT_KW_LIST = listOf(IMPORT_KW.toString())
        val WHERE_KW_LIST = listOf(WHERE_KW.toString())
        val TRUNCATED_KW_LIST = listOf(TRUNCATED_KW.toString())
        val NEW_KW_LIST = listOf(NEW_KW.toString())
        val FAKE_NTYPE_LIST = listOf("\\n-Type")
        val IN_KW_LIST = listOf(IN_KW.toString())
        val WITH_KW_LIST = listOf(WITH_KW.toString())
        val LEVEL_KW_LIST = listOf(LEVEL_KW.toString())
        val PROP_KW_LIST = listOf(PROP_KW.toString())
        val USE_KW_LIST = listOf(USE_KW.toString())
        val RETURN_KW_LIST = listOf(RETURN_KW.toString())
        val COWITH_KW_LIST = listOf(COWITH_KW.toString())
        val ELIM_KW_LIST = listOf(ELIM_KW.toString())

        val LOCAL_STATEMENT_KWS = STATEMENT_WT_KWS + TRUNCATED_KW_LIST
        val GLOBAL_STATEMENT_KWS = STATEMENT_WT_KWS + TRUNCATED_KW_LIST + IMPORT_KW_LIST
        val CLASS_STATEMENT_KWS = LOCAL_STATEMENT_KWS + CLASS_MEMBER_KWS
        val ALL_STATEMENT_KWS = STATEMENT_WT_KWS + TRUNCATED_KW_LIST + IMPORT_KW_LIST + USE_KW_LIST + CLASS_MEMBER_KWS
        val HU_KW_LIST = USING_KW_LIST + HIDING_KW_LIST
        val DATA_OR_EXPRESSION_KW = DATA_UNIVERSE_KW + BASIC_EXPRESSION_KW + NEW_KW_LIST
        val LPH_LEVEL_KWS = LPH_KW_LIST + LEVEL_KWS

        const val KEYWORD_PRIORITY = 0.0

        private fun afterLeaf(et: IElementType) = PlatformPatterns.psiElement().afterLeaf(PlatformPatterns.psiElement(et))
        private fun ofType(vararg types: IElementType) = or(*types.map { PlatformPatterns.psiElement(it) }.toTypedArray())
        private fun <T : PsiElement> ofTypeK(vararg types: Class<T>) = or(*types.map { PlatformPatterns.psiElement(it) }.toTypedArray())
        private fun <T : PsiElement> withParent(et: Class<T>) = PlatformPatterns.psiElement().withParent(PlatformPatterns.psiElement(et))
        private fun <T : PsiElement> withGrandParent(et: Class<T>) = PlatformPatterns.psiElement().withSuperParent(2, PlatformPatterns.psiElement(et))
        private fun <T : PsiElement> withParentOrGrandParent(et: Class<T>) = or(withParent(et), withGrandParent(et))
        private fun <T : PsiElement> withGrandParents(vararg et: Class<out T>) = or(*et.map { withGrandParent(it) }.toTypedArray())
        private fun <T : PsiElement> withGreatGrandParents(vararg et: Class<out T>) = or(*et.map { PlatformPatterns.psiElement().withSuperParent(3, it) }.toTypedArray())
        private fun <T : PsiElement> withParents(vararg et: Class<out T>) = or(*et.map { withParent(it) }.toTypedArray())
        private fun <T : PsiElement> withAncestors(vararg et: Class<out T>): ElementPattern<PsiElement> = and(*et.mapIndexed { i, it -> PlatformPatterns.psiElement().withSuperParent(i + 1, PlatformPatterns.psiElement(it)) }.toTypedArray())

        val PREC_CONTEXT = or(afterLeaf(FUNCTION_KW), afterLeaf(COERCE_KW), afterLeaf(LEVEL_KW), afterLeaf(DATA_KW), afterLeaf(CLASS_KW), afterLeaf(RECORD_KW), and(afterLeaf(AS_KW), withGrandParent(ArendNsId::class.java)),
                and(afterLeaf(PIPE), withGrandParents(ArendConstructor::class.java, ArendDataBody::class.java)), //simple data type constructor
                and(afterLeaf(FAT_ARROW), withGrandParents(ArendConstructor::class.java, ArendConstructorClause::class.java)), //data type constructors with patterns
                and(afterLeaf(PIPE), withGrandParents(ArendClassField::class.java, ArendClassStat::class.java)), //class field
                and(afterLeaf(FAT_ARROW), withGrandParent(ArendClassFieldSyn::class.java))) //class field synonym

        val AS_CONTEXT = and(withGrandParent(ArendNsId::class.java), withParents(ArendRefIdentifier::class.java, PsiErrorElement::class.java))
        val NS_CMD_CONTEXT = withAncestors(PsiErrorElement::class.java, ArendStatCmd::class.java)
        val ANY: PsiElementPattern.Capture<PsiElement> = PlatformPatterns.psiElement()
        val STATEMENT_END_CONTEXT = or(withParents(PsiErrorElement::class.java, ArendRefIdentifier::class.java),
                withAncestors(ArendDefIdentifier::class.java, ArendFieldDefIdentifier::class.java)) //Needed for correct completion inside empty classes
        val WHERE_CONTEXT = and(or(STATEMENT_END_CONTEXT, withAncestors(ArendDefIdentifier::class.java, ArendIdentifierOrUnknown::class.java, ArendNameTele::class.java)),
                not(PREC_CONTEXT), not(or(afterLeaf(COLON), afterLeaf(TRUNCATED_KW), afterLeaf(FAT_ARROW),
                afterLeaf(WITH_KW), afterLeaf(ARROW), afterLeaf(IN_KW), afterLeaf(INSTANCE_KW), afterLeaf(EXTENDS_KW), afterLeaf(DOT), afterLeaf(NEW_KW),
                afterLeaf(CASE_KW), afterLeaf(LET_KW), afterLeaf(WHERE_KW), afterLeaf(USE_KW), afterLeaf(PIPE))),
                not(withAncestors(PsiErrorElement::class.java, ArendDefInstance::class.java)), // don't allow \where in incomplete instance expressions
                not(withAncestors(ArendDefIdentifier::class.java, ArendIdentifierOrUnknown::class.java, ArendNameTele::class.java, ArendDefInstance::class.java)))
        val DATA_CONTEXT = withAncestors(PsiErrorElement::class.java, ArendDefData::class.java, ArendStatement::class.java)
        val EXPRESSION_CONTEXT = and(or(withAncestors(ArendRefIdentifier::class.java, ArendLongName::class.java, ArendLiteral::class.java, ArendAtom::class.java),
                withParentOrGrandParent(ArendFunctionBody::class.java),
                withParentOrGrandParent(ArendExpr::class.java),
                withAncestors(PsiErrorElement::class.java, ArendClause::class.java),
                withAncestors(PsiErrorElement::class.java, ArendTupleExpr::class.java),
                withAncestors(PsiErrorElement::class.java, ArendClassStat::class.java),
                withAncestors(PsiErrorElement::class.java, ArendCoClauses::class.java, ArendDefInstance::class.java),
                and(ofType(INVALID_KW), afterLeaf(COLON), withParent(ArendNameTele::class.java))),
                not(or(afterLeaf(PIPE), afterLeaf(COWITH_KW)))) // no expression keywords after pipe
        val CASE_CONTEXT = or(EXPRESSION_CONTEXT, withAncestors(PsiErrorElement::class.java, ArendCaseArg::class.java, ArendCaseExpr::class.java))
        val FIELD_CONTEXT = withAncestors(ArendFieldAcc::class.java, ArendAtomFieldsAcc::class.java)
        val TELE_CONTEXT =
                or(and(withAncestors(PsiErrorElement::class.java, ArendTypeTele::class.java),
                        withGreatGrandParents(ArendClassField::class.java, ArendConstructor::class.java, ArendDefData::class.java, ArendPiExpr::class.java, ArendSigmaExpr::class.java)),
                        withAncestors(ArendRefIdentifier::class.java, ArendLongName::class.java, ArendLiteral::class.java, ArendTypeTele::class.java))
        val FIRST_TYPE_TELE_CONTEXT = and(afterLeaf(ID), withParent(PsiErrorElement::class.java),
                withGrandParents(ArendDefData::class.java, ArendClassField::class.java, ArendConstructor::class.java))

        val DATA_OR_EXPRESSION_CONTEXT = or(DATA_CONTEXT, EXPRESSION_CONTEXT, TELE_CONTEXT, FIRST_TYPE_TELE_CONTEXT)
        val ARGUMENT_EXPRESSION = or(withAncestors(ArendRefIdentifier::class.java, ArendLongName::class.java, ArendLiteral::class.java, ArendAtom::class.java, ArendAtomFieldsAcc::class.java, ArendAtomArgument::class.java),
                withAncestors(PsiErrorElement::class.java, ArendAtomFieldsAcc::class.java, ArendArgumentAppExpr::class.java))
        val LPH_CONTEXT = and(withParent(PsiErrorElement::class.java), withGrandParents(ArendSetUniverseAppExpr::class.java, ArendUniverseAppExpr::class.java, ArendTruncatedUniverseAppExpr::class.java))
        val LPH_LEVEL_CONTEXT = and(withAncestors(PsiErrorElement::class.java, ArendAtomLevelExpr::class.java))
        val ELIM_CONTEXT = and(not(or(afterLeaf(DATA_KW), afterLeaf(FUNCTION_KW), afterLeaf(COERCE_KW), afterLeaf(TRUNCATED_KW), afterLeaf(COLON))),
                or(EXPRESSION_CONTEXT, TELE_CONTEXT,
                        withAncestors(ArendDefIdentifier::class.java, ArendIdentifierOrUnknown::class.java, ArendNameTele::class.java, ArendDefFunction::class.java),
                        withAncestors(PsiErrorElement::class.java, ArendNameTele::class.java, ArendDefFunction::class.java),
                        withAncestors(PsiErrorElement::class.java, ArendDefData::class.java)))
        val ARGUMENT_EXPRESSION_IN_BRACKETS =
                withAncestors(ArendRefIdentifier::class.java, ArendLongName::class.java, ArendLiteral::class.java, ArendAtom::class.java,
                        ArendAtomFieldsAcc::class.java, ArendArgumentAppExpr::class.java, ArendNewExpr::class.java, ArendTupleExpr::class.java, ArendTuple::class.java,
                        ArendAtom::class.java, ArendAtomFieldsAcc::class.java, ArendAtomArgument::class.java, ArendArgumentAppExpr::class.java)
        val INSTANCE_CONTEXT = withAncestors(ArendRefIdentifier::class.java, ArendLongName::class.java, ArendLiteral::class.java, ArendAtom::class.java, ArendAtomFieldsAcc::class.java, ArendArgumentAppExpr::class.java, ArendDefInstance::class.java)
        val GOAL_IN_COPATTERN = ArendCompletionContributor.withAncestors(ArendLiteral::class.java, ArendAtom::class.java, ArendAtomFieldsAcc::class.java,
                ArendArgumentAppExpr::class.java, ArendNewExpr::class.java, ArendCoClause::class.java)

        private fun noUsing(cmd: ArendStatCmd): Boolean = cmd.nsUsing?.usingKw == null
        private fun noHiding(cmd: ArendStatCmd): Boolean = cmd.hidingKw == null
        private fun noUsingAndHiding(cmd: ArendStatCmd): Boolean = noUsing(cmd) && noHiding(cmd)


        class ProviderWithCondition(private val condition: (CompletionParameters, ProcessingContext?) -> Boolean, private val completionProvider: CompletionProvider<CompletionParameters>) : CompletionProvider<CompletionParameters>() {
            override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
                if (condition(parameters, context))
                    completionProvider.addCompletionVariants(parameters, context, result)
            }
        }

        private fun <T> originalPositionCondition(opc: ElementPattern<T>, completionProvider: CompletionProvider<CompletionParameters>): CompletionProvider<CompletionParameters> =
                ProviderWithCondition({ parameters, context ->
                    opc.accepts(parameters.originalPosition, context)
                }, completionProvider)

        private class JointData(val prevElement: PsiElement?, val delimeterBeforeCaret: Boolean, val nextElement: PsiElement?)

        private fun textBeforeCaret(whiteSpace: PsiWhiteSpace, caretOffset: Int): String = when {
            whiteSpace.textRange.contains(caretOffset) -> whiteSpace.text.substring(0, caretOffset - whiteSpace.textRange.startOffset)
            caretOffset < whiteSpace.textRange.startOffset -> ""
            else -> whiteSpace.text
        }


        private fun elementsOnJoint(file: PsiFile, caretOffset: Int): JointData {
            var ofs = 0
            var nextElement: PsiElement?
            var prevElement: PsiElement?
            var delimiterBeforeCaret = false
            var skippedFirstErrorExpr: PsiElement? = null
            do {
                val pos = caretOffset + (ofs++)
                nextElement = if (pos > file.textLength) null else file.findElementAt(pos)
            } while (nextElement is PsiWhiteSpace || nextElement is PsiComment)
            ofs = -1
            do {
                val pos = caretOffset + (ofs--)
                prevElement = if (pos < 0) null else file.findElementAt(pos)
                delimiterBeforeCaret = delimiterBeforeCaret || (prevElement is PsiWhiteSpace && textBeforeCaret(prevElement, caretOffset).contains('\n')) || (pos <= 0)
                var skipFirstErrorExpr = (prevElement?.node?.elementType == BAD_CHARACTER || (prevElement?.node?.elementType == INVALID_KW &&
                        prevElement?.parent is PsiErrorElement && prevElement.text.startsWith("\\")))
                if (skipFirstErrorExpr && skippedFirstErrorExpr != null && skippedFirstErrorExpr != prevElement) skipFirstErrorExpr = false else skippedFirstErrorExpr = prevElement
            } while (prevElement is PsiWhiteSpace || prevElement is PsiComment || skipFirstErrorExpr)

            return JointData(prevElement, delimiterBeforeCaret, nextElement)
        }

        private fun ancestorsUntil(condition: (PsiElement) -> Boolean, element: PsiElement?): List<PsiElement> {
            val ancestors = ArrayList<PsiElement>()
            var elem: PsiElement? = element
            if (elem != null) ancestors.add(elem)
            while (elem != null && !condition.invoke(elem)) {
                elem = elem.parent
                if (elem != null) ancestors.add(elem)
            }
            return ancestors
        }

        private fun genericJointCondition(condition: (CompletionParameters, ProcessingContext?, JointData) -> Boolean, completionProvider: CompletionProvider<CompletionParameters>) =
                ProviderWithCondition({ parameters, context -> condition(parameters, context, elementsOnJoint(parameters.originalFile, parameters.offset)) }, completionProvider)


        private fun parentIsStatementHolder(p: PsiElement?) = when (p?.parent) {
            is ArendWhere -> true
            is ArendDefClass -> (p.parent as ArendDefClass).fatArrow == null
            else -> false
        }

        private fun onJointOfStatementsCondition(statementCondition: (PsiElement) -> Boolean, completionProvider: CompletionProvider<CompletionParameters>,
                                                 noCrlfRequired: Boolean = false, allowInsideBraces: Boolean = true,
                                                 additionalCondition: (JointData) -> Boolean = { _: JointData -> true }): CompletionProvider<CompletionParameters> =
                genericJointCondition({ _, _, jointData ->
                    val ancestorsNE = ancestorsUntil(statementCondition, jointData.nextElement)
                    val ancestorsPE = ancestorsUntil(statementCondition, jointData.prevElement)
                    val leftBrace = jointData.prevElement?.node?.elementType == LBRACE && parentIsStatementHolder(jointData.prevElement)
                    val rightBrace = jointData.nextElement?.node?.elementType == RBRACE && parentIsStatementHolder(jointData.nextElement)
                    val leftStatement = ancestorsPE.lastOrNull()
                    val rightStatement = ancestorsNE.lastOrNull()

                    val isBeforeClassFields = rightStatement is ArendClassStat && rightStatement.definition == null
                    val betweenStatementsOk = leftStatement != null && rightStatement != null && !isBeforeClassFields &&
                            ancestorsNE.intersect(ancestorsPE).isEmpty() &&
                            statementCondition.invoke(leftStatement) && statementCondition.invoke(rightStatement) &&
                            ancestorsNE.last().parent == ancestorsPE.last().parent

                    val leftSideOk = (leftStatement == null || leftBrace && allowInsideBraces) && !isBeforeClassFields
                    val rightSideOk = (rightStatement == null || rightBrace && !leftBrace)

                    val correctStatements = leftSideOk || rightSideOk || betweenStatementsOk

                    (jointData.delimeterBeforeCaret || noCrlfRequired) && additionalCondition(jointData) && correctStatements
                }, completionProvider)

        // Contribution to LookupElementBuilder
        fun LookupElementBuilder.withPriority(priority: Double): LookupElement = PrioritizedLookupElement.withPriority(this, priority)
    }

    open class LoggerCompletionProvider : CompletionProvider<CompletionParameters>() {
        override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
            val text = parameters.position.containingFile.text

            val mn = Math.max(0, parameters.position.node.startOffset - 15)
            val mx = Math.min(text.length, parameters.position.node.startOffset + parameters.position.node.textLength + 15)
            System.out.println("")
            System.out.println("surround text: ${text.substring(mn, mx).replace("\n", "\\n")}")
            System.out.println("position: " + parameters.position.javaClass + " text: " + parameters.position.text)
            var i = 0
            var pp: PsiElement? = parameters.position
            while (i < 13 && pp != null) {
                System.out.format("position.parent(%2d): %-40s text: %-50s\n", i, pp.javaClass.simpleName, pp.text)
                pp = pp.parent
                i++
            }
            System.out.println("originalPosition.parent: " + parameters.originalPosition?.parent?.javaClass)
            System.out.println("originalPosition.grandparent: " + parameters.originalPosition?.parent?.parent?.javaClass)
            val jointData = elementsOnJoint(parameters.originalFile, parameters.offset)
            System.out.println("prevElement: ${jointData.prevElement} text: ${jointData.prevElement?.text}")
            System.out.println("prevElement.parent: ${jointData.prevElement?.parent?.javaClass}")
            System.out.println("prevElement.grandparent: ${jointData.prevElement?.parent?.parent?.javaClass}")
            System.out.println("nextElement: ${jointData.nextElement} text: ${jointData.nextElement?.text}")
            System.out.println("nextElement.parent: ${jointData.nextElement?.parent?.javaClass}")
            if (parameters.position.parent is PsiErrorElement) System.out.println("errorDescription: " + (parameters.position.parent as PsiErrorElement).errorDescription)
            System.out.println("")
            System.out.flush()
        }
    }

    open class KeywordCompletionProvider(private val keywords: List<String>, private val tailSpaceNeeded: Boolean = true) : CompletionProvider<CompletionParameters>() {

        open fun insertHandler(keyword: String): InsertHandler<LookupElement> = InsertHandler { insertContext, _ ->
            val document = insertContext.document
            if (tailSpaceNeeded) document.insertString(insertContext.tailOffset, " ") // add tail whitespace
            insertContext.commitDocument()
            insertContext.editor.caretModel.moveToOffset(insertContext.tailOffset)
        }

        open fun lookupElement(keyword: String): LookupElementBuilder = LookupElementBuilder.create(keyword)

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
            if (ofTypeK(PsiComment::class.java).accepts(parameters.position) || // Prevents showing kw completions in comments
                    afterLeaf(DOT).accepts(parameters.position))                // Prevents showing kw completions after dot expression
                return

            val prefix = computePrefix(parameters, resultSet)

            val prefixMatcher = object : PlainPrefixMatcher(prefix) {
                override fun prefixMatches(name: String): Boolean = isStartMatch(name)
            }

            for (keyword in keywords)
                resultSet.withPrefixMatcher(prefixMatcher).addElement(lookupElement(keyword).withInsertHandler(insertHandler(keyword)).withPriority(KEYWORD_PRIORITY))
        }
    }

    override fun invokeAutoPopup(position: PsiElement, typeChar: Char): Boolean = typeChar == '\\'
}
