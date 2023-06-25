package org.arend.codeInsight

import com.intellij.lang.parameterInfo.*
import com.intellij.openapi.project.ProjectManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.*
import org.arend.error.CountingErrorReporter
import org.arend.error.DummyErrorReporter
import org.arend.ext.error.GeneralError
import org.arend.naming.reference.ClassReferable
import org.arend.naming.reference.Referable
import org.arend.naming.resolving.visitor.ExpressionResolveNameVisitor
import org.arend.psi.*
import org.arend.psi.ArendElementTypes.*
import org.arend.psi.ext.*
import org.arend.psi.parentOfType
import org.arend.resolving.ArendReferableConverter
import org.arend.resolving.DataLocatedReferable
import org.arend.resolving.util.ParameterImpl
import org.arend.term.abs.Abstract
import org.arend.term.abs.BaseAbstractExpressionVisitor
import org.arend.term.abs.ConcreteBuilder
import org.arend.term.concrete.Concrete
import org.arend.resolving.util.parseBinOp
import org.arend.term.abs.Abstract.ParametersHolder
import org.arend.util.checkConcreteExprIsArendExpr
import java.util.Collections.singletonList

class ArendParameterInfoHandler: ParameterInfoHandler<ArendReferenceContainer, List<Abstract.Parameter>> {

    override fun updateUI(p: List<Abstract.Parameter>?, context: ParameterInfoUIContext) {
        if (p == null) return
        var curOffset = 0
        var text = ""
        var hlStart = -1; var hlEnd = -1
        var ind = 0
        var curParam = context.currentParameterIndex
        for (pm in p) {
            if (pm is ArendNameTele && pm.identifierOrUnknownList.firstOrNull()?.text == "this") {
                curParam = if (curParam == -1) -1 else curParam - 1
                continue
            }
            val nameTypeList = mutableListOf<Pair<String?, String?>>()
            val vars = pm.referableList
            if (vars.isNotEmpty()) {
                vars.mapTo(nameTypeList) { Pair(it?.textRepresentation() ?: "_", exprToString(pm.type)) }
            } else {
                nameTypeList.add(Pair("_", exprToString(pm.type)))
            }
            for (v in nameTypeList) {
                if (text != "") {
                    text += ", "
                }
                var varText = v.first + if (v.second != null) " : " + v.second else ""
                if (!pm.isExplicit) {
                    varText = "{$varText}"
                }
                text += varText
                if (ind == curParam) {
                    hlStart = curOffset
                    hlEnd = text.length + 1
                }
                curOffset = text.length + 2
                ++ind
            }
        }
        context.setupUIComponentPresentation(text, hlStart, hlEnd, !context.isUIComponentEnabled, false, false, context.defaultParameterColor)
    }

    override fun findElementForParameterInfo(context: CreateParameterInfoContext): ArendReferenceContainer? {
        val offset = context.editor.caretModel.offset //adjustOffset(context.file, context.editor.caretModel.offset)
        val appExprInfo = findAppExpr(context.file, offset)
        val ref = appExprInfo?.second
        val referable = ref?.resolve as? Referable //ref?.referent?.let{ resolveIfNeeded(it, (ref as ArendSourceNode).scope) }
        val params = referable?.let { getAllParametersForReferable(it, calledFromImportHintAction = true) }

        if (params != null && params.isNotEmpty()) {
            context.itemsToShow = arrayOf(params) //(referable as Abstract.ParametersHolder).parameters)
        } else {
            context.itemsToShow = null
        }

        return appExprInfo?.second
    }

    override fun showParameterInfo(element: ArendReferenceContainer, context: CreateParameterInfoContext) {
        context.showHint(element, element.textRange.startOffset, this)
    }

    override fun findElementForUpdatingParameterInfo(context: UpdateParameterInfoContext): ArendReferenceContainer? {
        val offset = context.editor.caretModel.offset // adjustOffset(context.file, context.editor.caretModel.offset)
        val appExprInfo: Pair<Int, ArendReferenceContainer> = findAppExpr(context.file, offset) ?: return null

        if (context.parameterOwner != appExprInfo.second) {
            return null
        }

        context.setCurrentParameter(appExprInfo.first)
        return appExprInfo.second
    }

    override fun updateParameterInfo(parameterOwner: ArendReferenceContainer, context: UpdateParameterInfoContext) {

    }

    companion object {
        fun findParamIndex(params: List<Abstract.Parameter>, argsExplicitness: List<Boolean>): Int {
            if (argsExplicitness.isEmpty()) return -1

            val argIsExplicit = argsExplicitness.last()
            var numExplicitsBefore = 0
            var numImplicitsJustBefore = 0
            for (i in 0 until argsExplicitness.size - 1) {
                if (argsExplicitness[i]) {
                    ++numExplicitsBefore
                    numImplicitsJustBefore = 0
                } else {
                    ++numImplicitsJustBefore
                }
            }
            var paramIndex = 0
            loop@for (p in params.indices) {
                for (v in params[p].referableList) {
                    if (numExplicitsBefore == 0) {
                        if ((argIsExplicit && params[p].isExplicit) ||
                            (!argIsExplicit && numImplicitsJustBefore == 0)) {
                            break@loop
                        }
                        --numImplicitsJustBefore
                    } else if (params[p].isExplicit) {
                        --numExplicitsBefore
                    }
                    ++paramIndex
                }
            }
            return if (numExplicitsBefore == 0 && numImplicitsJustBefore <= 0) paramIndex else -1
        }

        fun getImplicitPrefixForReferable(def: ParametersHolder, newParameters: List<Referable?>? = null, newParametersReceiver: MutableList<Referable?>? = null): MutableList<Abstract.Parameter> {
            val params = ArrayList<Abstract.Parameter>()

            val defAncestors = (def as? PsiElement)?.ancestors?.toList()?.filterIsInstance<PsiReferable>()
            val containingClass = (def as? PsiElement)?.ancestor<ArendDefClass>()?.let {defClass ->
                if (def is ArendClassField || defAncestors != null && defAncestors.any { defClass.dynamicSubgroups.contains<PsiElement>(it) }) defClass else null
            }

            if (containingClass != null) { // constructors in patterns do not have leading {this} argument
                val thisArg = ArendPsiFactory(containingClass.project).createNameTele("this", containingClass.name, false)
                params.add(thisArg)
                newParametersReceiver?.add(thisArg.childOfType<ArendDefIdentifier>())
            }

            val containingData = if (def is ArendConstructor) def.parent?.parent as? ArendDefData else null
            if (def is ArendConstructor) {
                if (containingData != null) {
                    for (tele in containingData.parameters) {
                        val type = exprToString(tele.type)
                        for (p in tele.referableList) {
                            params.add(ArendPsiFactory(def.project).createNameTele(p?.textRepresentation() ?: "_", type, false))
                        }
                    }
                } else {
                    val constructorClause = def.parent as? ArendConstructorClause
                    val dataBody = constructorClause?.parent as? ArendDataBody
                    val elim = dataBody?.elim
                    val data = dataBody?.parent as? ArendDefData
                    val project = data?.project
                    if (data is Abstract.Definition && elim != null && project != null) {
                        val concreteData: Concrete.GeneralDefinition = ConcreteBuilder.convert(
                            ArendReferableConverter,
                            data,
                            CountingErrorReporter(GeneralError.Level.ERROR, DummyErrorReporter.INSTANCE)
                        )
                        if (concreteData is Concrete.DataDefinition) {
                            val clause = concreteData.constructorClauses.firstOrNull {
                                it.constructors.map { (it.data as? DataLocatedReferable)?.data?.element }.contains(def)
                            }
                            val clausePatterns = clause?.patterns?.run {
                                val newList = ArrayList(this)
                                ExpressionResolveNameVisitor(
                                    ArendReferableConverter,
                                    data.scope,
                                    mutableListOf(),
                                    DummyErrorReporter.INSTANCE,
                                    null
                                ).visitPatterns(newList, mutableMapOf())
                                newList
                            }
                            fun collectNamePatterns(pattern: Concrete.Pattern): List<Concrete.NamePattern> =
                                if (pattern is Concrete.NamePattern) singletonList(pattern) else pattern.patterns.map {
                                    collectNamePatterns(it)
                                }.flatten()
                            when {
                                elim.withKw != null -> {
                                    clausePatterns?.map { collectNamePatterns(it) }?.flatten()?.map {
                                        params.add(ParameterImpl(false, singletonList(it.referable), null))
                                    }
                                    if (newParameters != null && newParametersReceiver != null) {
                                        //TODO: Implement me
                                    }
                                }

                                elim.elimKw != null -> {
                                    val dataArgs = data.parameters.map { it.referableList }.flatten()
                                        .filterIsInstance<ArendDefIdentifier>()
                                    val eliminatedArgs =
                                        elim.refIdentifierList.map { it.resolve }.filterIsInstance<ArendDefIdentifier>()

                                    if (eliminatedArgs.size == clausePatterns?.size) {
                                        val dataArgsMapped = dataArgs.map { it ->
                                            val elimIndex = eliminatedArgs.indexOf(it)
                                            if (elimIndex == -1) singletonList(it) else collectNamePatterns(clausePatterns[elimIndex]).map { it.referable }
                                        }
                                        dataArgsMapped.flatten().forEach {
                                            params.add(ParameterImpl(false, singletonList(it), null))
                                        }

                                        if (newParameters != null && newParametersReceiver != null) {
                                            newParameters.map {
                                                val dataArgIndex = if (it != null) dataArgs.indexOf(it) else -1
                                                if (dataArgIndex != -1) newParametersReceiver.addAll(dataArgsMapped[dataArgIndex]) else newParametersReceiver.add(it)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return params
        }

        fun getAllParametersForReferable(def: Referable, needImplicitConstructorParameters: Boolean = true, calledFromImportHintAction: Boolean = false): List<Abstract.Parameter> {
            if (def !is ParametersHolder) return emptyList()
            val psiFactory = ArendPsiFactory((def as PsiElement).project)
            val params = ArrayList<Abstract.Parameter>()

            if (def is ArendDefClass) {
                ClassReferable.Helper.getNotImplementedFields(def).forEach {
                    val isExplicit = when (it) {
                        is ArendFieldDefIdentifier -> it.isExplicitField
                        else -> true
                    }
                    val type = when (it) {
                        is ArendFieldDefIdentifier -> it.parentFieldTele?.type
                        is ArendClassField -> it.resultType
                        else -> null
                    }
                    params.add(psiFactory.createNameTele(it.refName, type?.text ?: "???", isExplicit))
                }

                return params
            }

            if (needImplicitConstructorParameters) params.addAll(getImplicitPrefixForReferable(def))

            params.addAll(def.parameters)

            var resType: ArendExpr? = when (def) {
                is ArendClassField -> def.resultType; is ArendDefFunction -> def.resultType; else -> null
            }
            while (resType != null && calledFromImportHintAction) {
                resType = when (resType) {
                    is ArendArrExpr -> {
                        params.add(psiFactory.createNameTele(null, exprToString(resType.domain), true))
                        resType.codomain
                    }
                    is ArendPiExpr -> {
                        params.addAll(resType.parameters)
                        resType.codomain
                    }

                    is ArendAtomFieldsAcc -> resType.atom.tuple?.tupleExprList?.firstOrNull()?.exprIfSingle
                    else -> null
                }
            }
            return params
        }

        private fun exprToString(expr: Abstract.Expression?): String? =
            if (expr != null) ConcreteBuilder.convertExpression(expr).toString() else null

        private fun isClosingElement(element: PsiElement?) =
            when (element.elementType) {
                null, RPAREN, RBRACE, COMMA -> true
                else -> false
            }

        private fun adjustOffset(file: PsiFile, offset: Int) =
            if (isClosingElement(file.findElementAt(offset))) offset - 1 else offset

        private fun skipWhitespaces(file: PsiFile, offset: Int): PsiElement? {
            var shiftedOffset = offset
            var res:PsiElement?

            do {
                res = file.findElementAt(shiftedOffset)
                --shiftedOffset
            } while (res is PsiWhiteSpace)

            if (res?.parentOfType<ArendSourceNode>(false) is ArendDefFunction) {
                shiftedOffset = offset
                do {
                    res = file.findElementAt(shiftedOffset)
                    ++shiftedOffset
                } while (res is PsiWhiteSpace)
            }

            return res
        }

        private fun findArgInParsedBinopSeq(arg: Abstract.SourceNode, expr: Concrete.Expression, curArgInd: Int, curFunc: ArendReferenceContainer?): Pair<Int, ArendReferenceContainer>? {
            if (checkConcreteExprIsArendExpr(arg, expr)) {
                if (curFunc == null) {
                    return (expr.data as? ArendReferenceContainer)?.let { Pair(-1, it) }
                }
                return Pair(curArgInd, curFunc)
            }
            if (expr is Concrete.AppExpression) {
                val funcRes = findArgInParsedBinopSeq(arg, expr.function, curArgInd, curFunc)
                if (funcRes != null) return funcRes
                var func = (expr.function as? Concrete.ReferenceExpression)?.data as? ArendReferenceContainer

                var funcReferable = func?.resolve as? Referable // resolvedInScope //func?.referent?.let{ resolveIfNeeded(it, arg.scope)}
                val argExplicitness = mutableListOf<Boolean>()

                if (funcReferable !is Abstract.ParametersHolder) {
                    func = null
                    funcReferable = null
                }

                for (argument in expr.arguments) {
                    argExplicitness.add(argument.isExplicit)
                    val argRes = findArgInParsedBinopSeq(arg, argument.expression,
                        funcReferable?.let { findParamIndex(getAllParametersForReferable(it, calledFromImportHintAction = true), argExplicitness) }
                            ?: -1, func)
                    if (argRes != null) return argRes
                }

            } else if (expr is Concrete.LamExpression) {
                return findArgInParsedBinopSeq(arg, expr.body, curArgInd, curFunc)
            }

            return null
        }

        private fun locateArg(arg: Abstract.SourceNode, appExpr: ArendExpr) =
            appExpr.accept(object: BaseAbstractExpressionVisitor<Void, Pair<Int, ArendReferenceContainer>?>(null) {
                override fun visitBinOpSequence(data: Any?, left: Abstract.Expression, sequence: Collection<Abstract.BinOpSequenceElem>, params: Void?): Pair<Int, ArendReferenceContainer>? =
                    findArgInParsedBinopSeq(arg, parseBinOp(left, sequence), -1, null)
            }, null)

        private fun isNewArgumentPosition(file: PsiFile, offset: Int): Boolean {
            val element = file.findElementAt(offset)
            return (element is PsiWhiteSpace || isClosingElement(element)) && file.findElementAt(offset - 1) is PsiWhiteSpace
        }

        private fun isBinOpSeq(expr: ArendExpr): Boolean =
            expr.accept(object: BaseAbstractExpressionVisitor<Void, Boolean>(false) {
                override fun visitBinOpSequence(data: Any?, left: Abstract.Expression, sequence: Collection<Abstract.BinOpSequenceElem>, params: Void?) = true
            }, null)

        private fun isBinOp(def: Referable?, ref: ArendReferenceContainer): Boolean =
            ref is ArendIPName && ref.infix != null || def is ArendDefFunction && ReferableBase.calcPrecedence(def.prec).isInfix

        private fun ascendLongName(node: Abstract.SourceNode): Abstract.SourceNode =
            node.parentSourceNode as? ArendLongName ?: node

        private fun ascendToLiteral(node: Abstract.SourceNode): Abstract.SourceNode =
            node.parentSourceNode as? ArendLiteral ?: node

        private fun ascendLambda(node: Abstract.SourceNode): Abstract.SourceNode? {
            if (node is ArendLamExpr) {
                return node
            }
            (node.parentSourceNode as? ArendLamExpr)?.let {
                return it
            }
            if (node is ArendRefIdentifier) {
                (node.parentSourceNode as? ArendLamExpr)?.let {
                    return it
                }
                ((node.parentSourceNode as? ArendNameTele)?.parentSourceNode as? ArendLamExpr)?.let {
                    return it
                }
            }
            return null
        }

        //TODO: remove this function
        private fun toArgument(node: Abstract.SourceNode): ArendArgument? {
            if (node is ArendArgument) {
                return node
            }

            if (node is ArendTupleExpr) {
                return node.parentSourceNode as? ArendArgument
            }

            return null
        }

        private fun extractRefFromSourceNode(node: Abstract.SourceNode): ArendReferenceContainer? {
            if (node is ArendLiteral) {
                if (node.ipName != null) {
                    return node.ipName
                } else if (node.longName != null) {
                    return node.longName
                }
            }
            return node as? ArendReferenceContainer
        }

        private fun tryToLocateAtTheCurrentLevel(absNode: Abstract.SourceNode, isNewArgPos: Boolean, isLowestLevel: Boolean): Pair<Int, ArendReferenceContainer>? {
            val absNodeParent = ascendToLiteral(absNode).parentSourceNode ?: return null
            val refContainer = extractRefFromSourceNode(absNode)
            if (refContainer != null) {
                val ref = refContainer.resolve as? Referable
                val params = ref?.let { getAllParametersForReferable(it, calledFromImportHintAction = true) }
                if (params != null && params.isNotEmpty()) {
                    val isBinOp = isBinOp(ref, refContainer)
                    val parentAppExprCandidate = absNodeParent.parentSourceNode
                    if (parentAppExprCandidate is ArendExpr) {
                        if (isBinOpSeq(parentAppExprCandidate) && !isBinOp) {
                            val loc = (absNode as? PsiElement)?.parentOfType<ArendExpr>(false)?.let{ locateArg(it, parentAppExprCandidate) } ?: return null
                            if (isNewArgPos && isBinOp(loc.second.resolve as? Referable, refContainer)) {
                                return Pair(0, refContainer)
                            }
                            if (isNewArgPos && isLowestLevel) return Pair(loc.first + 1, loc.second)
                            return loc
                        }
                    }
                    if (isNewArgPos && isLowestLevel) {
                        if (!isBinOp)
                            return Pair(0, refContainer)
                        return Pair(1, refContainer)
                    }
                    return Pair(-1, refContainer)
                }
            }

            var arg: Abstract.SourceNode? = toArgument(absNodeParent) ?: toArgument(absNode)
            var argLoc: Pair<Int, ArendReferenceContainer>? = null

            if (arg == null && absNode is ArendRefIdentifier) {
                arg =  ascendToLiteral(absNode) as? ArendSourceNode
            }

            val argParent = arg?.parentSourceNode as? ArendExpr
            if (argParent != null && isBinOpSeq(argParent)) {
                if (arg is ArendArgument) arg = arg.expression
                argLoc = arg?.let { locateArg(it, argParent) } // (arg.expression as? ArendSourceNode)?.let{ locateArg(it, argParent) }
            } else if (absNodeParent is ArendExpr && isBinOpSeq(absNodeParent)) { // (absNodeParent.parentSourceNode is ArendExpr && absNodeParent.parentSourceNode?.let { isBinOpSeq(it as ArendExpr) } == true) {
                argLoc = (absNode as? ArendExpr)?.let{ locateArg(it, absNodeParent) }
            }

            if (argLoc != null) {
                if (isNewArgPos && isLowestLevel) return Pair(argLoc.first + 1, argLoc.second)
                return argLoc
            }

            return null
        }

        private fun ascendTillAppExpr(node: Abstract.SourceNode, isNewArgPos: Boolean): Pair<Int, ArendReferenceContainer>? {
            val absNode = ascendLongName(node)
            val res = tryToLocateAtTheCurrentLevel(absNode, isNewArgPos, true)

            if (res != null) {
                return res
            }

            return ascendLambda(absNode)?.parentSourceNode?.let{ tryToLocateAtTheCurrentLevel(it, isNewArgPos, false) }
        }

        private fun findAppExpr(file: PsiFile, offset: Int): Pair<Int, ArendReferenceContainer>? {
            val isNewArgPos = isNewArgumentPosition(file, offset)
            val absNode = skipWhitespaces(file, adjustOffset(file, offset))?.let { PsiTreeUtil.findFirstParent(it) { x -> x is Abstract.SourceNode } as? Abstract.SourceNode } ?: return null

            return ascendTillAppExpr(absNode, isNewArgPos)
        }
    }
}