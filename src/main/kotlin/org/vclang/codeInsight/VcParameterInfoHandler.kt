package org.vclang.codeInsight

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.lang.parameterInfo.*
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.jetpad.vclang.naming.reference.GlobalReferable
import com.jetbrains.jetpad.vclang.naming.reference.Referable
import com.jetbrains.jetpad.vclang.naming.reference.UnresolvedReference
import com.jetbrains.jetpad.vclang.naming.reference.converter.IdReferableConverter
import com.jetbrains.jetpad.vclang.naming.scope.Scope
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import com.jetbrains.jetpad.vclang.term.abs.BaseAbstractExpressionVisitor
import com.jetbrains.jetpad.vclang.term.abs.ConcreteBuilder
import com.jetbrains.jetpad.vclang.term.concrete.Concrete
import org.vclang.psi.VcArgument
import org.vclang.psi.VcArgumentAppExpr
import org.vclang.psi.VcExpr
import org.vclang.psi.VcTypeTele
import org.vclang.psi.ext.PsiLocatedReferable
import org.vclang.psi.ext.VcSourceNode
import org.vclang.typing.parseBinOp

class VcParameterInfoHandler: ParameterInfoHandler<Abstract.Reference, List<Abstract.Parameter>> {
    // private var lastAppExpr: VcArgumentAppExpr? = null

    override fun getParameterCloseChars(): String? {
        return ParameterInfoUtils.DEFAULT_PARAMETER_CLOSE_CHARS
    }

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

    override fun findElementForParameterInfo(context: CreateParameterInfoContext): Abstract.Reference? {
        val offset = adjustOffset(context.file, context.editor.caretModel.offset)
        val appExprInfo = findAppExpr(context.file, offset)
        val ref = appExprInfo?.second
        val referable = ref?.referent?.let{ resolveIfNeeded(it, (ref as VcSourceNode).scope) }

        if (referable is Abstract.ParametersHolder && !referable.parameters.isEmpty()) {
            context.itemsToShow = arrayOf((referable as Abstract.ParametersHolder).parameters)
        } else {
            context.itemsToShow = null
        }
        //lastAppExpr = appExprInfo?.second
        return appExprInfo?.second
    }

    override fun showParameterInfo(element: Abstract.Reference, context: CreateParameterInfoContext) {
        if (element is PsiElement) {
            context.showHint(element, element.textRange.startOffset, this)
        }
    }

    override fun getParametersForDocumentation(p: List<Abstract.Parameter>?, context: ParameterInfoContext?): Array<Any>? {
        return null
    }

    override fun getParametersForLookup(item: LookupElement?, context: ParameterInfoContext?): Array<Any>? {
        return null
    }

    override fun couldShowInLookup(): Boolean {
        return true
    }

    private fun fixedFindElement(file: PsiFile, offset: Int): PsiElement? {
        val elem: PsiElement = file.findElementAt(offset) ?: return null

        if (elem is PsiWhiteSpace) {
            if (elem.textOffset != offset) { //context.editor.caretModel.offset) {
                // return PsiTreeUtil.getParentOfType(PsiTreeUtil.nextLeaf(arg), VcArgument::class.java, true, VcArgumentAppExpr::class.java)
                return PsiTreeUtil.nextLeaf(elem)
            } else {
                // return PsiTreeUtil.getParentOfType(PsiTreeUtil.prevLeaf(arg), VcArgument::class.java, true, VcArgumentAppExpr::class.java)
                return PsiTreeUtil.prevLeaf(elem)
            }
        }

        return elem
    }

    private fun adjustOffset(file: PsiFile, offset: Int): Int {
        val element = file.findElementAt(offset)
        if (element?.text == ")" || element?.text == "}") {
            return offset - 1
        }
        return offset
    }

    private fun findParamIndex(func: Abstract.ParametersHolder, argsExplicitness: List<Boolean>): Int {
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
        loop@for (p in 0 until func.parameters.size) {
            for (v in func.parameters[p].referableList) {
                if (numExplicitsBefore == 0) {
                    if ((argIsExplicit && func.parameters[p].isExplicit) ||
                            (!argIsExplicit && numImplicitsJustBefore == 0)) {
                        break@loop
                    }
                    --numImplicitsJustBefore
                } else if (func.parameters[p].isExplicit) {
                    --numExplicitsBefore
                }
                ++paramIndex
            }
        }
        if (numExplicitsBefore == 0 && numImplicitsJustBefore <= 0) {
            return paramIndex
        } else {
            return -1
        }
    }

    private fun findArgInParsedBinopSeq(arg: VcExpr, expr: Concrete.Expression, curArgInd: Int, curFunc: Abstract.Reference?): Pair<Int, Abstract.Reference>? {
        if (expr is Concrete.ReferenceExpression || expr is Concrete.HoleExpression) {
            if ((expr.data as? VcSourceNode)?.topmostEquivalentSourceNode == arg.topmostEquivalentSourceNode ||
                    (expr.data as? VcSourceNode)?.parentSourceNode?.topmostEquivalentSourceNode == arg.topmostEquivalentSourceNode) {
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
                    funcReferable?.let{ findParamIndex(it as Abstract.ParametersHolder, argExplicitness)} ?: -1, func)
                if (argRes != null) return argRes
            }
        } else if (expr is Concrete.LamExpression) {
            return findArgInParsedBinopSeq(arg, expr.body, curArgInd, curFunc)
        }

        return null
    }

    private fun resolveIfNeeded(referent: Referable, scope: Scope): Referable? =
        (((referent as? UnresolvedReference)?.resolve(scope) ?: referent) as? GlobalReferable)?.let { PsiLocatedReferable.fromReferable(it) }

    private fun expressionToReference(expr: Abstract.Expression): Abstract.Reference? {
        return expr.accept(object : BaseAbstractExpressionVisitor<Void, Abstract.Reference?>(null) {
            override fun visitReference(data: Any?, referent: Referable, lp: Int, lh: Int, errorData: Abstract.ErrorData?, params: Void?): Abstract.Reference? =
                data as? Abstract.Reference

            override fun visitReference(data: Any?, referent: Referable, level1: Abstract.LevelExpression?, level2: Abstract.LevelExpression?, errorData: Abstract.ErrorData?, params: Void?): Abstract.Reference? =
                data as? Abstract.Reference
        }, null)
    }

    private fun locateArg(arg: VcExpr, appExpr: VcExpr): Pair<Int, Abstract.Reference>? {
        return appExpr.accept(object: BaseAbstractExpressionVisitor<Void, Pair<Int, Abstract.Reference>?>(null) {
            override fun visitApp(data: Any?, expr: Abstract.Expression, arguments: MutableCollection<out Abstract.Argument>, errorData: Abstract.ErrorData?, params: Void?): Pair<Int, Abstract.Reference>? {
                val argExplicitness = mutableListOf<Boolean>()
                for (arg_ in arguments) {
                    argExplicitness.add(arg_.isExplicit)
                    if (arg_ == arg) break
                }

                val reference = expressionToReference(expr) ?: return null
                return (reference.referent as? Abstract.ParametersHolder)?.let { Pair(findParamIndex(it, argExplicitness), reference) }
            }

            override fun visitBinOpSequence(data: Any?, left: Abstract.Expression, sequence: Collection<Abstract.BinOpSequenceElem>, errorData: Abstract.ErrorData?, params: Void?): Pair<Int, Abstract.Reference>? =
                findArgInParsedBinopSeq(arg, parseBinOp(left, sequence), -1, null)
        }, null)
    }

    private fun findAppExpr(file: PsiFile, offset: Int): Pair<Int, Abstract.Reference>? {
        var absNode = fixedFindElement(file, offset)?.let { PsiTreeUtil.findFirstParent(it, {x -> x is Abstract.SourceNode}) as? Abstract.SourceNode } ?: return null
        var absNodeParent = absNode.parentSourceNode ?: return null
        /*
        val mbJumpToExternalAppExpr = lbl_@{arg:VcArgument?, appExpr:VcArgumentAppExpr ->
            if (extractParametersHolder(appExpr) == null) {
                if (arg != null || appExpr.parentSourceNode !is VcArgument || appExpr.parentSourceNode?.parentSourceNode !is VcArgumentAppExpr) {
                    return@lbl_ null
                }
                return@lbl_ Pair(appExpr.parentSourceNode as VcArgument, appExpr.parentSourceNode!!.parentSourceNode as VcArgumentAppExpr)
            }
            return@lbl_ Pair(arg, appExpr)
        }

        val processReference = lbl@{
            if (absNodeParent is VcArgument) {
                val argLoc = locateArg(absNodeParent as VcArgument)
                if (argLoc == null && absNodeParent.parentSourceNode?.parentSourceNode is VcArgument) {
                    return@lbl locateArg(absNodeParent.parentSourceNode?.parentSourceNode as VcArgument)
                }
                return@lbl argLoc
                /*if (absNodeParent.parentSourceNode !is VcArgumentAppExpr) {
                    return@lbl null
                } else {
                    //return@lbl Pair(absNodeParent as? VcArgument, absNodeParent!!.parentSourceNode as VcArgumentAppExpr)
                    return@lbl mbJumpToExternalAppExpr(absNodeParent as? VcArgument, absNodeParent.parentSourceNode as VcArgumentAppExpr)
                } */
            } else if (absNodeParent is VcArgumentAppExpr) {
                //return@lbl Pair(null, absNodeParent as VcArgumentAppExpr)
                if (absNodeParent.parentSourceNode is VcArgument) {
                    return@lbl locateArg(absNodeParent.parentSourceNode as VcArgument)
                }

                return@lbl extractParametersHolder(absNodeParent as VcArgumentAppExpr)?.let{ Pair(null, it)}
                //mbJumpToExternalAppExpr(null, absNodeParent as VcArgumentAppExpr)
            }
            return@lbl null
        }*/

        if (absNode is Abstract.Pattern || absNodeParent is Abstract.Pattern) {
            return null
        }

        if (absNode is VcTypeTele || absNodeParent is VcTypeTele) {
            return null
        }

        while (absNode !is Abstract.Expression) {
            absNode = absNodeParent
            absNodeParent = absNodeParent.parentSourceNode ?: return null
        }

        if (absNodeParent is VcArgument && absNodeParent.parentSourceNode is VcExpr) {
            var arg: VcArgument = absNodeParent
            val argLoc = arg.expression?.let { locateArg(it as VcExpr, absNodeParent.parentSourceNode as VcExpr) }
            if (argLoc == null && absNodeParent.parentSourceNode?.parentSourceNode is VcArgument) {
                arg = absNodeParent.parentSourceNode?.parentSourceNode as VcArgument
                return arg.expression?.let{ locateArg(it as VcExpr, absNodeParent.parentSourceNode as VcExpr) }
            }
            return argLoc
        } else if (absNodeParent is VcArgumentAppExpr) {
            val argLoc = locateArg(absNode as VcExpr, absNodeParent)
            if (argLoc != null) return argLoc

            if (absNodeParent.parentSourceNode is VcArgument && absNodeParent.parentSourceNode?.parentSourceNode is VcExpr) {
                val arg: VcArgument = absNodeParent.parentSourceNode as VcArgument
                return arg.expression?.let { locateArg(it as VcExpr, absNodeParent.parentSourceNode?.parentSourceNode as VcExpr) }
            }

            return expressionToReference(absNodeParent)?.let { Pair(-1, it) }
        }

        return null
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

    private fun extractParametersHolder(appExpr: VcArgumentAppExpr): Abstract.ParametersHolder? {
        val longName = appExpr.longName ?: appExpr.atomFieldsAcc?.atom?.literal?.longName
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

    override fun tracksParameterIndex(): Boolean {
        return false
    }
}