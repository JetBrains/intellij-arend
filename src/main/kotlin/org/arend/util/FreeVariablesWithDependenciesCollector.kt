package org.arend.util

import org.arend.core.context.binding.Binding
import org.arend.core.context.binding.TypedBinding
import org.arend.core.context.param.DependentLink
import org.arend.core.elimtree.ElimBody
import org.arend.core.expr.*
import org.arend.core.expr.visitor.VoidExpressionVisitor
import org.arend.ext.core.ops.NormalizationMode
import kotlin.Pair


enum class ParameterExplicitnessState {
    EXPLICIT, IMPLICIT;

    fun surround(string : String) : String = when(this) {
        EXPLICIT -> "($string)"
        IMPLICIT -> "{$string}"
    }
}

class FreeVariablesWithDependenciesCollector private constructor() : VoidExpressionVisitor<ParameterExplicitnessState>() {
    companion object {
        fun collectFreeVariables(expressions : List<Expression>) : List<Pair<Binding, ParameterExplicitnessState>> =
            FreeVariablesWithDependenciesCollector()
                .apply { expressions.forEach { it.accept(this, ParameterExplicitnessState.EXPLICIT) } }
                .run {
                    val allImplicitBindings = freeBindings.mapNotNull { if (it.second == ParameterExplicitnessState.IMPLICIT) it.first else null }
                    val classRefs = classReferences.filter { it.typeExpr !in recognizedClasses }.map { it to ParameterExplicitnessState.EXPLICIT }
                    freeBindings.removeIf { it.first in toRemove || (it.second == ParameterExplicitnessState.EXPLICIT && it.first in allImplicitBindings) }
                    classRefs + freeBindings.toList()
                }
    }

    private val freeBindings : MutableSet<Pair<Binding, ParameterExplicitnessState>> = LinkedHashSet()
    private val toRemove : MutableSet<Binding> = HashSet()
    private val recognizedClasses : MutableSet<ClassCallExpression> = HashSet()
    private val classReferences : MutableSet<ClassCallExpression.ClassCallBinding>  = HashSet()

    override fun visitReference(expr: ReferenceExpression, state: ParameterExplicitnessState): Void? {
        val currentBinding = expr.binding
        if (currentBinding is ClassCallExpression.ClassCallBinding) {
            classReferences.add(currentBinding)
            return null
        }
        currentBinding.type.normalize(NormalizationMode.RNF).expr.accept(this, ParameterExplicitnessState.IMPLICIT)
        val weakenedState = weakenState(state, currentBinding.typeExpr)
        freeBindings.add(currentBinding to weakenedState)
        return null
    }

    private fun weakenState(state: ParameterExplicitnessState, typeExpr: Expression): ParameterExplicitnessState {
        if (state == ParameterExplicitnessState.EXPLICIT) return state
        return when (typeExpr) {
            is FunCallExpression, is PiExpression, is SigmaExpression -> ParameterExplicitnessState.EXPLICIT
            else -> state
        }
    }

    override fun visitClassCall(expr: ClassCallExpression, state: ParameterExplicitnessState): Void? {
        recognizedClasses.add(expr)
        visitDefCall(expr, state)
        for ((_, value) in expr.implementedHere) {
            value.accept(this, state)
        }
        toRemove.add(expr.thisBinding)
        return null
    }

    override fun visitSubst(expr: SubstExpression, state: ParameterExplicitnessState): Void? {
        expr.expression.accept(this, state)
        toRemove.addAll(expr.substitution.keys)
        for ((_, value) in expr.substitution.entries) {
            value.accept(this, state)
        }
        return null
    }



    private fun freeParams(param: DependentLink) {
        var currentParam = param
        while (currentParam.hasNext()) {
            toRemove.add(currentParam)
            currentParam = currentParam.next
        }
    }

    override fun visitLam(expr: LamExpression, state: ParameterExplicitnessState): Void? {
        super.visitLam(expr, state)
        freeParams(expr.parameters)
        return null
    }

    override fun visitPi(expr: PiExpression, state: ParameterExplicitnessState): Void? {
        super.visitPi(expr, state)
        freeParams(expr.parameters)
        return null
    }

    override fun visitSigma(expr: SigmaExpression, state: ParameterExplicitnessState): Void? {
        super.visitSigma(expr, state)
        freeParams(expr.parameters)
        return null
    }

    override fun visitLet(expr: LetExpression, state: ParameterExplicitnessState): Void? {
        super.visitLet(expr, state)
        for (clause in expr.clauses) {
            toRemove.add(clause)
        }
        return null
    }

    override fun visitElimBody(elimBody: ElimBody, state: ParameterExplicitnessState) {
        super.visitElimBody(elimBody, state)
        for (clause in elimBody.clauses) {
            freeParams(clause.parameters)
        }
    }

    override fun visitCase(expr: CaseExpression, state: ParameterExplicitnessState): Void? {
        super.visitCase(expr, state)
        freeParams(expr.parameters)
        return null
    }

    override fun visitProj(expr: ProjExpression, state: ParameterExplicitnessState): Void? {
        val representation = expr.toString()
        if (representation.contains(".")) {
            expr.expression.accept(this, state)
        } else {
            // this is a decomposition of tuple/record
            freeBindings.add(TypedBinding(expr.toString(), expr.type) to state)
        }
        return null
    }
}
