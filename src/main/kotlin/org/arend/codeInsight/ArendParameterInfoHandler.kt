package org.arend.codeInsight

import com.intellij.lang.parameterInfo.*
import com.intellij.openapi.project.ProjectManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import org.arend.naming.reference.Referable
import org.arend.psi.*
import org.arend.psi.ext.ArendReferenceContainer
import org.arend.psi.ext.ArendSourceNode
import org.arend.psi.ext.impl.ClassFieldAdapter
import org.arend.psi.ext.impl.FunctionDefinitionAdapter
import org.arend.term.abs.Abstract
import org.arend.term.abs.BaseAbstractExpressionVisitor
import org.arend.term.abs.ConcreteBuilder
import org.arend.term.concrete.Concrete
import org.arend.resolving.util.parseBinOp
import org.arend.util.checkConcreteExprIsArendExpr

class ArendParameterInfoHandler: ParameterInfoHandler<ArendReferenceContainer, List<Abstract.Parameter>> {

    private fun exprToString(expr: Abstract.Expression?) =
        if (expr != null) ConcreteBuilder.convertExpression(expr).toString() else "{?error}"

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
            val nameTypeList = mutableListOf<Pair<String?, String>>()
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
                var varText = v.first + " : " + v.second
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

    public fun getAllParametersForReferable(def: Referable): List<Abstract.Parameter> {
        val params = mutableListOf<Abstract.Parameter>()
        val psiFactory = ArendPsiFactory(ProjectManager.getInstance().openProjects.first())
        if (def is Abstract.ParametersHolder) {
            params.addAll(def.parameters)
            var resType: ArendExpr? = when(def) {
                is ClassFieldAdapter ->
                {
                    val defClass = def.parent?.parent as? ArendDefClass
                    val className = defClass?.name
                    if (className != null) {
                        params.add(0, psiFactory.createNameTele("this", className, false))
                    }
                    def.resultType
                }
                is FunctionDefinitionAdapter -> {
                    val defClass = def.parent?.parent as? ArendDefClass
                    val className = defClass?.name
                    if (className != null) {
                        params.add(0, psiFactory.createNameTele("this", className, false))
                    }
                    def.resultType
                }
                else -> null
            }
            if (def is ArendConstructor) {
                val defData = def.parent?.parent as? ArendDefData
                if (defData != null) {
                    for (tele in defData.typeTeleList.reversed()) {
                        val type = exprToString(tele.type)
                        for (p in tele.referableList.reversed()) {
                            params.add(0, psiFactory.createNameTele(p.textRepresentation(), type, false))
                        }
                    }
                }
            }
            while (resType != null) {
                resType = when(resType) {
                    is ArendArrExpr -> {
                        params.add(psiFactory.createNameTele(null,
                                exprToString(resType.exprList.first()), true))
                        resType.exprList[1]
                    }
                    is ArendPiExpr -> {
                        params.addAll(resType.typeTeleList)
                        resType.expr
                    }
                    is ArendAtomFieldsAcc -> {
                        var res: ArendExpr? = null
                        if (resType.atom.tuple != null) {
                            val exprList = (resType.atom.tuple as ArendTuple).tupleExprList.firstOrNull()?.exprList
                            if (exprList?.size == 1) {
                                res = exprList[0]
                            }
                        }
                        res
                    }
                    else -> null
                }
            }
        }
        return params
    }

    override fun findElementForParameterInfo(context: CreateParameterInfoContext): ArendReferenceContainer? {
        val offset = context.editor.caretModel.offset //adjustOffset(context.file, context.editor.caretModel.offset)
        val appExprInfo = findAppExpr(context.file, offset)
        val ref = appExprInfo?.second
        val referable = ref?.resolve as? Referable //ref?.referent?.let{ resolveIfNeeded(it, (ref as ArendSourceNode).scope) }
        val params = referable?.let { getAllParametersForReferable(it) }

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

    private fun isClosingElement(element: PsiElement?) =
        when (element?.elementType) {
            null, ArendElementTypes.RPAREN, ArendElementTypes.RBRACE, ArendElementTypes.COMMA -> true
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

    public fun findParamIndex(params: List<Abstract.Parameter>, argsExplicitness: List<Boolean>): Int {
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


    private fun findArgInParsedBinopSeq(arg: ArendSourceNode, expr: Concrete.Expression, curArgInd: Int, curFunc: ArendReferenceContainer?): Pair<Int, ArendReferenceContainer>? {
        if (checkConcreteExprIsArendExpr(arg, expr)) {
            if (curFunc == null) {
                return (expr.data as? ArendReferenceContainer)?.let { Pair(-1, it) }
            }
            return Pair(curArgInd, curFunc)
        }
        /*
        if (expr is Concrete.ReferenceExpression || expr is Concrete.HoleExpression) {
            // Rewrite in a less ad-hoc way
            if ((expr.data as? ArendSourceNode)?.topmostEquivalentSourceNode == arg.topmostEquivalentSourceNode ||
                    (expr.data as? ArendSourceNode)?.topmostEquivalentSourceNode?.parentSourceNode?.topmostEquivalentSourceNode == arg.topmostEquivalentSourceNode
                    || (expr.data as? ArendSourceNode)?.parentSourceNode?.parentSourceNode?.topmostEquivalentSourceNode == arg.topmostEquivalentSourceNode
            ) {
                if (curFunc == null) {
                    if (expr is Concrete.ReferenceExpression && resolveIfNeeded(expr.referent, arg.scope) is Abstract.ParametersHolder && expr.data is Abstract.Reference) {
                        return Pair(-1, expr.data as Abstract.Reference)
                    }
                    return null
                }
                return Pair(curArgInd, curFunc)
            }
        } */
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
                        funcReferable?.let { findParamIndex(getAllParametersForReferable(it), argExplicitness) }
                                ?: -1, func)
                if (argRes != null) return argRes
            }

        } else if (expr is Concrete.LamExpression) {
            return findArgInParsedBinopSeq(arg, expr.body, curArgInd, curFunc)
        }

        return null
    }

    private fun locateArg(arg: ArendSourceNode, appExpr: ArendExpr) =
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

    private fun isBinOp(def: Referable?, ref: ArendReferenceContainer): Boolean {
        return ref is ArendIPName && ref.infix != null ||
                def is ArendDefFunction && (def.getPrec()?.infixLeftKw != null || def.getPrec()?.infixNonKw != null || def.getPrec()?.infixRightKw != null)
    }

    private fun ascendLongName(node: Abstract.SourceNode): Abstract.SourceNode? {
        if (node.parentSourceNode is ArendLongName) {
            return node.parentSourceNode
        }
        return node
    }

    private fun ascendToLiteral(node: Abstract.SourceNode): Abstract.SourceNode {
        if (node.parentSourceNode is ArendLiteral) {
            return node.parentSourceNode ?: node
        }
        return node
    }

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
            ((node.parentSourceNode as? ArendLamTele)?.parentSourceNode as? ArendLamExpr)?.let {
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

    public fun extractRefFromSourceNode(node: Abstract.SourceNode): ArendReferenceContainer? {
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
            val params = ref?.let { getAllParametersForReferable(it) }
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

        val arg = toArgument(absNodeParent)
        var argLoc: Pair<Int, ArendReferenceContainer>? = null

        val argParent = arg?.parentSourceNode as? ArendExpr
        if (argParent != null && isBinOpSeq(argParent)) {
            argLoc = (arg.expression as? ArendSourceNode)?.let{ locateArg(it, argParent) }
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
        val absNode = ascendLongName(node) ?: return null
        val res = tryToLocateAtTheCurrentLevel(absNode, isNewArgPos, true)

        if (res != null) {
            return res
        }

        return ascendLambda(absNode)?.parentSourceNode?.let{ tryToLocateAtTheCurrentLevel(it, isNewArgPos, false) }
    }

    public fun findAppExpr(file: PsiFile, offset: Int): Pair<Int, ArendReferenceContainer>? {
        val isNewArgPos = isNewArgumentPosition(file, offset)
        val absNode = skipWhitespaces(file, adjustOffset(file, offset))?.let { PsiTreeUtil.findFirstParent(it) { x -> x is Abstract.SourceNode } as? Abstract.SourceNode } ?: return null

        return ascendTillAppExpr(absNode, isNewArgPos)
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
}