package org.vclang.typing

import com.jetbrains.jetpad.vclang.naming.reference.ClassReferable
import com.jetbrains.jetpad.vclang.naming.reference.Referable
import com.jetbrains.jetpad.vclang.naming.reference.UnresolvedReference
import com.jetbrains.jetpad.vclang.naming.scope.Scope
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import com.jetbrains.jetpad.vclang.term.abs.BaseAbstractExpressionVisitor
import com.jetbrains.jetpad.vclang.term.concrete.Concrete
import org.vclang.psi.VcDefFunction


class ReferableExtractVisitor(private val scope: Scope) : BaseAbstractExpressionVisitor<Void?, Referable?>(null) {
    private var isType = true
    private var arguments: Int = 0

    private fun findClassReference(referent: Referable?): ClassReferable? {
        isType = false
        var ref: Referable? = referent
        var visited: MutableSet<VcDefFunction>? = null
        while (true) {
            ref = (ref as? UnresolvedReference)?.resolve(scope) ?: ref
            if (ref is ClassReferable) {
                return ref
            }
            if (ref !is VcDefFunction) {
                return null
            }

            val term = ref.functionBody?.expr ?: return null
            arguments -= ref.nameTeleList.sumBy { if (it.isExplicit) it.identifierOrUnknownList.size else 0 }
            if (arguments < 0) {
                return null
            }

            if (visited == null) {
                visited = mutableSetOf(ref)
            } else {
                if (!visited.add(ref)) {
                    return null
                }
            }
            ref = term.accept(this, null)
        }
    }

    fun findClassReferable(expr: Abstract.Expression): ClassReferable? {
        isType = true
        arguments = 0
        return findClassReference(expr.accept(this, null))
    }

    override fun visitApp(data: Any?, expr: Abstract.Expression, args: Collection<Abstract.Argument>, errorData: Abstract.ErrorData?, params: Void?): Referable? {
        arguments += args.sumBy { if (it.isExplicit) 1 else 0 }
        return expr.accept(this, null)
    }

    override fun visitBinOpSequence(data: Any?, left: Abstract.Expression, sequence: Collection<Abstract.BinOpSequenceElem>, errorData: Abstract.ErrorData?, params: Void?): Referable? {
        var expr = parseBinOp(left, sequence)
        while (expr is Concrete.AppExpression) {
            arguments++
            expr = expr.function
        }
        return (expr as? Concrete.ReferenceExpression)?.referent
    }

    override fun visitReference(data: Any?, referent: Referable, level1: Abstract.LevelExpression?, level2: Abstract.LevelExpression?, errorData: Abstract.ErrorData?, params: Void?): Referable? = referent

    override fun visitReference(data: Any?, referent: Referable, lp: Int, lh: Int, errorData: Abstract.ErrorData?, params: Void?): Referable? = referent

    override fun visitLam(data: Any?, parameters: Collection<Abstract.Parameter>, body: Abstract.Expression?, errorData: Abstract.ErrorData?, params: Void?): Referable? {
        if (isType || body == null) {
            return null
        }

        arguments -= parameters.sumBy { if (it.isExplicit) it.referableList.size else 0 }
        if (arguments < 0) {
            return null
        }

        return body.accept(this, null)
    }

    override fun visitPi(data: Any?, parameters: Collection<Abstract.Parameter>, codomain: Abstract.Expression?, errorData: Abstract.ErrorData?, params: Void?): Referable? {
        if (!isType || codomain == null) {
            return null
        }

        for (parameter in parameters) {
            if (parameter.isExplicit) {
                return null
            }
        }

        return codomain.accept(this, null)
    }

    override fun visitClassExt(data: Any?, isNew: Boolean, baseClass: Abstract.Expression?, implementations: Collection<Abstract.ClassFieldImpl>?, sequence: Collection<Abstract.BinOpSequenceElem>, errorData: Abstract.ErrorData?, params: Void?): Referable? =
        if (isNew || !sequence.isEmpty() || baseClass == null) null else baseClass.accept(this, null)
}