package org.arend.injection

import com.intellij.util.castSafelyTo
import org.arend.core.context.param.DependentLink
import org.arend.core.context.param.EmptyDependentLink
import org.arend.core.expr.DefCallExpression
import org.arend.core.expr.Expression
import org.arend.ext.error.GeneralError
import org.arend.ext.prettyprinting.PrettyPrinterConfig
import org.arend.ext.prettyprinting.doc.Doc
import org.arend.ext.reference.Precedence
import org.arend.injection.actions.NormalizationCache
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
value class ConcreteLambdaParameter(val expr: Concrete.Parameter) : ConcreteResult {
    override fun getTextId(): String {
        return expr.names.first()
    }
}

data class RevealableFragment(val revealLifetime: Int, val hideLifetime: Int, val result: ConcreteResult, val relativeOffset: Int)

fun findRevealableCoreAtOffset(
    offset: Int,
    doc: Doc?,
    error: GeneralError?,
    ppConfig: PrettyPrinterConfig,
    cache: NormalizationCache
): RevealableFragment? {
    if (doc == null || error == null) {
        return null
    }
    val (coreExpression, relativeOffset) = doc.findInjectedCoreByOffset(offset, error) ?: return null
    val normalizedCore = cache.getNormalizedExpression(coreExpression)
    val concreteExpression = ToAbstractVisitor.convert(normalizedCore, ppConfig)
    val stringInterceptor = InterceptingPrettyPrintVisitor(relativeOffset)
    concreteExpression.accept(
        stringInterceptor,
        Precedence(Precedence.Associativity.NON_ASSOC, Concrete.Expression.PREC, true)
    )
    return stringInterceptor.revealableResult?.run { RevealableFragment(stringInterceptor.lifetime, stringInterceptor.hideLifetime, this, relativeOffset) }
}

private fun Doc.findInjectedCoreByOffset(offset: Int, error: GeneralError): Pair<Expression, Int>? {
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
    var revealableResult: ConcreteResult? = null
    private var visitParent: Boolean = true
    var lifetime: Int = 0
    var hideLifetime: Int = 0

    override fun visitReference(expr: Concrete.ReferenceExpression, prec: Precedence?): Void? {
        val offsetBefore = sb.length
        super.visitReference(expr, prec)
        val offsetAfter = sb.length
        if (relativeOffset in offsetBefore..offsetAfter) {
            revealableResult = ConcreteRefExpr(expr)
            val parameterLink = expr.data?.castSafelyTo<DefCallExpression>()?.definition?.parameters
            if (parameterLink != null && parameterLink.accumulate(true) { prev, link -> prev && !link.isExplicit }) {
                lifetime = parameterLink.accumulate(0) { prev, _ -> prev + 1 }
                hideLifetime = 0
            }
        }
        return null
    }

      override fun visitApp(expr: Concrete.AppExpression, prec: Precedence?): Void? {
        val resultBefore = revealableResult
        super.visitApp(expr, prec)
        val resultAfter = revealableResult
        if (visitParent && resultAfter != resultBefore && resultAfter is ConcreteRefExpr && resultAfter.expr == expr.function) {
            visitParent = false
            val argumentsShown = expr.arguments.count()
            val agumentsOverall = resultAfter.expr.data.castSafelyTo<DefCallExpression>()?.defCallArguments?.count() ?: return null
            lifetime = agumentsOverall - argumentsShown
            hideLifetime = expr.arguments.count { !it.isExplicit }
        }
        return null
    }

    override fun prettyPrintParameter(parameter: Concrete.Parameter) {
        val offsetBefore = sb.length
        super.prettyPrintParameter(parameter)
        val offsetAfter = sb.length
        if (revealableResult == null && relativeOffset in offsetBefore..offsetAfter) {
            if (parameter is Concrete.NameParameter) {
                revealableResult = ConcreteLambdaParameter(parameter)
                lifetime = 1
                hideLifetime = 0
            }
            if (parameter is Concrete.TypeParameter) {
                revealableResult = ConcreteLambdaParameter(parameter)
                lifetime = 0
                hideLifetime = 1
            }
        }
    }

    override fun visitPi(expr: Concrete.PiExpression?, prec: Precedence?): Void? {
        val resultBefore = revealableResult
        super.visitPi(expr, prec)
        val resultAfter = revealableResult
        if (visitParent && resultBefore != resultAfter && resultAfter is ConcreteLambdaParameter) {
            visitParent = false
            revealableResult = null
        }
        return null
    }

    override fun visitLam(expr: Concrete.LamExpression?, prec: Precedence?): Void? {
        val resultBefore = revealableResult
        super.visitLam(expr, prec)
        val resultAfter = revealableResult
        if (visitParent && resultBefore != resultAfter && resultAfter is ConcreteLambdaParameter) {
            visitParent = false
        }
        return null
    }
}

private fun <T> DependentLink.accumulate(initial: T, consumer: (T, DependentLink) -> T) : T {
    var current = this
    var value = initial
    while (true) {
        value = consumer(value, current)
        if (current.hasNext()) {
            current = current.next
            if (current is EmptyDependentLink) {
                return value
            }
        } else {
            return value
        }
    }
}