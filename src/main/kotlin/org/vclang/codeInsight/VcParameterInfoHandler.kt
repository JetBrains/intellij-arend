package org.vclang.codeInsight

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.lang.parameterInfo.*
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.jetpad.vclang.error.DummyErrorReporter
import com.jetbrains.jetpad.vclang.naming.BinOpParser
import com.jetbrains.jetpad.vclang.naming.reference.GlobalReferable
import com.jetbrains.jetpad.vclang.naming.reference.Referable
import com.jetbrains.jetpad.vclang.naming.reference.UnresolvedReference
import com.jetbrains.jetpad.vclang.naming.scope.Scope
import com.jetbrains.jetpad.vclang.term.Fixity
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import com.jetbrains.jetpad.vclang.term.abs.BaseAbstractExpressionVisitor
import com.jetbrains.jetpad.vclang.term.abs.ConcreteBuilder
import com.jetbrains.jetpad.vclang.term.concrete.Concrete
import org.vclang.psi.VcArgument
import org.vclang.psi.VcArgumentAppExpr
import org.vclang.psi.VcExpr
import org.vclang.psi.VcTypeTele
import org.vclang.psi.ext.VcSourceNode

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
                vars.mapTo(nameTypeList) {
                    Pair(it?.textRepresentation() ?: "_", ConcreteBuilder.convertExpression(pm.type).toString()) }
            } else {
                nameTypeList.add(Pair("_", ConcreteBuilder.convertExpression(pm.type).toString()))
            }
            for (v in nameTypeList) {
                if (text != "") {
                    text += ", "
                }
                var varText = v.first + ":" + v.second
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
        context.setupUIComponentPresentation(text, hlStart, hlEnd, true, false, false, context.defaultParameterColor)
    }

    override fun findElementForParameterInfo(context: CreateParameterInfoContext): Abstract.Reference? {
        val offset = adjustOffset(context.file, context.editor.caretModel.offset)
        val appExprInfo = findAppExpr(context.file, offset)
        val ref = appExprInfo?.second
        val referable = ref?.referent?.let{ resolveIfNeeded(it, (ref as VcSourceNode).scope) }
        // val curArg = appExprInfo
       // var appExpr = curArg?.let { PsiTreeUtil.getParentOfType(it, VcArgumentAppExpr::class.java) } ?:
       //          ParameterInfoUtils.findParentOfTypeWithStopElements(context.file, adjustOffset(context.file, context.editor.caretModel.offset), VcArgumentAppExpr::class.java, PsiGlobalReferable::class.java) ?: return null
     //   var appExpr = appExprInfo?.second ?: return null
    //    var paramsHolder = extractParametersHolder(appExpr)

    //    if (paramsHolder == null) {
     //       appExpr = PsiTreeUtil.getParentOfType(appExpr.parent, VcArgumentAppExpr::class.java, true, PsiLocatedReferable::class.java) ?: return null
     //       paramsHolder = extractParametersHolder(appExpr)
     //   }

        if (referable is Abstract.ParametersHolder) {
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

    private fun appExprToFunArgs(appExpr: Concrete.AppExpression): Pair<Concrete.Expression, List<Concrete.Argument>> {
        val args = mutableListOf<Concrete.Argument>()
        var expr: Concrete.Expression = appExpr

        while (expr is Concrete.AppExpression) {
            args.add(0, expr.argument)
            expr = expr.function
        }

        return Pair(expr, args)
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
        if (expr is Concrete.ReferenceExpression || expr is Concrete.InferHoleExpression) {
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
            val appData = appExprToFunArgs(expr)
            val funcRes = findArgInParsedBinopSeq(arg, appData.first, curArgInd, curFunc)
            if (funcRes != null) return funcRes
            var func = (appData.first as? Concrete.ReferenceExpression)?.data as? Abstract.Reference
            val funcReferable = func?.referent?.let{ resolveIfNeeded(it, arg.scope)}
            val argExplicitness = mutableListOf<Boolean>()

            if (funcReferable !is Abstract.ParametersHolder) func = null

            for (i in 0 until appData.second.size) {
                argExplicitness.add(appData.second[i].isExplicit)
                val argRes = findArgInParsedBinopSeq(arg, appData.second[i].expression,
                        funcReferable?.let{ findParamIndex(it as Abstract.ParametersHolder, argExplicitness)} ?: -1, func)
                if (argRes != null) return argRes
            }
        } else if (expr is Concrete.LamExpression) {
            return findArgInParsedBinopSeq(arg, expr.body, curArgInd, curFunc)
        }

        return null
    }

    private fun resolveIfNeeded(referent: Referable, scope: Scope): Referable {
        return (referent as? UnresolvedReference)?.resolve(scope) ?: referent
    }

    private fun expressionToReference(expr: Abstract.Expression): Concrete.ReferenceExpression? {
        return expr.accept(object : BaseAbstractExpressionVisitor<Void, Concrete.ReferenceExpression?>(null) {
            override fun visitReference(data: Any?, referent: Referable, lp: Int, lh: Int, params: Void?): Concrete.ReferenceExpression? {
                return Concrete.ReferenceExpression(data, resolveIfNeeded(referent, (expr as VcExpr).scope), Concrete.PLevelExpression(data), Concrete.HLevelExpression(data))
            }

            override fun visitReference(data: Any?, referent: Referable, level1: Abstract.LevelExpression?, level2: Abstract.LevelExpression?, params: Void?): Concrete.ReferenceExpression? {
                return visitReference(data, referent, 0, 0, params)
            }
        }, null)
    }

    private fun locateArg(arg: VcExpr, appExpr: VcExpr): Pair<Int, Abstract.Reference>? {
        return appExpr.accept(object: BaseAbstractExpressionVisitor<Void, Pair<Int, Abstract.Reference>?>(null) {
            override fun visitApp(data: Any?, expr: Abstract.Expression, arguments: MutableCollection<out Abstract.Argument>, params: Void?): Pair<Int, Abstract.Reference>? {
                val argExplicitness = mutableListOf<Boolean>()
                for (arg_ in arguments) {
                    argExplicitness.add(arg_.isExplicit)
                    if (arg_ == arg) break
                }
                return ((expressionToReference(expr))?.data as? Abstract.Reference)?.let {
                    if (it.referent is Abstract.ParametersHolder) Pair(findParamIndex(it.referent as Abstract.ParametersHolder, argExplicitness), it)
                    else null }
            }

            private fun pushExpression(expr: Abstract.Expression?, binopSeq: MutableList<Concrete.BinOpSequenceElem>, fixity: Fixity, isExplicit: Boolean) {
                val ref = expr?.let { expressionToReference(it) }
                if (ref != null && (ref.referent as? GlobalReferable)?.precedence != null) {
                    //parser.push(ref, (ref.referent as? GlobalReferable)?.precedence ?: Precedence.DEFAULT, fixity == Fixity.POSTFIX)
                    binopSeq.add(Concrete.BinOpSequenceElem(ref, fixity, isExplicit))
                } else {
                    //parser.push(Concrete.InferHoleExpression(expr), isExplicit)
                    binopSeq.add(Concrete.BinOpSequenceElem(Concrete.InferHoleExpression(expr), fixity, isExplicit))
                }
            }

            override fun visitBinOpSequence(data: Any?, left: Abstract.Expression, sequence: MutableCollection<out Abstract.BinOpSequenceElem>, params: Void?): Pair<Int, Abstract.Reference>? {
                val parser = BinOpParser(DummyErrorReporter.INSTANCE)
                val concreteSeq = mutableListOf<Concrete.BinOpSequenceElem>()


                pushExpression(left, concreteSeq, Fixity.NONFIX, true)
                for (elem in sequence) {
                    pushExpression(elem.expression, concreteSeq, elem.fixity, elem.isExplicit)
                }

                return findArgInParsedBinopSeq(arg, parser.parse(Concrete.BinOpSequenceExpression(null, concreteSeq)), -1, null)
            }
        }, null)
    }

    private fun findAppExpr(file: PsiFile, offset: Int): Pair<Int, Abstract.Reference>? {
        var absNode = fixedFindElement(file, offset)?.let { PsiTreeUtil.findFirstParent(it, {x -> x is Abstract.SourceNode}) as? Abstract.SourceNode } ?: return null
        var absNodeParent = absNode.parentSourceNode ?: return null
        val mbJumpToExternalAppExpr = lbl_@{arg:VcArgument?, appExpr:VcArgumentAppExpr ->
            if (extractParametersHolder(appExpr) == null) {
                if (arg != null || appExpr.parentSourceNode !is VcArgument || appExpr.parentSourceNode?.parentSourceNode !is VcArgumentAppExpr) {
                    return@lbl_ null
                }
                return@lbl_ Pair(appExpr.parentSourceNode as VcArgument, appExpr.parentSourceNode!!.parentSourceNode as VcArgumentAppExpr)
            }
            return@lbl_ Pair(arg, appExpr)
        }
        /*
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

      //  if (absNode is Abstract.Argument) {
      //      if (absNodeParent !is VcArgumentAppExpr) return null
       //     return Pair(absNode as? VcArgument, absNodeParent)
       // }

        while (absNode !is Abstract.Expression) {
            absNode = absNodeParent
            absNodeParent = absNodeParent.parentSourceNode ?: return null
        }

        //val defaultRes =
        //if (absNodeParent is VcArgument) {
        //    return locateArg(absNodeParent as VcArgument)
       // }
        //else return null

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

            return expressionToReference(absNodeParent)?.let {
                if (it.data is Abstract.Reference) Pair(-1, it.data as Abstract.Reference)
                else null}
        }

        /*
        return (absNode).accept(object : BaseAbstractExpressionVisitor<Void, Pair<VcArgument?, VcArgumentAppExpr>?>(defaultRes) {
            override fun visitApp(data: Any?, expr: Abstract.Expression, arguments: MutableCollection<out Abstract.Argument>, params: Void?): Pair<VcArgument?, VcArgumentAppExpr>? {
                // if (arguments.isEmpty()) return expr.accept(this, params)
                if (absNode !is VcArgumentAppExpr) return null
                /*if (expr.accept(object : BaseAbstractExpressionVisitor<Void, Boolean>(false) {
                    override fun visitReference(data: Any?, referent: Referable, level1: Abstract.LevelExpression?, level2: Abstract.LevelExpression?, params: Void?): Boolean {
                        if (referent is UnresolvedReference) return referent.resolve((absNode as VcArgumentAppExpr).scope) is Abstract.ParametersHolder
                        return referent is Abstract.ParametersHolder
                    }

                    override fun visitReference(data: Any?, referent: Referable, lp: Int, lh: Int, params: Void?): Boolean {
                        if (referent is UnresolvedReference) return referent.resolve((absNode as VcArgumentAppExpr).scope) is Abstract.ParametersHolder
                        return referent is Abstract.ParametersHolder
                    }
                }, null)) {
                    return Pair(null, absNode as VcArgumentAppExpr)
                } else if (absNodeParent is Abstract.Argument && arguments.isEmpty()) {
                    if (absNodeParent?.parentSourceNode !is VcArgumentAppExpr) return null
                    return Pair(absNodeParent as? VcArgument, absNodeParent!!.parentSourceNode as VcArgumentAppExpr)
                } */
                return mbJumpToExternalAppExpr(null, absNode as VcArgumentAppExpr)
            }

            override fun visitReference(data: Any?, referent: Referable, lp: Int, lh: Int, params: Void?): Pair<VcArgument?, VcArgumentAppExpr>? {
                return processReference()
            }

            override fun visitReference(data: Any?, referent: Referable, level1: Abstract.LevelExpression?, level2: Abstract.LevelExpression?, params: Void?): Pair<VcArgument?, VcArgumentAppExpr>? {
                return visitReference(data, referent, 0, 0, params)
            }

            override fun visitBinOpSequence(data: Any?, left: Abstract.Expression, sequence: MutableCollection<out Abstract.BinOpSequenceElem>, params: Void?): Pair<VcArgument?, VcArgumentAppExpr>? {

                return visitApp(data, left, sequence.filter { it is Abstract.Argument }.map { it as Abstract.Argument }.toMutableList(), null)
            }

        }, null) */
        return null
    }

    override fun findElementForUpdatingParameterInfo(context: UpdateParameterInfoContext): Abstract.Reference? {
        val offset = adjustOffset(context.file, context.editor.caretModel.offset)
        val appExprInfo: Pair<Int, Abstract.Reference> = findAppExpr(context.file, offset) ?: return null
//        val curArg = appExprInfo?.first
//        val appExpr = appExprInfo?.second ?: return null
       // val paramsHolder = appExprInfo.second//extractParametersHolder(appExpr)

       // if (appExpr != lastAppExpr) {
       //     context.setCurrentParameter(-1)
        //    return null
       // }


        //val parameters = paramsHolder.parameters ?: return null
        /*
        val argIndex = // ParameterInfoUtils.getCurrentParameterIndex(appExpr.node, context.offset, TokenType.WHITE_SPACE)
                 appExpr.argumentList.indexOf(curArg)
        if (argIndex >= 0) {
            val argIsExplicit = appExpr.argumentList[argIndex].isExplicit
            var numExplicitsBefore = 0
            var numImplicitsJustBefore = 0
            for (i in 0 until argIndex) {
                if (appExpr.argumentList[i].isExplicit) {
                    ++numExplicitsBefore
                    numImplicitsJustBefore = 0
                } else {
                    ++numImplicitsJustBefore
                }
            }
            var paramIndex = 0
            loop@for (p in 0 until parameters.size) {
                for (v in parameters[p].referableList) {
                    if (numExplicitsBefore == 0) {
                        if ((argIsExplicit && parameters[p].isExplicit) ||
                                (!argIsExplicit && numImplicitsJustBefore == 0)) {
                            break@loop
                        }
                        --numImplicitsJustBefore
                    } else if (parameters[p].isExplicit) {
                        --numExplicitsBefore
                    }
                    ++paramIndex
                }
            }
            if (numExplicitsBefore == 0 && numImplicitsJustBefore <= 0) {
                context.setCurrentParameter(paramIndex)
            } else {
                context.setCurrentParameter(-1)
            }
        } else {
            context.setCurrentParameter(-1)
        }
        return appExpr */
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