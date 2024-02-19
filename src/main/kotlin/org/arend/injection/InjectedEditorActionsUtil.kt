package org.arend.injection

import org.arend.core.context.param.DependentLink
import org.arend.core.context.param.EmptyDependentLink
import org.arend.core.expr.ClassCallExpression
import org.arend.core.expr.ClassCallExpression.ClassCallBinding
import org.arend.core.expr.DefCallExpression
import org.arend.core.expr.Expression
import org.arend.core.expr.FieldCallExpression
import org.arend.core.expr.ReferenceExpression
import org.arend.ext.error.GeneralError
import org.arend.ext.prettyprinting.PrettyPrinterConfig
import org.arend.ext.prettyprinting.PrettyPrinterFlag
import org.arend.ext.prettyprinting.doc.Doc
import org.arend.ext.reference.Precedence
import org.arend.injection.actions.NormalizationCache
import org.arend.naming.reference.ModuleReferable
import org.arend.term.concrete.Concrete
import org.arend.term.concrete.Concrete.LongReferenceExpression
import org.arend.term.prettyprint.PrettyPrintVisitor
import org.arend.term.prettyprint.ToAbstractVisitor
import org.arend.util.allBindings
import org.arend.util.allParameters
import org.jetbrains.annotations.TestOnly

sealed interface ConcreteResult {
    @TestOnly
    fun getTextId(): String

    val expr: Concrete.SourceNode
}

@JvmInline
value class ConcreteRefExpr(override val expr: Concrete.ReferenceExpression) : ConcreteResult {
    override fun getTextId(): String {
        return expr.referent.refName
    }
}

@JvmInline
value class ConcreteLambdaParameter(override val expr: Concrete.Parameter) : ConcreteResult {
    override fun getTextId(): String {
        return expr.names.first()
    }
}

@JvmInline
value class ConcreteTuple(override val expr: Concrete.TupleExpression) : ConcreteResult {
    override fun getTextId(): String {
        return expr.toString()
    }
}

@JvmInline
value class ConcreteImplementation(override val expr: Concrete.Expression) : ConcreteResult {
    override fun getTextId(): String {
        return expr.toString()
    }
}

data class RevealableFragment(
    val revealLifetime: Int,
    val hideLifetime: Int,
    val result: ConcreteResult,
    val relativeOffset: Int
)

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
    val stringInterceptor = InterceptingPrettyPrintVisitor(relativeOffset, ppConfig)
    concreteExpression.accept(
        stringInterceptor,
        Precedence(Precedence.Associativity.NON_ASSOC, Concrete.Expression.PREC, true)
    )
    return stringInterceptor.revealableResult?.run {
        RevealableFragment(
            stringInterceptor.lifetime,
            stringInterceptor.hideLifetime,
            this,
            relativeOffset
        )
    }
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
            relativeOffset += range.length - substring.length + 1 // 1 is for \n, which will be replaced by ' '
        }
    }
    return null
}

private class InterceptingPrettyPrintVisitor(
    private val relativeOffset: Int,
    private val ppConfig: PrettyPrinterConfig,
    private val sb: StringBuilder = StringBuilder()
) : PrettyPrintVisitor(sb, 0, false) {
    var revealableResult: ConcreteResult? = null
    private var visitParent: Boolean = true
    var lifetime: Int = 0
    var hideLifetime: Int = 0
    var indentOffset = 0

    private val actualRelativeOffset : Int get() = relativeOffset + indentOffset

    override fun visitReference(expr: Concrete.ReferenceExpression, prec: Precedence?): Void? {
        visitReferenceExpression(expr) { super.visitReference(expr, prec) }
        return null
    }

    override fun printReferenceName(expr: Concrete.ReferenceExpression, prec: Precedence?) {
        visitReferenceExpression(expr) { super.printReferenceName(expr, prec) }
    }

    private fun visitReferenceExpression(expr: Concrete.ReferenceExpression, action : () -> Unit) {
        val offsetBefore = sb.length
        action()
        val offsetAfter = sb.length
        if (actualRelativeOffset in offsetBefore..offsetAfter) {
            revealableResult = ConcreteRefExpr(expr)
            val data = expr.data
            if (data is Expression) {
                lifetime = getImplicitArgumentsCount(data, expr)
                hideLifetime = 0
            }
        }
    }

    private fun getImplicitArgumentsCount(core: Expression, expr: Concrete.ReferenceExpression) : Int {
        if (((core as? DefCallExpression)?.defCallArguments?.singleOrNull() as? ReferenceExpression)?.binding is ClassCallBinding) {
            return 0
        }
        if (core is FieldCallExpression) {
            val definition = core.definition.type.allParameters().flatMap { it.parameters.allBindings() }
            val subtractee = (expr as? LongReferenceExpression)?.qualifier?.referent?.takeIf { it !is ModuleReferable }?.let { 1 } ?: 0
            return definition.count { !it.isExplicit } - subtractee
        }
        if (core is ClassCallExpression) {
            val implementations = core.implementations
            return core.definition.notImplementedFields.take(implementations.size).count { !it.referable.isExplicitField }
        }
        val parameterLink = (core as? DefCallExpression)?.definition?.parameters
        if (parameterLink != null) {
            return parameterLink.accumulate(0) { prev, link -> prev + (if (!link.isExplicit) 1 else 0) }
        }
        return 0
    }

    override fun printIndent() {
        val offsetBefore = sb.length
        super.printIndent()
        val offsetAfter = sb.length
        indentOffset += offsetAfter - offsetBefore // newline is not considered here
    }

    override fun visitTuple(expr: Concrete.TupleExpression, prec: Precedence): Void? {
        val offsetBefore = sb.length
        super.visitTuple(expr, prec)
        val offsetAfter = sb.length
        if (revealableResult == null && actualRelativeOffset in offsetBefore..offsetAfter) {
            revealableResult = ConcreteTuple(expr)
            lifetime = 1
            hideLifetime = 0
        }
        return null
    }

    override fun visitTyped(expr: Concrete.TypedExpression, prec: Precedence): Void? {
        val resultBefore = revealableResult
        super.visitTyped(expr, prec)
        val resultAfter = revealableResult
        if (resultAfter != resultBefore && resultAfter is ConcreteTuple && expr.expression == resultAfter.expr) {
            visitParent = false
            lifetime = 0
            hideLifetime = 1
        }
        return null
    }

    override fun visitApp(expr: Concrete.AppExpression, prec: Precedence?): Void? {
        val resultBefore = revealableResult
        super.visitApp(expr, prec)
        val resultAfter = revealableResult
        if (visitParent && resultAfter != resultBefore && resultAfter is ConcreteRefExpr && resultAfter.expr == expr.function) {
            visitParent = false
            val implicitArgumentsShown = expr.arguments.count { !it.isExplicit }
            val implicitArgumentsOverall = (resultAfter.expr.data as? Expression)?.let { getImplicitArgumentsCount(it.function, resultAfter.expr)} ?: return null
            lifetime = implicitArgumentsOverall - implicitArgumentsShown
            hideLifetime = implicitArgumentsShown
        }
        return null
    }

    override fun prettyPrintParameter(parameter: Concrete.Parameter) {
        val offsetBefore = sb.length
        super.prettyPrintParameter(parameter)
        val offsetAfter = sb.length
        if (visitParent && revealableResult == null && actualRelativeOffset in offsetBefore..offsetAfter) {
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

    override fun visitSigma(expr: Concrete.SigmaExpression, prec: Precedence?): Void? {
        val resultBefore = revealableResult
        super.visitSigma(expr, prec)
        val resultAfter = revealableResult
        if (visitParent && resultBefore != resultAfter && resultAfter is ConcreteLambdaParameter && expr.parameters.any { it == resultAfter.expr }) {
            visitParent = false
            revealableResult = null
        }
        return null
    }

    override fun visitGoal(expr: Concrete.GoalExpression, prec: Precedence?): Void? {
        val offsetBefore = sb.length
        super.visitGoal(expr, prec)
        val offsetAfter = sb.length
        if (revealableResult == null && actualRelativeOffset in offsetBefore..offsetAfter) {
            revealableResult = ConcreteImplementation(expr)
            lifetime = 1
            hideLifetime = 0
        }
        return null
    }

    override fun prettyPrintClassFieldImpl(classFieldImpl: Concrete.ClassFieldImpl) {
        val resultBefore = revealableResult
        super.prettyPrintClassFieldImpl(classFieldImpl)
        val resultAfter = revealableResult
        if (visitParent && resultBefore != resultAfter && resultAfter is ConcreteImplementation) {
            visitParent = false
            if (classFieldImpl.implementation != resultAfter.expr) {
                revealableResult = null
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
            if (ppConfig.expressionFlags.contains(PrettyPrinterFlag.SHOW_TYPES_IN_LAM)) {
                revealableResult = null
            }
        }
        return null
    }
}

private fun <T> DependentLink.accumulate(initial: T, consumer: (T, DependentLink) -> T): T {
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