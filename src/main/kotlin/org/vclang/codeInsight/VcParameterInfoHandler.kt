package org.vclang.codeInsight

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.lang.parameterInfo.*
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.jetpad.vclang.naming.reference.Referable
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import com.jetbrains.jetpad.vclang.term.abs.BaseAbstractExpressionVisitor
import com.jetbrains.jetpad.vclang.term.abs.ConcreteBuilder
import org.vclang.psi.VcArgument
import org.vclang.psi.VcArgumentAppExpr
import org.vclang.psi.VcTypeTele
import org.vclang.psi.ext.PsiLocatedReferable

class VcParameterInfoHandler: ParameterInfoHandler<VcArgumentAppExpr, List<Abstract.Parameter>> {
    private var lastAppExpr: VcArgumentAppExpr? = null

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
                vars.mapTo(nameTypeList) { Pair(it.textRepresentation(), ConcreteBuilder.convertExpression(pm.type).toString()) }
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

    override fun findElementForParameterInfo(context: CreateParameterInfoContext): VcArgumentAppExpr? {
        val offset = adjustOffset(context.file, context.editor.caretModel.offset)
        val appExprInfo = findAppExpr(context.file, offset)
        // val curArg = appExprInfo
       // var appExpr = curArg?.let { PsiTreeUtil.getParentOfType(it, VcArgumentAppExpr::class.java) } ?:
       //          ParameterInfoUtils.findParentOfTypeWithStopElements(context.file, adjustOffset(context.file, context.editor.caretModel.offset), VcArgumentAppExpr::class.java, PsiGlobalReferable::class.java) ?: return null
        var appExpr = appExprInfo?.second ?: return null
        var paramsHolder = extractParametersHolder(appExpr)

        if (paramsHolder == null) {
            appExpr = PsiTreeUtil.getParentOfType(appExpr.parent, VcArgumentAppExpr::class.java, true, PsiLocatedReferable::class.java) ?: return null
            paramsHolder = extractParametersHolder(appExpr)
        }

        if (paramsHolder != null) {
            context.itemsToShow = arrayOf(paramsHolder.parameters)
        } else {
            context.itemsToShow = null
        }
        lastAppExpr = appExpr
        return appExpr
    }

    override fun showParameterInfo(element: VcArgumentAppExpr, context: CreateParameterInfoContext) {
        context.showHint(element, element.textRange.startOffset, this)
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

    private fun findAppExpr(file: PsiFile, offset: Int): Pair<VcArgument?, VcArgumentAppExpr>? {
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
        val processReference = lbl@{
            if (absNodeParent is Abstract.Argument) {
                if (absNodeParent.parentSourceNode !is VcArgumentAppExpr) {
                    return@lbl null
                } else {
                    //return@lbl Pair(absNodeParent as? VcArgument, absNodeParent!!.parentSourceNode as VcArgumentAppExpr)
                    return@lbl mbJumpToExternalAppExpr(absNodeParent as? VcArgument, absNodeParent.parentSourceNode as VcArgumentAppExpr)
                }
            } else if (absNodeParent is VcArgumentAppExpr) {
                //return@lbl Pair(null, absNodeParent as VcArgumentAppExpr)
                return@lbl mbJumpToExternalAppExpr(null, absNodeParent as VcArgumentAppExpr)
            }
            return@lbl null
        }

        if (absNode is Abstract.Pattern || absNodeParent is Abstract.Pattern) {
            return null
        }

        if (absNode is VcTypeTele || absNodeParent is VcTypeTele) {
            return null
        }

        if (absNode is Abstract.Argument) {
            if (absNodeParent !is VcArgumentAppExpr) return null
            return Pair(absNode as? VcArgument, absNodeParent)
        }

        while (absNode !is Abstract.Expression) {
            absNode = absNodeParent
            absNodeParent = absNodeParent.parentSourceNode ?: return null
        }

        val defaultRes = if (absNodeParent is VcArgument && absNodeParent.parentSourceNode is VcArgumentAppExpr) {
            Pair(absNodeParent, absNodeParent.parentSourceNode as VcArgumentAppExpr) } else null

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

        }, null)
    }

    override fun findElementForUpdatingParameterInfo(context: UpdateParameterInfoContext): VcArgumentAppExpr? {
        val offset = adjustOffset(context.file, context.editor.caretModel.offset)
        val appExprInfo: Pair<VcArgument?, VcArgumentAppExpr>? = findAppExpr(context.file, offset)
        val curArg = appExprInfo?.first
        val appExpr = appExprInfo?.second ?: return null
        val paramsHolder = extractParametersHolder(appExpr)

        if (appExpr != lastAppExpr) {
            context.setCurrentParameter(-1)
            return null
        }


        val parameters = paramsHolder?.parameters ?: return null
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
        return appExpr
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

    override fun updateParameterInfo(parameterOwner: VcArgumentAppExpr, context: UpdateParameterInfoContext) {

    }

    override fun tracksParameterIndex(): Boolean {
        return false
    }
}