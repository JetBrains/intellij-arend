package org.arend.util

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.arend.naming.reference.Referable
import org.arend.naming.scope.Scope
import org.arend.psi.ArendExpr
import org.arend.psi.ArendIPName
import org.arend.psi.ext.ArendReferenceContainer
import org.arend.psi.ext.ArendSourceNode
import org.arend.refactoring.resolveIfNeeded
import org.arend.term.Fixity
import org.arend.term.abs.Abstract
import org.arend.term.abs.BaseAbstractExpressionVisitor
import org.arend.term.concrete.Concrete
import org.arend.typing.parseBinOp
import org.arend.typing.resolveReference

fun appExprToConcrete(appExpr: Abstract.Expression): Concrete.Expression? = appExpr.accept(object : BaseAbstractExpressionVisitor<Void, Concrete.Expression>(null) {
    override fun visitBinOpSequence(data: Any?, left: Abstract.Expression, sequence: Collection<Abstract.BinOpSequenceElem>, params: Void?) =
            parseBinOp(left, sequence)

    override fun visitReference(data: Any?, referent: Referable, lp: Int, lh: Int, params: Void?) = resolveReference(data, referent, null)
    override fun visitReference(data: Any?, referent: Referable, fixity: Fixity?, level1: Abstract.LevelExpression?, level2: Abstract.LevelExpression?, params: Void?) = resolveReference(data, referent, fixity)
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

        val startOffset = elements.asSequence().map { it.startOffset }.min()
        val endOffset = elements.asSequence().map { it.endOffset }.max()
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

fun checkConcreteExprIsArendExpr(aExpr: ArendExpr, cExpr: Concrete.Expression): Boolean {
    val checkConcreteExprDataIsArendNode = { cData: ArendSourceNode?, aNode: ArendSourceNode -> // Rewrite in a less ad-hoc way
        cData?.topmostEquivalentSourceNode == aNode.topmostEquivalentSourceNode ||
                cData?.topmostEquivalentSourceNode?.parentSourceNode?.topmostEquivalentSourceNode == aNode.topmostEquivalentSourceNode ||
                cData?.parentSourceNode?.parentSourceNode?.topmostEquivalentSourceNode == aNode.topmostEquivalentSourceNode }

    if (cExpr is Concrete.AppExpression) return false

    return checkConcreteExprDataIsArendNode(concreteDataToSourceNode(cExpr.data), aExpr)
}

fun checkConcreteExprIsFunc(expr: Concrete.Expression, scope: Scope): Boolean =
        expr is Concrete.ReferenceExpression && resolveIfNeeded(expr.referent, scope) is Abstract.ParametersHolder && expr.data is ArendReferenceContainer

data class DefAndArgsInParsedBinopResult(val functionReferenceContainer: ArendReferenceContainer,
                                         val operatorConcrete: Concrete.Expression,
                                         val argumentsConcrete: List<Concrete.Argument>)

fun findDefAndArgsInParsedBinop(arg: ArendExpr, parsedExpr: Concrete.Expression): DefAndArgsInParsedBinopResult? {
    if (checkConcreteExprIsArendExpr(arg, parsedExpr) && checkConcreteExprIsFunc(parsedExpr, arg.scope))
        return DefAndArgsInParsedBinopResult(parsedExpr.data as ArendReferenceContainer, parsedExpr, emptyList())

    if (parsedExpr is Concrete.AppExpression) {
        if (checkConcreteExprIsArendExpr(arg, parsedExpr.function) && checkConcreteExprIsFunc(parsedExpr.function, arg.scope))
            return DefAndArgsInParsedBinopResult(parsedExpr.function.data as ArendReferenceContainer, parsedExpr, parsedExpr.arguments)

        findDefAndArgsInParsedBinop(arg, parsedExpr.function)?.let { return it }

        for (argument in parsedExpr.arguments) {
            if (checkConcreteExprIsArendExpr(arg, argument.expression)) {
                if (checkConcreteExprIsFunc(argument.expression, arg.scope)) {
                    return DefAndArgsInParsedBinopResult(argument.expression.data as ArendReferenceContainer, argument.expression, emptyList())
                }
                if (!checkConcreteExprIsFunc(parsedExpr.function, arg.scope)) return null
                return DefAndArgsInParsedBinopResult(parsedExpr.function.data as ArendReferenceContainer, parsedExpr, parsedExpr.arguments)
            }
        }

        for (argument in parsedExpr.arguments)
            findDefAndArgsInParsedBinop(arg, argument.expression)?.let { return it }

    } else if (parsedExpr is Concrete.LamExpression)
        return findDefAndArgsInParsedBinop(arg, parsedExpr.body)

    return null
}