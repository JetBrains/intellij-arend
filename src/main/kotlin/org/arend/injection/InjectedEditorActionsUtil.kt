package org.arend.injection

import com.intellij.util.castSafelyTo
import org.arend.core.expr.DefCallExpression
import org.arend.core.expr.Expression
import org.arend.ext.error.GeneralError
import org.arend.ext.prettyprinting.PrettyPrinterConfig
import org.arend.ext.prettyprinting.doc.Doc
import org.arend.ext.reference.Precedence
import org.arend.term.concrete.Concrete
import org.arend.term.prettyprint.PrettyPrintVisitor
import org.arend.term.prettyprint.ToAbstractVisitor
import org.jetbrains.annotations.TestOnly

sealed interface ConcreteResult {
    @TestOnly
    fun getTextId() : String
}

@JvmInline
value class ConcreteRefExpr(val expr: Concrete.ReferenceExpression) : ConcreteResult {
    override fun getTextId(): String {
        return expr.referent.refName
    }
}

@JvmInline
value class ConcreteLambdaParameter(val expr: Concrete.NameParameter) : ConcreteResult {
    override fun getTextId(): String {
        return expr.names.first()
    }
}

data class RevealableFragment(val lifetime: Int, val result: ConcreteResult)

fun findRevealableCoreAtOffset(
    offset: Int,
    doc: Doc?,
    error: GeneralError?,
    ppConfig: PrettyPrinterConfig,
): RevealableFragment? {
    if (doc == null || error == null) {
        return null
    }
    val (coreExpression, relativeOffset) = doc.findGlobalCoreByOffset(offset, error) ?: return null
    val concreteExpression = ToAbstractVisitor.convert(coreExpression, ppConfig)
    val stringInterceptor = InterceptingPrettyPrintVisitor(relativeOffset)
    concreteExpression.accept(
        stringInterceptor,
        Precedence(Precedence.Associativity.NON_ASSOC, Concrete.Expression.PREC, true)
    )
    return stringInterceptor.result?.run { RevealableFragment(stringInterceptor.lifetime, this) }
}

private fun Doc.findGlobalCoreByOffset(offset: Int, error: GeneralError): Pair<Expression, Int>? {
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

private class InterceptingPrettyPrintVisitor(
    private val relativeOffset: Int,
    private val sb: StringBuilder = StringBuilder()
) : PrettyPrintVisitor(sb, 0, false) {
    var result: ConcreteResult? = null
    var visitParent: Boolean = true
    var lifetime: Int = 0

    override fun visitReference(expr: Concrete.ReferenceExpression, prec: Precedence?): Void? {
        val offsetBefore = sb.length
        super.visitReference(expr, prec)
        val offsetAfter = sb.length
        if (relativeOffset in offsetBefore..offsetAfter) {
            result = ConcreteRefExpr(expr)
        }
        return null
    }

    override fun visitApp(expr: Concrete.AppExpression, prec: Precedence?): Void? {
        val resultBefore = result
        super.visitApp(expr, prec)
        val resultAfter = result
        if (visitParent && resultAfter != resultBefore && resultAfter is ConcreteRefExpr) {
            visitParent = false
            val argumentsShown = expr.arguments.count()
            val agumentsOverall = resultAfter.expr.data.castSafelyTo<DefCallExpression>()?.defCallArguments?.count() ?: return null
            lifetime = agumentsOverall - argumentsShown
            if (agumentsOverall == argumentsShown) {
                // nothing to insert
                result = null
            }
        }
        return null
    }

    override fun prettyPrintParameter(parameter: Concrete.Parameter) {
        val offsetBefore = sb.length
        super.prettyPrintParameter(parameter)
        val offsetAfter = sb.length
        if (relativeOffset in offsetBefore..offsetAfter && parameter is Concrete.NameParameter) {
            result = ConcreteLambdaParameter(parameter)
            lifetime = 1
        }
    }
}
