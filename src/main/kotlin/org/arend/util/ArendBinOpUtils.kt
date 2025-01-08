package org.arend.util

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.arend.error.DummyErrorReporter
import org.arend.ext.error.ErrorReporter
import org.arend.naming.reference.AliasReferable
import org.arend.naming.reference.GlobalReferable
import org.arend.naming.reference.Referable
import org.arend.naming.resolving.visitor.ExpressionResolveNameVisitor
import org.arend.naming.scope.CachingScope
import org.arend.psi.ext.*
import org.arend.psi.ext.ArendExpr
import org.arend.resolving.util.parseBinOp
import org.arend.resolving.util.resolveReference
import org.arend.term.Fixity
import org.arend.term.abs.Abstract
import org.arend.term.abs.AbstractReference
import org.arend.term.abs.BaseAbstractExpressionVisitor
import org.arend.term.abs.ConcreteBuilder
import org.arend.term.concrete.Concrete
import org.arend.term.concrete.Concrete.NumberPattern

fun appExprToConcrete(appExpr: ArendExpr): Concrete.Expression? = appExprToConcrete(appExpr, false)

fun appExprToConcrete(appExpr: ArendExpr, setData: Boolean, errorReporter: ErrorReporter = DummyErrorReporter.INSTANCE): Concrete.Expression? {
    return appExpr.accept(object : BaseAbstractExpressionVisitor<Void, Concrete.Expression>(null) {
        override fun visitBinOpSequence(data: Any?, left: Abstract.Expression, sequence: Collection<Abstract.BinOpSequenceElem>, params: Void?): Concrete.Expression =
                parseBinOp(if (setData) data else null, left, sequence, errorReporter)

        override fun visitReference(data: Any?, referent: Referable, lp: Int, lh: Int, params: Void?) =
                resolveReference(data, referent, null)

        override fun visitReference(data: Any?, referent: Referable, fixity: Fixity?, pLevels: Collection<Abstract.LevelExpression>?, hLevels: Collection<Abstract.LevelExpression>?, params: Void?) =
                resolveReference(data, referent, fixity)

        override fun visitFieldAccs(data: Any?, expression: Abstract.Expression, fieldAccs: MutableList<Abstract.FieldAcc>, infixReference: AbstractReference?, infixName: String?, isInfix: Boolean, params: Void?): Concrete.Expression {
            var result = expression.accept<Void, Concrete.Expression>(this, null)
            for (fieldAcc in fieldAccs) {
                val number = fieldAcc.number
                if (number != null) {
                    result = Concrete.ProjExpression(data, result, number - 1)
                } else {
                    val fieldName = fieldAcc.fieldName
                    if (fieldName != null) {
                        result = Concrete.FieldCallExpression(data, fieldName, Fixity.UNKNOWN, result)
                    }
                }
            }
            return result!!
        }

    }, null)
}

fun patternToConcrete(unparsedPattern: ArendPattern, errorReporter: ErrorReporter = DummyErrorReporter.INSTANCE): Concrete.Pattern? {
    val scope = CachingScope.make(unparsedPattern.scope)
    val builder = object: ConcreteBuilder(errorReporter, null) {
        override fun buildPattern(pattern: Abstract.Pattern?): Concrete.Pattern {
            if (pattern is ArendPattern && pattern.getSequence().size == 1 && pattern.isTuplePattern) {
                return NumberPattern(pattern.data, -1, buildTypedReferable(pattern.asPattern)) // substitute every parenthesized pattern with dummy
            }
            return super.buildPattern(pattern)
        }
    }
    val unparsed = builder.buildPattern(unparsedPattern)
    val referables = ArrayList<Referable>()
    val patterns = ArrayList<Concrete.Pattern>(); patterns.add(unparsed)
    ExpressionResolveNameVisitor(scope, referables, errorReporter, null).visitPatterns(patterns, HashMap())
    return patterns.firstOrNull()
}

fun getBounds(cExpr: Concrete.SourceNode, aaeBlocks: List<ASTNode>, rangesMap: HashMap<Concrete.SourceNode, TextRange>? = null): TextRange? {
    val cExprData = cExpr.data
    var result: TextRange? = null

    if (rangesMap != null && rangesMap[cExpr] != null) return rangesMap[cExpr]

    if (cExpr is Concrete.AppExpression || cExpr is Concrete.ConstructorPattern) {
        val elements = ArrayList<TextRange>()
        val fData = when (cExpr) {
            is Concrete.AppExpression -> cExpr.function.data
            is Concrete.ConstructorPattern -> cExpr.constructorData
            else -> throw IllegalStateException()
        }

        val args = when (cExpr) {
            is Concrete.AppExpression -> cExpr.arguments
            is Concrete.ConstructorPattern -> cExpr.patterns
            else -> throw IllegalStateException()
        }

        elements.addAll(args.asSequence().mapNotNull {
            val key = when (it) {
                is Concrete.Argument -> it.expression
                is Concrete.Pattern -> it
                else -> throw IllegalStateException()
            }
            getBounds(key, aaeBlocks, rangesMap) }
        )

        if (fData is PsiElement) {
            val f = aaeBlocks.filter { it.textRange.contains(fData.textRange) }
            if (f.size != 1) throw IllegalStateException()
            val functionRange = f.first().textRange
            elements.add(functionRange)
            if (cExpr is Concrete.AppExpression) rangesMap?.put(cExpr.function, functionRange)
        }

        val startOffset = elements.asSequence().map { it.startOffset }.minOrNull()
        val endOffset = elements.asSequence().map { it.endOffset }.maxOrNull()
        if (startOffset != null && endOffset != null) {
            result = TextRange.create(startOffset, endOffset)
        }
    } else if (cExprData is PsiElement) {
        for (psi in aaeBlocks) if (psi.textRange.contains(cExprData.node.textRange)) {
            result = psi.textRange
            break
        }
    } else if (cExpr is Concrete.LamExpression) { //cExpr.data == null, so this is most likely a postfix or apply hole expression
        result = getBounds(cExpr.body, aaeBlocks, rangesMap)
    }

    if (result != null) rangesMap?.put(cExpr, result)
    return result
}

fun concreteDataToSourceNode(data: Any?): ArendSourceNode? =
    if (data is ArendIPName) data.parentOfType() else data as? ArendSourceNode

fun checkConcreteExprIsArendExpr(aExpr: Abstract.SourceNode, cExpr: Concrete.Expression): Boolean {
    val checkConcreteExprDataIsArendNode = ret@{ cData: ArendSourceNode?, aNode: Abstract.SourceNode ->
        // Rewrite in a less ad-hoc way
        if (cData?.topmostEquivalentSourceNode == aNode.topmostEquivalentSourceNode ||
                cData?.topmostEquivalentSourceNode?.parentSourceNode?.topmostEquivalentSourceNode == aNode.topmostEquivalentSourceNode
                || cData?.parentSourceNode?.parentSourceNode?.topmostEquivalentSourceNode == aNode.topmostEquivalentSourceNode
                || cData?.parentSourceNode?.parentSourceNode?.parentSourceNode?.topmostEquivalentSourceNode == aNode.topmostEquivalentSourceNode
        ) {
            return@ret true
        }
        return@ret false
    }
    if (cExpr is Concrete.AppExpression) {
        return false
    }
    /*if (aExpr is ArendImplicitArgument) {
        val expr = aExpr.tupleExprList.firstOrNull()?.let { it.type ?: it.expr } ?: return false
        return checkConcreteExprDataIsArendNode(concreteDataToSourceNode(cExpr.data), expr)
    }*/
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

fun isBinOp(binOpReference: ArendReferenceContainer?) =
        if (binOpReference is ArendIPName) binOpReference.infix != null
        else (resolve(binOpReference))?.precedence?.isInfix == true

private fun resolve(reference: ArendReferenceContainer?): GlobalReferable? =
        (reference?.resolve as? GlobalReferable)
                ?.let { if (it.hasAlias() && it.aliasName == reference.referenceName) AliasReferable(it) else it }

/**
 * [action] returns true if the processing should be stopped.
 */
fun forEachRange(concrete: Concrete.Expression, action: (TextRange, Concrete.Expression) -> Boolean): TextRange? {
    var result: TextRange? = null

    fun doVisit(concrete: Concrete.Expression): TextRange? {
        when (concrete) {
            is Concrete.AppExpression -> {
                val childRanges = mutableListOf<TextRange>()
                for (arg in concrete.arguments) {
                    val argRange = doVisit(arg.expression) ?: return null
                    if (action(argRange, arg.expression)) {
                        result = argRange
                        return null
                    }
                    childRanges.add(argRange)
                }
                childRanges.add((concrete.function.data as PsiElement).textRange)
                return TextRange(childRanges.minOf { it.startOffset }, childRanges.maxOf { it.endOffset })
            }
            else -> return (concrete.data as PsiElement).textRange
        }
    }
    val resultRange = doVisit(concrete)
    if (resultRange != null && action(resultRange, concrete)) {
        result = resultRange
    }
    return result
}