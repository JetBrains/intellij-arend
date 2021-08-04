package org.arend.util

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.arend.naming.reference.Referable
import org.arend.psi.ArendExpr
import org.arend.psi.ArendIPName
import org.arend.psi.ArendImplicitArgument
import org.arend.psi.ext.ArendReferenceContainer
import org.arend.psi.ext.ArendSourceNode
import org.arend.term.Fixity
import org.arend.term.abs.Abstract
import org.arend.term.abs.BaseAbstractExpressionVisitor
import org.arend.term.concrete.Concrete
import org.arend.resolving.util.parseBinOp
import org.arend.resolving.util.resolveReference

fun appExprToConcrete(appExpr: Abstract.Expression): Concrete.Expression? = appExprToConcrete(appExpr, false)

fun appExprToConcrete(appExpr: Abstract.Expression, setData: Boolean): Concrete.Expression? =
        appExpr.accept(object : BaseAbstractExpressionVisitor<Void, Concrete.Expression>(null) {
            override fun visitBinOpSequence(data: Any?, left: Abstract.Expression, sequence: Collection<Abstract.BinOpSequenceElem>, params: Void?): Concrete.Expression =
                    parseBinOp(if (setData) data else null, left, sequence)
            override fun visitReference(data: Any?, referent: Referable, lp: Int, lh: Int, params: Void?) =
                    resolveReference(data, referent, null)
            override fun visitReference(data: Any?, referent: Referable, fixity: Fixity?, level1: Abstract.LevelExpression?, level2: Abstract.LevelExpression?, params: Void?) =
                    resolveReference(data, referent, fixity)
        }, null)

fun getBounds(cExpr: Concrete.Expression, aaeBlocks: List<ASTNode>): TextRange? {
    val cExprData = cExpr.data
    if (cExpr is Concrete.AppExpression) {
        val elements = ArrayList<TextRange>()
        val fData = cExpr.function.data

        elements.addAll(cExpr.arguments.asSequence().mapNotNull { getBounds(it.expression, aaeBlocks) })

        if (fData is PsiElement) {
            val f = aaeBlocks.filter { it.textRange.contains(fData.textRange) }
            if (f.size != 1) throw IllegalStateException()
            elements.add(f.first().textRange)
        }

        val startOffset = elements.asSequence().map { it.startOffset }.minOrNull()
        val endOffset = elements.asSequence().map { it.endOffset }.maxOrNull()
        if (startOffset != null && endOffset != null) {
            return TextRange.create(startOffset, endOffset)
        }
    } else if (cExprData is PsiElement) {
        for (psi in aaeBlocks) if (psi.textRange.contains(cExprData.node.textRange)) {
            return psi.textRange
        }
    }
    return null
}

fun concreteDataToSourceNode(data: Any?): ArendSourceNode? = if (data is ArendIPName) (data.infix
        ?: data.postfix)?.parentOfType() else data as? ArendSourceNode

/*fun checkConcreteExprIsArendExpr(aExpr: ArendExpr, cExpr: Concrete.Expression): Boolean {
    val checkConcreteExprDataIsArendNode = { cData: ArendSourceNode?, aNode: ArendSourceNode -> // Rewrite in a less ad-hoc way
        cData?.topmostEquivalentSourceNode == aNode.topmostEquivalentSourceNode ||
                cData?.topmostEquivalentSourceNode?.parentSourceNode?.topmostEquivalentSourceNode == aNode.topmostEquivalentSourceNode ||
                cData?.parentSourceNode?.parentSourceNode?.topmostEquivalentSourceNode == aNode.topmostEquivalentSourceNode }

    if (cExpr is Concrete.AppExpression) return false

    return checkConcreteExprDataIsArendNode(concreteDataToSourceNode(cExpr.data), aExpr)
} */

fun checkConcreteExprIsArendExpr(aExpr: ArendSourceNode, cExpr: Concrete.Expression): Boolean {
    val checkConcreteExprDataIsArendNode = ret@{ cData: ArendSourceNode?, aNode: ArendSourceNode ->
        // Rewrite in a less ad-hoc way
        if (cData?.topmostEquivalentSourceNode == aNode.topmostEquivalentSourceNode ||
                cData?.topmostEquivalentSourceNode?.parentSourceNode?.topmostEquivalentSourceNode == aNode.topmostEquivalentSourceNode
                || cData?.parentSourceNode?.parentSourceNode?.topmostEquivalentSourceNode == aNode.topmostEquivalentSourceNode
        ) {
            return@ret true
        }
        return@ret false
    }
    if (cExpr is Concrete.AppExpression) {
        return false
    }
    if (aExpr is ArendImplicitArgument) {
        val expr = aExpr.tupleExprList.firstOrNull()?.exprList?.lastOrNull() ?: return false
        return checkConcreteExprDataIsArendNode(concreteDataToSourceNode(cExpr.data), expr)
    }
    return checkConcreteExprDataIsArendNode(concreteDataToSourceNode(cExpr.data), aExpr)
}

private fun getReferenceContainer(expr: Concrete.Expression) = (expr as? Concrete.ReferenceExpression)?.data as? ArendReferenceContainer

data class DefAndArgsInParsedBinopResult(val functionReferenceContainer: ArendReferenceContainer,
                                         val operatorConcrete: Concrete.Expression,
                                         val argumentsConcrete: List<Concrete.Argument>)

fun findDefAndArgsInParsedBinop(arg: ArendExpr, parsedExpr: Concrete.Expression): DefAndArgsInParsedBinopResult? {
    if (checkConcreteExprIsArendExpr(arg, parsedExpr)) {
        getReferenceContainer(parsedExpr)?.let {
            return DefAndArgsInParsedBinopResult(it, parsedExpr, emptyList())
        }
    }

    if (parsedExpr is Concrete.AppExpression) {
        if (checkConcreteExprIsArendExpr(arg, parsedExpr.function)) {
            getReferenceContainer(parsedExpr.function)?.let {
                return DefAndArgsInParsedBinopResult(it, parsedExpr, parsedExpr.arguments)
            }
        }

        findDefAndArgsInParsedBinop(arg, parsedExpr.function)?.let { return it }

        for (argument in parsedExpr.arguments) {
            if (checkConcreteExprIsArendExpr(arg, argument.expression)) {
                getReferenceContainer(argument.expression)?.let {
                    return DefAndArgsInParsedBinopResult(it, argument.expression, emptyList())
                }
                return getReferenceContainer(parsedExpr.function)?.let { DefAndArgsInParsedBinopResult(it, parsedExpr, parsedExpr.arguments) }
            }
        }

        for (argument in parsedExpr.arguments)
            findDefAndArgsInParsedBinop(arg, argument.expression)?.let { return it }

    } else if (parsedExpr is Concrete.LamExpression)
        return findDefAndArgsInParsedBinop(arg, parsedExpr.body)

    return null
}

/*
// The second component of the Pair in the return type is a list of (argument, isExplicit)
fun findDefAndArgsInParsedBinop(arg: ArendExpr, parsedExpr: Concrete.Expression): Pair<Abstract.Reference, List<Pair<ArendSourceNode, Boolean>>>? {
    if (checkConcreteExprIsArendExpr(arg, parsedExpr)) {
        if (checkConcreteExprIsFunc(parsedExpr, arg.scope)) {
            return Pair(parsedExpr.data as Abstract.Reference, emptyList())
        }
    }

    if (parsedExpr is Concrete.AppExpression) {
        val createArglist = ret@{
            val ardArguments = mutableListOf<Pair<ArendSourceNode, Boolean>>()
            for (argument_ in parsedExpr.arguments) {
                if (argument_.expression.data !is ArendSourceNode) {
                    return@ret null
                }
                ardArguments.add(Pair(argument_.expression.data as ArendSourceNode, argument_.isExplicit))
            }
            return@ret ardArguments
        }

        if (checkConcreteExprIsArendExpr(arg, parsedExpr.function)) {
            if (checkConcreteExprIsFunc(parsedExpr.function, arg.scope)) {
                return createArglist()?.let { Pair(parsedExpr.data as Abstract.Reference, it) }
            }
        }

        val funcRes = findDefAndArgsInParsedBinop(arg, parsedExpr.function)
        if (funcRes != null) return funcRes

        for (argument in parsedExpr.arguments) {
            if (checkConcreteExprIsArendExpr(arg, argument.expression)) {
                if (checkConcreteExprIsFunc(argument.expression, arg.scope)) {
                    return Pair(argument.expression.data as Abstract.Reference, emptyList())
                }
                if (!checkConcreteExprIsFunc(parsedExpr.function, arg.scope)) return null
                return createArglist()?.let { Pair(parsedExpr.function.data  as Abstract.Reference, it) }
            }
        }

        for (argument in parsedExpr.arguments) {
            val argRes = findDefAndArgsInParsedBinop(arg, argument.expression)
            if (argRes != null) return argRes
        }
    } else if (parsedExpr is Concrete.LamExpression) {
        return findDefAndArgsInParsedBinop(arg, parsedExpr.body)
    }

    return null
}

fun concreteDataToSourceNode(data: Any?): ArendSourceNode? {
    if (data is ArendIPName) {
        val element = data.infix ?: data.postfix
        val node = element?.parentOfType<ArendSourceNode>() ?: return null
        return node
    }
    return data as? ArendSourceNode
}

*/