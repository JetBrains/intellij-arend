package org.arend.injection

import org.arend.core.expr.Expression
import org.arend.core.expr.PiExpression
import org.arend.core.expr.visitor.ExpressionVisitor
import org.arend.ext.prettyprinting.PrettyPrinterConfig
import org.arend.ext.prettyprinting.doc.*
import org.arend.ext.reference.Precedence
import org.arend.term.concrete.Concrete
import org.arend.term.prettyprint.PrettyPrintVisitor
import org.arend.term.prettyprint.ToAbstractVisitor
import org.arend.toolWindow.errors.tree.ArendErrorTreeElement
import kotlin.text.StringBuilder

fun addImplicitArguments(
    file: PsiInjectionTextFile?,
    offset: Int,
    doc: Doc?,
    treeElement: ArendErrorTreeElement?,
    ppConfig: PrettyPrinterConfig
) {
    if (file == null || doc == null || treeElement == null) {
        return
    }
    val (coreExpression, relativeOffset) = doc.findTermByOffset(offset, treeElement) ?: return
    val concreteExpression = ToAbstractVisitor.convert(coreExpression, ppConfig)
    val stringInterceptor = InterceptingPrettyPrintVisitor(relativeOffset)
    concreteExpression.accept(stringInterceptor, Precedence.DEFAULT)
    stringInterceptor.stack.reverse()
    val x = 1
}


fun Doc.findTermByOffset(offset: Int, treeElement: ArendErrorTreeElement) : Pair<Expression, Int>? {
    val collectingBuilder = CollectingDocStringBuilder(StringBuilder(), treeElement.sampleError.error)
    accept(collectingBuilder, false)
    for ((ranges, expression) in collectingBuilder.textRanges.zip(collectingBuilder.expressions)) {
        expression ?: continue
        var relativeOffset = 0
        for (range in ranges) {
            if (range.contains(offset)) {
                relativeOffset += offset - range.startOffset
                return expression to relativeOffset
            }
            relativeOffset += range.length + 1 // 1 for a space
        }
    }
    return null
}

private class InterceptingPrettyPrintVisitor(val relativeOffset: Int, val sb : StringBuilder = StringBuilder()) : PrettyPrintVisitor(sb, 0, false) {
    var stack = mutableListOf<Concrete.Expression>()

    override fun visitReference(expr: Concrete.ReferenceExpression, prec: Precedence?): Void? {
        val offsetBefore = sb.length
        super.visitReference(expr, prec)
        val offsetAfter = sb.length
        if (relativeOffset in offsetBefore until offsetAfter) {
            stack.add(expr)
        }
        return null
    }

    override fun visitApp(expr: Concrete.AppExpression, prec: Precedence?): Void? {
        runWithStackWatch(expr) { super.visitApp(expr, prec) }
        return null
    }

    override fun visitLam(expr: Concrete.LamExpression, prec: Precedence?): Void? {
        runWithStackWatch(expr) { super.visitLam(expr, prec) }
        return null
    }

    override fun visitPi(expr: Concrete.PiExpression, prec: Precedence?): Void? {
        runWithStackWatch(expr) { super.visitPi(expr, prec) }
        return null
    }

    override fun visitTuple(expr: Concrete.TupleExpression, prec: Precedence?): Void? {
        runWithStackWatch(expr) { super.visitTuple(expr, prec) }
        return null
    }

    override fun visitSigma(expr: Concrete.SigmaExpression, prec: Precedence?): Void? {
        runWithStackWatch(expr) { super.visitSigma(expr, prec) }
        return null
    }

    override fun visitBinOpSequence(expr: Concrete.BinOpSequenceExpression, prec: Precedence?): Void? {
        runWithStackWatch(expr) { super.visitBinOpSequence(expr, prec) }
        return null
    }

    override fun visitCase(expr: Concrete.CaseExpression, prec: Precedence?): Void? {
        runWithStackWatch(expr) { super.visitCase(expr, prec) }
        return null
    }

    override fun visitProj(expr: Concrete.ProjExpression, prec: Precedence?): Void? {
        runWithStackWatch(expr) { super.visitProj(expr, prec) }
        return null
    }

    override fun visitNew(expr: Concrete.NewExpression, prec: Precedence?): Void? {
        runWithStackWatch(expr) { super.visitNew(expr, prec) }
        return null
    }

    override fun visitLet(expr: Concrete.LetExpression, prec: Precedence?): Void? {
        runWithStackWatch(expr) { super.visitLet(expr, prec) }
        return null
    }

    override fun visitTyped(expr: Concrete.TypedExpression, prec: Precedence?): Void? {
        runWithStackWatch(expr) { super.visitTyped(expr, prec) }
        return null
    }

    private inline fun runWithStackWatch(concrete: Concrete.Expression, action : () -> Unit) {
        val before = stack.isEmpty()
        action()
        if (stack.isEmpty() != before) {
            stack.add(concrete)
        }
    }
}
