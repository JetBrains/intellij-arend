package org.arend.codeInsight

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.lang.parameterInfo.*
import com.intellij.openapi.project.ProjectManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentsOfType
import org.arend.naming.reference.GlobalReferable
import org.arend.naming.reference.Referable
import org.arend.naming.reference.converter.IdReferableConverter
import org.arend.naming.resolving.visitor.ExpressionResolveNameVisitor
import org.arend.naming.scope.Scope
import org.arend.psi.*
import org.arend.psi.ext.ArendSourceNode
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.ext.impl.ClassFieldAdapter
import org.arend.psi.ext.impl.FunctionDefinitionAdapter
import org.arend.term.abs.Abstract
import org.arend.term.abs.BaseAbstractExpressionVisitor
import org.arend.term.abs.ConcreteBuilder
import org.arend.term.concrete.Concrete
import org.arend.typing.parseBinOp

class ArendParameterInfoHandler: ParameterInfoHandler<Abstract.Reference, List<Abstract.Parameter>> {
    // private var lastAppExpr: ArendArgumentAppExpr? = null

    override fun updateUI(p: List<Abstract.Parameter>?, context: ParameterInfoUIContext) {
        //val types = p?.map { Array(it.referableList.size, {_ -> ConcreteBuilder.convertExpression(it.type).toString()}) }?.toTypedArray()?.flatten()
        if (p == null) return
        // val params = ConcreteBuilder.convertParams(p)
        var curOffset = 0
        var text = ""
        var hlStart = -1; var hlEnd = -1
        var ind = 0
        for (pm in p) {
            // val tele = if (pm is Concrete.TelescopeParameter) Array(pm.referableList.size, {_ -> pm.type}) else arrayOf(pm)
            val nameTypeList = mutableListOf<Pair<String?, String>>()
            val vars = pm.referableList
            if (!vars.isEmpty()) {

//                vars.mapTo(nameTypeList) {
 //                   Pair(it?.textRepresentation() ?: "_", ConcreteBuilder.convertExpression(pm.type).toString()) }

                vars.mapTo(nameTypeList) { Pair(it?.textRepresentation() ?: "_", ConcreteBuilder.convertExpression(IdReferableConverter.INSTANCE, pm.type).toString()) }
            } else {
                nameTypeList.add(Pair("_", ConcreteBuilder.convertExpression(IdReferableConverter.INSTANCE, pm.type).toString()))
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
                if (ind == context.currentParameterIndex) {
                    hlStart = curOffset
                    hlEnd = text.length + 1
                }
                curOffset = text.length + 2
                ++ind
            }
        }
        context.setupUIComponentPresentation(text, hlStart, hlEnd, !context.isUIComponentEnabled, false, false, context.defaultParameterColor)
    }

    private fun getAllParametersForReferable(def: Referable): List<Abstract.Parameter> {
        val params = mutableListOf<Abstract.Parameter>()
        if (def is Abstract.ParametersHolder) {
            params.addAll(def.parameters)
            var resType: ArendExpr? = when(def) {
                is ClassFieldAdapter -> def.resultType
                is FunctionDefinitionAdapter -> def.resultType
                else -> null
            }
            while (resType != null) {
                resType = when(resType) {
                    is ArendArrExpr -> {
                        params.add(ArendPsiFactory(ProjectManager.getInstance().openProjects.first()).createNameTele(null,
                                ConcreteBuilder.convertExpression(IdReferableConverter.INSTANCE, resType.exprList.first()).toString()))
                        resType.exprList[1]
                    }
                    is ArendPiExpr -> {
                        params.addAll(resType.typeTeleList)
                        resType.expr
                    }
                    else -> null
                }
            }
        }
        return params
    }

    override fun findElementForParameterInfo(context: CreateParameterInfoContext): Abstract.Reference? {
        val offset = context.editor.caretModel.offset //adjustOffset(context.file, context.editor.caretModel.offset)
        val appExprInfo = findAppExpr(context.file, offset)
        val ref = appExprInfo?.second
        val referable = ref?.referent?.let{ resolveIfNeeded(it, (ref as ArendSourceNode).scope) }
        val params = referable?.let { getAllParametersForReferable(it) }

        if (params != null && !params.isEmpty()) {
            context.itemsToShow = arrayOf(params) //(referable as Abstract.ParametersHolder).parameters)
        } else {
            context.itemsToShow = null
        }

        return appExprInfo?.second
    }

    override fun showParameterInfo(element: Abstract.Reference, context: CreateParameterInfoContext) {
        if (element is PsiElement) {
            context.showHint(element, element.textRange.startOffset, this)
        }
    }

    override fun getParametersForLookup(item: LookupElement?, context: ParameterInfoContext?): Array<Any>? {
        return null
    }

    override fun couldShowInLookup(): Boolean {
        return true
    }

    /*
    private fun fixedFindElement(file: PsiFile, offset: Int): PsiElement? {
        var elem: PsiElement? = file.findElementAt(adjustOffset(file, offset))
        //var shiftedOffset = offset + 1

        //while (shiftedOffset >= 0 && elem == null) {
        //    --shiftedOffset
        //    elem = file.findElementAt(shiftedOffset)
       // }

        //if (elem == null) return null

        if (elem is PsiWhiteSpace && elem.prevSibling !is PsiWhiteSpace) {
            return elem.prevSibling
        }

        while (elem is PsiWhiteSpace) {
            elem = elem.nextSibling
            /*
            if (elem.textOffset != shiftedOffset) { //context.editor.caretModel.offset) {
                // return PsiTreeUtil.getParentOfType(PsiTreeUtil.nextLeaf(arg), ArendArgument::class.java, true, ArendArgumentAppExpr::class.java)
                return PsiTreeUtil.nextLeaf(elem)
            } else {
                // return PsiTreeUtil.getParentOfType(PsiTreeUtil.prevLeaf(arg), ArendArgument::class.java, true, ArendArgumentAppExpr::class.java)
                return PsiTreeUtil.prevLeaf(elem)
            }*/
        }

        return elem
    } */

    private fun adjustOffset(file: PsiFile, offset: Int): Int {
        val element = file.findElementAt(offset)

        if (element?.text == ")" || element?.text == "}") {
            return offset - 1
        }

        return offset
    }

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

    private fun findParamIndex(params: List<Abstract.Parameter>, argsExplicitness: List<Boolean>): Int {
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
        loop@for (p in 0 until params.size) {
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

    private fun findArgInParsedBinopSeq(arg: ArendExpr, expr: Concrete.Expression, curArgInd: Int, curFunc: Abstract.Reference?): Pair<Int, Abstract.Reference>? {
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
        }
        if (expr is Concrete.AppExpression) {
            val funcRes = findArgInParsedBinopSeq(arg, expr.function, curArgInd, curFunc)
            if (funcRes != null) return funcRes
            var func = (expr.function as? Concrete.ReferenceExpression)?.data as? Abstract.Reference
            var funcReferable = func?.referent?.let{ resolveIfNeeded(it, arg.scope)}
            val argExplicitness = mutableListOf<Boolean>()

            if (funcReferable !is Abstract.ParametersHolder) {
                func = null
                funcReferable = null
            }

            for (argument in expr.arguments) {
                argExplicitness.add(argument.isExplicit)
                val argRes = findArgInParsedBinopSeq(arg, argument.expression,
                    funcReferable?.let{ findParamIndex(getAllParametersForReferable(it), argExplicitness)} ?: -1, func)
                if (argRes != null) return argRes
            }
        } else if (expr is Concrete.LamExpression) {
            return findArgInParsedBinopSeq(arg, expr.body, curArgInd, curFunc)
        }

        return null
    }

    private fun resolveIfNeeded(referent: Referable, scope: Scope): Referable? =
        (ExpressionResolveNameVisitor.resolve(referent, scope) as? GlobalReferable)?.let { PsiLocatedReferable.fromReferable(it) }

    private fun locateArg(arg: ArendExpr, appExpr: ArendExpr) =
        appExpr.accept(object: BaseAbstractExpressionVisitor<Void, Pair<Int, Abstract.Reference>?>(null) {
            override fun visitBinOpSequence(data: Any?, left: Abstract.Expression, sequence: Collection<Abstract.BinOpSequenceElem>, errorData: Abstract.ErrorData?, params: Void?): Pair<Int, Abstract.Reference>? =
                findArgInParsedBinopSeq(arg, parseBinOp(left, sequence), -1, null)
        }, null)

    private fun isNewArgumentPosition(file: PsiFile, offset: Int): Boolean {
        val element: PsiElement = file.findElementAt(offset) ?: return file.findElementAt(offset - 1) is PsiWhiteSpace

        return (element is PsiWhiteSpace || element.text == ")" || element.text == "}") && (file.findElementAt(offset - 1) is PsiWhiteSpace)
    }

    private fun isBinOpSeq(expr: ArendExpr): Boolean =
        expr.accept(object: BaseAbstractExpressionVisitor<Void, Boolean>(false) {
            override fun visitBinOpSequence(data: Any?, left: Abstract.Expression, sequence: Collection<Abstract.BinOpSequenceElem>, errorData: Abstract.ErrorData?, params: Void?): Boolean =
                    true
        }, null)

    private fun isBinOp(def: Referable?): Boolean {
        return def is ArendDefFunction && (def.prec?.infixLeftKw != null || def.prec?.infixNonKw != null || def.prec?.infixRightKw != null)
    }

    private fun ascendTillAppExpr(node: Abstract.SourceNode, isNewArgPos: Boolean): Pair<Int, Abstract.Reference>? {
        var absNode = node
        var absNodeParent = absNode.parentSourceNode

        while (absNodeParent != null) {
            if (absNode is Abstract.Reference) {
                val ref = absNode.referent.let{ resolveIfNeeded(it, (absNode as ArendSourceNode).scope) }
                val params = ref?.let { getAllParametersForReferable(it) }
                if (params != null && !params.isEmpty()) {//ref is Abstract.ParametersHolder && !ref.parameters.isEmpty()) {
                    val isBinOp = isBinOp(ref)
                    val parentAppExprCandidate = absNodeParent.parentSourceNode?.parentSourceNode
                    if (parentAppExprCandidate is ArendExpr) {
                        if (isBinOpSeq(parentAppExprCandidate) && !isNewArgPos && !isBinOp) {
                            return (absNode as? PsiElement)?.parentOfType<ArendExpr>(false)?.let{ locateArg(it, parentAppExprCandidate) }
                            //(absNodeParent as Abstract.SourceNode).parentSourceNode as ArendExpr) }
                            //absNode = absNodeParent
                            //absNodeParent = absNodeParent.parentSourceNode
                            //continue
                        }
                    }
                    if (isNewArgPos) {
                       // if (absNodeParent.parentSourceNode?.parentSourceNode is ArendExpr && isBinOpSeq(absNodeParent.parentSourceNode?.parentSourceNode as ArendExpr))
                        //    return Pair(1, absNode)
                        if (!isBinOp)
                            return Pair(0, absNode)
                        return Pair(1, absNode)
                    }
                    return Pair(-1, absNode)
                }
            } else if (absNodeParent is ArendArgument && absNodeParent.parentSourceNode is ArendExpr ||
                    absNodeParent is ArendExpr && isBinOpSeq(absNodeParent)) {
                val arg: ArendExpr = if (absNodeParent is ArendArgument) absNodeParent.expression as ArendExpr else absNodeParent as ArendExpr
                val argLoc =  if (absNodeParent is ArendArgument) locateArg(arg, (absNodeParent).parentSourceNode as ArendExpr)
                                else locateArg(arg, absNodeParent as ArendExpr)

                if (argLoc != null) {
                    if (isNewArgPos) return Pair(argLoc.first + 1, argLoc.second)
                    return argLoc
                }
            }
            absNode = absNodeParent
            absNodeParent = absNodeParent.parentSourceNode
        }

        return null
    }

    private fun findAppExpr(file: PsiFile, offset: Int): Pair<Int, Abstract.Reference>? {
        val isNewArgPos = isNewArgumentPosition(file, offset)
        val absNode = skipWhitespaces(file, adjustOffset(file, offset))?.let { PsiTreeUtil.findFirstParent(it) { x -> x is Abstract.SourceNode } as? Abstract.SourceNode } ?: return null
        /*
        var absNodeParent = absNode.parentSourceNode ?: return null

        //if (absNode is Abstract.Pattern || absNodeParent is Abstract.Pattern) {
         //   return null
       // }

       // if (absNode is ArendTypeTele || absNodeParent is ArendTypeTele) {
       //     return null
       // }

        while (absNode !is Abstract.Expression) {
            absNode = absNodeParent
            absNodeParent = absNodeParent.parentSourceNode ?: return null
        }

        if (absNodeParent is ArendArgument && absNodeParent.parentSourceNode is ArendExpr) {
            var arg: ArendArgument = absNodeParent
            val argLoc = arg.expression?.let { locateArg(it as ArendExpr, absNodeParent.parentSourceNode as ArendExpr) }

            if (argLoc == null && absNodeParent.parentSourceNode?.parentSourceNode is ArendArgument) {
                arg = absNodeParent.parentSourceNode?.parentSourceNode as ArendArgument
                return arg.expression?.let{ locateArg(it as ArendExpr, absNodeParent.parentSourceNode as ArendExpr) }
            }

            return argLoc
        } else if (absNodeParent is ArendArgumentAppExpr) {
            val argLoc = locateArg(absNode as ArendExpr, absNodeParent)
            if (argLoc != null) return argLoc

            if (absNodeParent.parentSourceNode is ArendArgument && absNodeParent.parentSourceNode?.parentSourceNode is ArendExpr) {
                val arg: ArendArgument = absNodeParent.parentSourceNode as ArendArgument
                return arg.expression?.let { locateArg(it as ArendExpr, absNodeParent.parentSourceNode?.parentSourceNode as ArendExpr) }
            }

            return expressionToReference(absNodeParent)?.let { Pair(-1, it) }
        } */

        return ascendTillAppExpr(absNode, isNewArgPos)
    }

    override fun findElementForUpdatingParameterInfo(context: UpdateParameterInfoContext): Abstract.Reference? {
        val offset = adjustOffset(context.file, context.editor.caretModel.offset)
        val appExprInfo: Pair<Int, Abstract.Reference> = findAppExpr(context.file, offset) ?: return null

        if (context.parameterOwner != appExprInfo.second) {
            return null
        }

        context.setCurrentParameter(appExprInfo.first)
        return appExprInfo.second
    }

    private fun extractParametersHolder(appExpr: ArendArgumentAppExpr): Abstract.ParametersHolder? {
        val longName = appExpr.longNameExpr?.longName ?: appExpr.atomFieldsAcc?.atom?.literal?.longName
        if (longName != null && longName.headReference != null) {
            val ref = longName.refIdentifierList.lastOrNull()?.reference?.resolve()
            if (ref != null && ref is Abstract.ParametersHolder) {
                return ref
            }
        }
        return null
    }

    override fun updateParameterInfo(parameterOwner: Abstract.Reference, context: UpdateParameterInfoContext) {

    }
}