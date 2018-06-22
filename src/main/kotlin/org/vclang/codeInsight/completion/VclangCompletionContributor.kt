package org.vclang.codeInsight.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PlatformPatterns
import com.intellij.patterns.StandardPatterns.*
import com.intellij.psi.*
import com.intellij.psi.TokenType.BAD_CHARACTER
import com.intellij.psi.tree.IElementType
import com.intellij.util.ProcessingContext
import com.intellij.util.containers.isNullOrEmpty
import org.vclang.psi.*
import org.vclang.psi.VcElementTypes.*
import org.vclang.psi.ext.impl.DefinitionAdapter
import org.vclang.search.VcWordScanner
import java.util.*

class VclangCompletionContributor() : CompletionContributor() {

    init {
        extend(CompletionType.BASIC, PREC_CONTEXT, KeywordCompletionProvider(FIXITY_KWS))
        extend(CompletionType.BASIC, afterLeaf(FAT_ARROW), originalPositionCondition(withParentOrGrandParent(VcClassFieldSyn::class.java),
                KeywordCompletionProvider(FIXITY_KWS))) // fixity kws for class field synonym (2nd part)
        extend(CompletionType.BASIC, AS_CONTEXT, ProviderWithCondition({parameters, _ -> (parameters.position.parent.parent as VcNsId).asKw == null},
                KeywordCompletionProvider(AS_KW_LIST)))

        extend(CompletionType.BASIC, NS_CMD_CONTEXT, originalPositionCondition(withParent(VcFile::class.java),
                KeywordCompletionProvider(HU_KW_LIST)))
        extend(CompletionType.BASIC, NS_CMD_CONTEXT, ProviderWithCondition({ parameters, _ -> noUsing(parameters.position.parent.parent as VcStatCmd)},
                KeywordCompletionProvider(USING_KW_LIST)))
        extend(CompletionType.BASIC, NS_CMD_CONTEXT, ProviderWithCondition({ parameters, _ -> noUsingAndHiding(parameters.position.parent.parent as VcStatCmd)},
                KeywordCompletionProvider(HIDING_KW_LIST)))
        extend(CompletionType.BASIC, withAncestors(PsiErrorElement::class.java, VcNsUsing::class.java, VcStatCmd::class.java),
                ProviderWithCondition({parameters, _ -> noHiding(parameters.position.parent.parent.parent as VcStatCmd)},
                        KeywordCompletionProvider(HIDING_KW_LIST)))

        extend(CompletionType.BASIC, STATEMENT_END_CONTEXT,
                onJointOfStatementsCondition(VcStatement::class.java,
                        KeywordCompletionProvider(STATEMENT_WT_KWS)))
        extend(CompletionType.BASIC, STATEMENT_END_CONTEXT,
                onJointOfStatementsCondition(VcStatement::class.java,
                        object: KeywordCompletionProvider(TRUNCATED_KW_LIST){
                            override fun insertHandler(keyword: String): InsertHandler<LookupElement> = InsertHandler {insertContext, _ ->
                                val document = insertContext.document
                                document.insertString(insertContext.tailOffset, " \\data ")
                                insertContext.commitDocument()
                                insertContext.editor.caretModel.moveToOffset(insertContext.tailOffset)
                            }

                            override fun lookupElement(keyword: String): LookupElementBuilder =
                                    LookupElementBuilder.create(keyword).withPresentableText("\\truncated \\data")
                        }))

        extend(CompletionType.BASIC, STATEMENT_END_CONTEXT, onJointOfStatementsCondition(VcStatement::class.java,
                ProviderWithCondition({ parameters, _ -> parameters.position.ancestors.filter { it is VcWhere }.toList().isEmpty() },
                        KeywordCompletionProvider(IMPORT_KW_LIST))))
        extend(CompletionType.BASIC, and(DATA_CONTEXT, afterLeaf(TRUNCATED_KW)), KeywordCompletionProvider(DATA_KW_LIST))//data after \truncated keyword

        extend(CompletionType.BASIC, STATEMENT_END_CONTEXT, onJointOfStatementsCondition(VcStatement::class.java, KeywordCompletionProvider(WHERE_KW_LIST)
        ) { jD: JointData ->
            var anc = jD.prevElement
            while (anc != null && anc !is VcStatement) anc = anc.parent
            var flag = false
            if (anc is VcStatement) {
                val da = anc.definition
                if (da is DefinitionAdapter<*>) {
                    val where =  da.getWhere()
                    if (where == null || (where.lbrace == null && where.rbrace == null)) flag = true
                }
            }
            flag})

        extend(CompletionType.BASIC,  and(withAncestors(PsiErrorElement::class.java, VcDefClass::class.java), afterLeaf(ID)), KeywordCompletionProvider(EXTENDS_KW_LIST))
        extend(CompletionType.BASIC, withAncestors(PsiErrorElement::class.java, VcFieldTele::class.java, VcDefClass::class.java),
                ProviderWithCondition({ parameters, _ ->
                    var nS = parameters.position.parent!!.parent.nextSibling
                    while (nS is PsiWhiteSpace) nS = nS.nextSibling
                    nS !is VcFieldTele
                }, KeywordCompletionProvider(EXTENDS_KW_LIST)))

        extend(CompletionType.BASIC, and(DATA_CONTEXT, afterLeaf(COLON)), KeywordCompletionProvider(DATA_UNIVERSE_KW))

         val bareSigmaOrPi = { expression: PsiElement ->
             var result : PsiElement? = expression

             val context = ofType(PI_KW, SIGMA_KW)

             var tele: VcTypeTele? = null
             while (result != null) {
                 if (result is VcTypeTele) tele = result
                 if (result is VcExpr && result !is VcUniverseAtom) break
                 result = result.parent
             }

             if (context.accepts(expression)) true else
             if (tele?.text == null || tele.text.startsWith("(")) false else //Not Bare \Sigma or \Pi -- should display all expression keywords in completion
                result is VcSigmaExpr || result is VcPiExpr
        }
        val noExpressionKwsAfter = ofType(SET, PROP_KW, UNIVERSE, TRUNCATED_UNIVERSE, NEW_KW)

        val expressionFilter = {basicCompletionProvider: CompletionProvider<CompletionParameters>, allowInBareSigmaOrPiExpressions: Boolean, allowInArgumentExpressionContext: Boolean ->
            genericJointCondition({cP, _, jD ->

                !FIELD_CONTEXT.accepts(jD.prevElement) && //No keyword completion after field
                        !(ofType(RBRACE, WITH_KW).accepts(jD.prevElement) && withParent(VcCaseExpr::class.java).accepts(jD.prevElement)) && //No keyword completion after \with or } in case expr
                        !(ofType(LAM_KW, LET_KW).accepts(jD.prevElement)) && //No keyword completion after \lam or \let
                        !(noExpressionKwsAfter.accepts(jD.prevElement)) && //No expression keyword completion after universe literals or \new keyword
                        !(or(LPH_CONTEXT, LPH_LEVEL_CONTEXT).accepts(cP.position)) && //No expression keywords when completing levels in universes
                        (allowInBareSigmaOrPiExpressions || jD.prevElement == null || !bareSigmaOrPi(jD.prevElement)) &&  //Only universe expressions allowed inside Sigma or Pi expressions
                        (allowInArgumentExpressionContext || !ARGUMENT_EXPRESSION.accepts(cP.position)) // New expressions & universe expressions are allowed in applications
            }, basicCompletionProvider)
        }

        extend(CompletionType.BASIC, EXPRESSION_CONTEXT, expressionFilter.invoke(KeywordCompletionProvider(DATA_UNIVERSE_KW), true, true))
        extend(CompletionType.BASIC, or(TELE_CONTEXT, FIRST_TYPE_TELE_CONTEXT), KeywordCompletionProvider(DATA_UNIVERSE_KW))

        extend(CompletionType.BASIC, EXPRESSION_CONTEXT, expressionFilter.invoke(KeywordCompletionProvider(BASIC_EXPRESSION_KW), false, false))
        extend(CompletionType.BASIC, EXPRESSION_CONTEXT, expressionFilter.invoke(KeywordCompletionProvider(NEW_KW_LIST), false, true))

        val truncatedTypeCompletionProvider = object: KeywordCompletionProvider(FAKE_NTYPE_LIST) {
            override fun insertHandler(keyword: String): InsertHandler<LookupElement> = InsertHandler { insertContext, _ ->
                val document = insertContext.document
                document.insertString(insertContext.tailOffset, " ") // add tail whitespace
                insertContext.commitDocument()
                insertContext.editor.caretModel.moveToOffset(insertContext.startOffset+1)
                document.replaceString(insertContext.startOffset+1, insertContext.startOffset+2, "1") //replace letter n by 1 so that the keyword would be highlighted correctly
                insertContext.editor.selectionModel.setSelection(insertContext.startOffset+1, insertContext.startOffset+2)
            }
        }

        extend(CompletionType.BASIC, and(DATA_CONTEXT, afterLeaf(COLON)), truncatedTypeCompletionProvider)
        extend(CompletionType.BASIC, EXPRESSION_CONTEXT, expressionFilter.invoke(truncatedTypeCompletionProvider, true, true))
        extend(CompletionType.BASIC, or(TELE_CONTEXT, FIRST_TYPE_TELE_CONTEXT), truncatedTypeCompletionProvider)

        fun isAfterNumber(element: PsiElement?): Boolean = element?.prevSibling?.text == "\\" && element.node?.elementType == NUMBER

        extend(CompletionType.BASIC, DATA_OR_EXPRESSION_CONTEXT, genericJointCondition({ _, _, jD -> isAfterNumber(jD.prevElement)}, object: KeywordCompletionProvider(listOf("-Type")){
            override fun computePrefix(parameters: CompletionParameters, resultSet: CompletionResultSet): String = ""
        }))

        extend(CompletionType.BASIC, DATA_OR_EXPRESSION_CONTEXT, ProviderWithCondition({cP, _ ->
            cP.originalPosition != null && cP.originalPosition!!.text.matches(Regex("\\\\[0-9]+(-(T(y(pe?)?)?)?)?"))
        }, object: KeywordCompletionProvider(listOf("Type")) {
            override fun computePrefix(parameters: CompletionParameters, resultSet: CompletionResultSet): String =
                    super.computePrefix(parameters, resultSet).replace(Regex("\\\\[0-9]+-?"), "")
        }))

        //val lpKwAfter = or(afterLeaf(SET), afterLeaf(UNIVERSE), afterLeaf(TRUNCATED_UNIVERSE))
        extend(CompletionType.BASIC, LPH_CONTEXT, ProviderWithCondition({ cP, pC ->
            val pp = cP.position.parent.parent
            when (pp) {
                is VcSetUniverseAppExpr, is VcTruncatedUniverseAppExpr ->
                    pp.children.filterIsInstance<VcAtomLevelExpr>().isEmpty()
                else -> pp.children.filterIsInstance<VcAtomLevelExpr>().size <= 1
            }
        }, KeywordCompletionProvider(LPH_KW_LIST)))

        extend(CompletionType.BASIC, withParent(VcLevelExpr::class.java), ProviderWithCondition({cP, _ ->
            when (cP.position.parent?.firstChild?.node?.elementType) {
                MAX_KW, SUC_KW -> true
                else ->false
            }
        }, KeywordCompletionProvider(LPH_KW_LIST)))
        
        extend(CompletionType.BASIC, LPH_LEVEL_CONTEXT, KeywordCompletionProvider(LPH_LEVEL_KWS))

        fun pairingWordCondition (condition: (PsiElement?) -> Boolean, cp: CompletionProvider<CompletionParameters>) = ProviderWithCondition({cP, _ ->
            var position: PsiElement? = cP.position
            var exprFound = false
            while (position != null) {
                if (!(position.nextSibling == null || position.nextSibling is PsiErrorElement)) break
                if (condition.invoke(position)) {
                    exprFound = true
                    break
                }
                position = position.parent
            }
            exprFound
        }, cp)

        extend(CompletionType.BASIC, and(EXPRESSION_CONTEXT, not(afterLeaf(IN_KW))),
                pairingWordCondition({ position: PsiElement? -> position is VcLetExpr && position.inKw == null }, KeywordCompletionProvider(IN_KW_LIST)))

        extend(CompletionType.BASIC, and(EXPRESSION_CONTEXT, not(afterLeaf(WITH_KW))),
                pairingWordCondition({ position -> position is VcCaseExpr && position.withKw == null }, KeywordCompletionProvider(WITH_KW_LIST)))

        extend(CompletionType.BASIC, ELIM_CONTEXT, ProviderWithCondition({cP, _ ->
            var pos2: PsiElement? = cP.position
            var exprFound = false
            while (pos2 != null) {
                if (pos2.nextSibling is PsiWhiteSpace) {
                    if ((pos2.nextSibling.nextSibling is VcFunctionBody) && (pos2.parent is VcDefFunction)) {
                        val fBody = (pos2.parent as VcDefFunction).functionBody
                        exprFound = fBody == null || fBody.expr == null && fBody.functionClauses?.clauseList.isNullOrEmpty()
                        break
                    }
                    if ((pos2.nextSibling.nextSibling is VcDataBody) && (pos2.parent is VcDefData)) {
                        val dBody = (pos2.parent as VcDefData).dataBody
                        exprFound = dBody == null || (dBody.constructorList.isNullOrEmpty() && dBody.constructorClauseList.isNullOrEmpty())
                        break

                    }
                }
                if (pos2.nextSibling == null) pos2 = pos2.parent else break
            }
                exprFound}, KeywordCompletionProvider(ELIM_KW_LIST)))

        //TODO: One more \with keyword occurrence

        extend(CompletionType.BASIC, ANY, LoggerCompletionProvider())
    }

    companion object {
        val FIXITY_KWS = listOf(INFIX_LEFT_KW, INFIX_RIGHT_KW, INFIX_NON_KW, NON_ASSOC_KW, LEFT_ASSOC_KW, RIGHT_ASSOC_KW).map { it.toString() }
        val STATEMENT_WT_KWS = listOf(FUNCTION_KW, DATA_KW, CLASS_KW, INSTANCE_KW, OPEN_KW).map {it.toString()}
        val DATA_UNIVERSE_KW = listOf("\\Type", "\\Set", PROP_KW.toString(), "\\oo-Type")
        val BASIC_EXPRESSION_KW = listOf(PI_KW, SIGMA_KW, LAM_KW, LET_KW, CASE_KW).map { it.toString() }
        val LEVEL_KWS = listOf(MAX_KW, SUC_KW).map { it.toString() }
        val LPH_KW_LIST = listOf(LP_KW, LH_KW).map { it.toString() }

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
        val ELIM_KW_LIST = listOf(ELIM_KW.toString())

        val STATEMENT_KWS = STATEMENT_WT_KWS + TRUNCATED_KW_LIST
        val GLOBAL_STATEMENT_KWS = STATEMENT_KWS + IMPORT_KW_LIST
        val HU_KW_LIST = USING_KW_LIST + HIDING_KW_LIST
        val DATA_OR_EXPRESSION_KW = DATA_UNIVERSE_KW + BASIC_EXPRESSION_KW + NEW_KW_LIST
        val LPH_LEVEL_KWS = LPH_KW_LIST + LEVEL_KWS

        const val KEYWORD_PRIORITY = 10.0

        private fun afterLeaf(et: IElementType) = PlatformPatterns.psiElement().afterLeaf(PlatformPatterns.psiElement(et))
        private fun ofType(vararg types: IElementType) = or(*types.map { PlatformPatterns.psiElement(it) }.toTypedArray())
        private fun <T : PsiElement> withParent(et: Class<T>) = PlatformPatterns.psiElement().withParent(PlatformPatterns.psiElement(et))
        private fun <T : PsiElement> withGrandParent(et: Class<T>) = PlatformPatterns.psiElement().withSuperParent(2, PlatformPatterns.psiElement(et))
        private fun <T : PsiElement> withParentOrGrandParent(et: Class<T>) = or(withParent(et), withGrandParent(et))
        private fun <T : PsiElement> withGrandParents(vararg et: Class<out T>) = or(*et.map { withGrandParent(it) }.toTypedArray())
        private fun <T : PsiElement> withGreatGrandParents(vararg et: Class<out T>) = or(*et.map { PlatformPatterns.psiElement().withSuperParent(3, it) }.toTypedArray())
        private fun <T : PsiElement> withParents(vararg et: Class<out T>) = or(*et.map { withParent(it) }.toTypedArray())
        private fun <T : PsiElement> withAncestors(vararg et: Class<out T>): ElementPattern<PsiElement> = and(*et.mapIndexed { i, it -> PlatformPatterns.psiElement().withSuperParent(i + 1, PlatformPatterns.psiElement(it)) }.toTypedArray())

        val PREC_CONTEXT = or(afterLeaf(FUNCTION_KW), afterLeaf(DATA_KW), afterLeaf(CLASS_KW), afterLeaf(AS_KW),
                and(afterLeaf(PIPE), withGrandParents(VcConstructor::class.java, VcDataBody::class.java)), //simple data type constructor
                and(afterLeaf(FAT_ARROW), withGrandParents(VcConstructor::class.java, VcConstructorClause::class.java)), //data type constructors with patterns
                and(afterLeaf(PIPE), withGrandParents(VcClassField::class.java, VcClassStat::class.java)), //class field
                and(afterLeaf(FAT_ARROW), withGrandParent(VcClassFieldSyn::class.java))) //class field synonym

        val AS_CONTEXT = and(withGrandParent(VcNsId::class.java), withParents(VcRefIdentifier::class.java, PsiErrorElement::class.java))
        val NS_CMD_CONTEXT = withAncestors(PsiErrorElement::class.java, VcStatCmd::class.java)
        val ANY = PlatformPatterns.psiElement()!!
        val STATEMENT_END_CONTEXT = withParents(PsiErrorElement::class.java, VcRefIdentifier::class.java)
        val DATA_CONTEXT = withAncestors(PsiErrorElement::class.java, VcDefData::class.java, VcStatement::class.java)
        val EXPRESSION_CONTEXT = or(withAncestors(VcRefIdentifier::class.java, VcLongName::class.java, VcLiteral::class.java, VcAtom::class.java),
                                    withParentOrGrandParent(VcFunctionBody::class.java),
                                    withParentOrGrandParent(VcExpr::class.java),
                                    withAncestors(PsiErrorElement::class.java, VcClause::class.java))
        val FIELD_CONTEXT = withAncestors(VcFieldAcc::class.java, VcAtomFieldsAcc::class.java)
        val TELE_CONTEXT =
                or(and(withAncestors(PsiErrorElement::class.java, VcTypeTele::class.java),
                        withGreatGrandParents(VcClassField::class.java, VcConstructor::class.java, VcDefData::class.java, VcPiExpr::class.java, VcSigmaExpr::class.java)),
                withAncestors(VcRefIdentifier::class.java, VcLongName::class.java, VcLiteral::class.java, VcTypeTele::class.java))
        val FIRST_TYPE_TELE_CONTEXT = and(afterLeaf(ID), withParent(PsiErrorElement::class.java),
                withGrandParents(VcDefData::class.java, VcClassField::class.java, VcConstructor::class.java))

        val DATA_OR_EXPRESSION_CONTEXT = or(DATA_CONTEXT, EXPRESSION_CONTEXT, TELE_CONTEXT, FIRST_TYPE_TELE_CONTEXT)
        val ARGUMENT_EXPRESSION = or(withAncestors(VcRefIdentifier::class.java, VcLongName::class.java, VcLiteral::class.java, VcAtom::class.java, VcAtomFieldsAcc::class.java, VcAtomArgument::class.java),
                withAncestors(PsiErrorElement::class.java, VcAtomFieldsAcc::class.java, VcArgumentAppExpr::class.java))
        val LPH_CONTEXT = and(withParent(PsiErrorElement::class.java), withGrandParents(VcSetUniverseAppExpr::class.java, VcUniverseAppExpr::class.java, VcTruncatedUniverseAppExpr::class.java))
        val LPH_LEVEL_CONTEXT = and(withAncestors(PsiErrorElement::class.java, VcAtomLevelExpr::class.java)
                /*,withGreatGrandParents(VcSetUniverseAppExpr::class.java, VcUniverseAppExpr::class.java, VcTruncatedUniverseAppExpr::class.java)*/)
        val ELIM_CONTEXT = and(not(or(afterLeaf(DATA_KW), afterLeaf(FUNCTION_KW), afterLeaf(TRUNCATED_KW), afterLeaf(COLON))),
                or(EXPRESSION_CONTEXT, TELE_CONTEXT,
                        withAncestors(VcDefIdentifier::class.java, VcIdentifierOrUnknown::class.java, VcNameTele::class.java),
                        withAncestors(PsiErrorElement::class.java, VcNameTele::class.java),
                        withAncestors(PsiErrorElement::class.java, VcDefData::class.java),
                        withAncestors(PsiErrorElement::class.java, VcDefFunction::class.java)))

        private fun noUsing(cmd: VcStatCmd): Boolean = cmd.nsUsing?.usingKw == null
        private fun noHiding(cmd: VcStatCmd): Boolean = cmd.hidingKw == null
        private fun noUsingAndHiding(cmd: VcStatCmd): Boolean = noUsing(cmd) && noHiding(cmd)


        class ProviderWithCondition(private val condition: (CompletionParameters, ProcessingContext?) -> Boolean, private val completionProvider: CompletionProvider<CompletionParameters>) : CompletionProvider<CompletionParameters>() {
            override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext?, result: CompletionResultSet) {
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

        private fun <T> ancestorsUntil(c: Class<T>, element: PsiElement?): HashSet<PsiElement> {
            val ancestors = HashSet<PsiElement>()
            var elem: PsiElement? = element
            if (elem != null) ancestors.add(elem)
            while (elem != null && !c.isInstance(elem)) {
                elem = elem.parent
                ancestors.add(elem)
            }
            return ancestors
        }

        private fun genericJointCondition(condition: (CompletionParameters, ProcessingContext?, JointData) -> Boolean, completionProvider: CompletionProvider<CompletionParameters>) =
                ProviderWithCondition({ parameters, context -> condition(parameters, context, elementsOnJoint(parameters.originalFile, parameters.offset)) }, completionProvider)


        private fun <T> onJointOfStatementsCondition(statementClass: Class<T>, completionProvider: CompletionProvider<CompletionParameters>,
                                                     additionalCondition: (JointData) -> Boolean = {_: JointData -> true}): CompletionProvider<CompletionParameters> =
                genericJointCondition({_, _, jointData ->
                    val ancestorsNE = ancestorsUntil(statementClass, jointData.nextElement)
                    val ancestorsPE = ancestorsUntil(statementClass, jointData.prevElement)
                    jointData.delimeterBeforeCaret && additionalCondition(jointData) && ancestorsNE.intersect(ancestorsPE).isEmpty() }, completionProvider)

        // Contribution to LookupElementBuilder
        fun LookupElementBuilder.withPriority(priority: Double): LookupElement = PrioritizedLookupElement.withPriority(this, priority)
    }

    open class LoggerCompletionProvider: CompletionProvider<CompletionParameters>() {
        override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext?, result: CompletionResultSet) {
            val text = parameters.position.containingFile.text

            val mn = Math.max(0, parameters.position.node.startOffset - 15)
            val mx = Math.min(text.length, parameters.position.node.startOffset + parameters.position.node.textLength + 15)

            System.out.println("")
            System.out.println("surround text: ${text.substring(mn, mx).replace("\n", "\\n")}")
            System.out.println("position.parent: "+parameters.position.parent?.javaClass + " text: " + parameters.position.parent?.text)
            System.out.println("position.grandparent: "+parameters.position.parent?.parent?.javaClass + " text: " + parameters.position.parent?.parent?.text)
            System.out.println("position.great-grandparent: "+parameters.position.parent?.parent?.parent?.javaClass + " text: " + parameters.position.parent?.parent?.parent?.text)
            System.out.println("position.great-great-grandparent: "+parameters.position.parent?.parent?.parent?.parent?.javaClass + " text: " + parameters.position.parent?.parent?.parent?.parent?.text)
            System.out.println("position.great-great-great-grandparent: "+parameters.position.parent?.parent?.parent?.parent?.parent?.javaClass+ " text: " + parameters.position.parent?.parent?.parent?.parent?.parent?.text)
            System.out.println("position.great-great-great-great-grandparent: "+parameters.position.parent?.parent?.parent?.parent?.parent?.parent?.javaClass+ " text: " + parameters.position.parent?.parent?.parent?.parent?.parent?.parent?.text)
            System.out.println("position.great-great-great-great-great-grandparent: "+parameters.position.parent?.parent?.parent?.parent?.parent?.parent?.parent?.javaClass+ " text: " + parameters.position.parent?.parent?.parent?.parent?.parent?.parent?.parent?.text)
            System.out.println("position.great-great-great-great-great-great-grandparent: "+parameters.position.parent?.parent?.parent?.parent?.parent?.parent?.parent?.parent?.javaClass+ " text: " + parameters.position.parent?.parent?.parent?.parent?.parent?.parent?.parent?.parent?.text)
            System.out.println("position.great-great-great-great-great-great-great-grandparent: "+parameters.position.parent?.parent?.parent?.parent?.parent?.parent?.parent?.parent?.parent?.javaClass+ " text: " + parameters.position.parent?.parent?.parent?.parent?.parent?.parent?.parent?.parent?.parent?.text)
            System.out.println("position.great-great-great-great-great-great-great-great-grandparent: "+parameters.position.parent?.parent?.parent?.parent?.parent?.parent?.parent?.parent?.parent?.parent?.javaClass+ " text: " + parameters.position.parent?.parent?.parent?.parent?.parent?.parent?.parent?.parent?.parent?.parent?.text)
            System.out.println("position.great-great-great-great-great-great-great-great-great-grandparent: "+parameters.position.parent?.parent?.parent?.parent?.parent?.parent?.parent?.parent?.parent?.parent?.parent?.javaClass+ " text: " + parameters.position.parent?.parent?.parent?.parent?.parent?.parent?.parent?.parent?.parent?.parent?.parent?.text)
            System.out.println("position.great-great-great-great-great-great-great-great-great-great-grandparent: "+parameters.position.parent?.parent?.parent?.parent?.parent?.parent?.parent?.parent?.parent?.parent?.parent?.parent?.javaClass+ " text: " + parameters.position.parent?.parent?.parent?.parent?.parent?.parent?.parent?.parent?.parent?.parent?.parent?.parent?.text)
            System.out.println("position.great-great-great-great-great-great-great-great-great-great-great-grandparent: "+parameters.position.parent?.parent?.parent?.parent?.parent?.parent?.parent?.parent?.parent?.parent?.parent?.parent?.parent?.javaClass+ " text: " + parameters.position.parent?.parent?.parent?.parent?.parent?.parent?.parent?.parent?.parent?.parent?.parent?.parent?.parent?.text)
            System.out.println("originalPosition.parent: "+parameters.originalPosition?.parent?.javaClass)
            System.out.println("originalPosition.grandparent: "+parameters.originalPosition?.parent?.parent?.javaClass)
            val jointData = elementsOnJoint(parameters.originalFile, parameters.offset)
            System.out.println("prevElement: ${jointData.prevElement} text: ${jointData.prevElement?.text}")
            System.out.println("prevElement.parent: ${jointData.prevElement?.parent?.javaClass}")
            System.out.println("prevElement.grandparent: ${jointData.prevElement?.parent?.parent?.javaClass}")
            System.out.println("nextElement: ${jointData.nextElement} text: ${jointData.nextElement?.text}")
            System.out.println("nextElement.parent: ${jointData.nextElement?.parent?.javaClass}")
            if (parameters.position.parent is PsiErrorElement) System.out.println("errorDescription: "+(parameters.position.parent as PsiErrorElement).errorDescription)
            System.out.println("")
        }
    }

    open class KeywordCompletionProvider(private val keywords : List<String>): CompletionProvider<CompletionParameters>() {

        open fun insertHandler(keyword: String) : InsertHandler<LookupElement> = InsertHandler { insertContext, _ ->
            val document = insertContext.document
            document.insertString(insertContext.tailOffset, " ") // add tail whitespace
            insertContext.commitDocument()
            insertContext.editor.caretModel.moveToOffset(insertContext.tailOffset)
        }

        open fun lookupElement(keyword: String) : LookupElementBuilder = LookupElementBuilder.create(keyword)

        open fun computePrefix(parameters: CompletionParameters, resultSet: CompletionResultSet): String {
            var prefix = resultSet.prefixMatcher.prefix
            val lastInvalidIndex = prefix.mapIndexedNotNull { i, c -> if (!VcWordScanner.isVclangIdentifierPart(c)) i else null}.lastOrNull()
            if (lastInvalidIndex != null) prefix = prefix.substring(lastInvalidIndex+1, prefix.length)
            val pos = parameters.offset - prefix.length - 1
            if (pos >= 0 && pos < parameters.originalFile.textLength)
                prefix = (if (parameters.originalFile.text[pos] == '\\') "\\" else "") + prefix
            return prefix
        }

        override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext?, resultSet: CompletionResultSet) {
            val prefix = computePrefix(parameters, resultSet)
            System.out.println("keywords: $keywords; prefix: $prefix ")

            val prefixMatcher = object: PlainPrefixMatcher(prefix) {
                override fun prefixMatches(name: String): Boolean = isStartMatch(name)
            }

            for (keyword in keywords)
                resultSet.withPrefixMatcher(prefixMatcher).addElement(lookupElement(keyword).bold().withInsertHandler(insertHandler(keyword)).withPriority(KEYWORD_PRIORITY))
        }
    }

    override fun invokeAutoPopup(position: PsiElement, typeChar: Char): Boolean = typeChar == '\\'
}