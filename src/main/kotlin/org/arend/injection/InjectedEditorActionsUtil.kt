package org.arend.injection

import com.intellij.util.castSafelyTo
import org.arend.core.expr.Expression
import org.arend.ext.error.GeneralError
import org.arend.ext.prettyprinting.PrettyPrinterConfig
import org.arend.ext.prettyprinting.doc.*
import org.arend.ext.reference.Precedence
import org.arend.term.concrete.Concrete
import org.arend.term.prettyprint.PrettyPrintVisitor
import org.arend.term.prettyprint.ToAbstractVisitor
import kotlin.text.StringBuilder

fun findCoreAtOffset(
    offset: Int,
    doc: Doc?,
    error: GeneralError?,
    ppConfig: PrettyPrinterConfig,
): Expression? {
    if (doc == null || error == null) {
        return null
    }
    val (coreExpression, relativeOffset) = doc.findGlobalCoreByOffset(offset, error) ?: return null
    val concreteExpression = ToAbstractVisitor.convert(coreExpression, ppConfig)
    val stringInterceptor = InterceptingPrettyPrintVisitor(relativeOffset)
    concreteExpression.accept(stringInterceptor, Precedence(Precedence.Associativity.NON_ASSOC, Concrete.Expression.PREC, true))
    return stringInterceptor.result?.data?.castSafelyTo<Expression>()
}

private fun Doc.findGlobalCoreByOffset(offset: Int, error: GeneralError) : Pair<Expression, Int>? {
    val collectingBuilder = CollectingDocStringBuilder(StringBuilder(), error)
    accept(collectingBuilder, false)
    val docString = toString()
    for ((ranges, expression) in collectingBuilder.textRanges.zip(collectingBuilder.expressions)) {
        expression ?: continue
        var relativeOffset = 0
        for (range in ranges) {
            val substring = docString.subSequence(range.startOffset, range.endOffset).takeWhile { it == ' ' }
            if (range.contains(offset)) {
                relativeOffset += offset - range.startOffset - substring.length
                return expression to relativeOffset
            }
            relativeOffset += range.length + 1 - substring.length // 1 for a space
        }
    }
    return null
}

private class InterceptingPrettyPrintVisitor(private val relativeOffset: Int, private val sb : StringBuilder = StringBuilder())
    : PrettyPrintVisitor(sb, 0, false) {
    var result : Concrete.Expression? = null

    override fun visitReference(expr: Concrete.ReferenceExpression, prec: Precedence?): Void? {
        val offsetBefore = sb.length
        super.visitReference(expr, prec)
        val offsetAfter = sb.length
        if (relativeOffset in offsetBefore..offsetAfter) {
            result = expr
        }
        return null
    }
}
