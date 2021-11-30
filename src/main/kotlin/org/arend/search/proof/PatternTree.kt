package org.arend.search.proof

import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import com.intellij.util.castSafelyTo
import com.intellij.util.containers.map2Array
import org.arend.psi.ArendExpr
import org.arend.psi.ArendTuple
import org.arend.psi.childOfType
import org.arend.psi.ext.ArendCompositeElement
import org.arend.term.concrete.BaseConcreteExpressionVisitor
import org.arend.term.concrete.Concrete
import org.arend.util.appExprToConcrete

/**
 * Proof Search in Arend differs from regular structural search in the sense that
 * searched expression is not a valid Arend PSI element.
 * Pattern of the Proof Search contains infix symbols that cannot be completely resolved at the time of its writing.
 */
sealed interface PatternTree {

    enum class Implicitness {
        IMPLICIT, EXPLICIT;

        fun toBoolean(): Boolean = this == EXPLICIT
    }

    @JvmInline
    value class BranchingNode(val subNodes: List<Pair<PatternTree, Implicitness>>) : PatternTree {
        override fun toString(): String = subNodes.joinToString(" ", "[", "]", transform = { it.first.toString() })
    }

    @JvmInline
    value class LeafNode(val referenceName: List<String>) : PatternTree {
        override fun toString(): String = referenceName.joinToString(".")
    }

    object Wildcard : PatternTree {
        override fun toString(): String = "_"
    }
}

internal fun deconstructArendExpr(expr: ArendExpr): PatternTree {
    if (expr.text == "_") return PatternTree.Wildcard
    val concrete = appExprToConcrete(removeParens(expr))!!
    return concrete.accept(object : BaseConcreteExpressionVisitor<Unit>() {
        override fun visitApp(expr: Concrete.AppExpression, params: Unit): Concrete.Expression {
            val function = expr.function.accept(this, Unit).data as PatternTree
            val args = expr.arguments.map2Array {
                it.expression.accept(
                    this,
                    Unit
                ).data as PatternTree to explicitnessFromBoolean(it.isExplicit)
            }
            return Concrete.HoleExpression(
                PatternTree.BranchingNode(
                    listOf(
                        function to PatternTree.Implicitness.EXPLICIT,
                        *args
                    )
                )
            )
        }

        override fun visitHole(expr: Concrete.HoleExpression, params: Unit?): Concrete.Expression {
            val data = expr.data as ArendExpr
            return if (data.text == "_") {
                Concrete.HoleExpression(PatternTree.Wildcard)
            } else {
                Concrete.HoleExpression(deconstructArendExpr(data))
            }
        }

        override fun visitReference(expr: Concrete.ReferenceExpression, params: Unit?): Concrete.Expression {
            return Concrete.HoleExpression(PatternTree.LeafNode(expr.data.castSafelyTo<ArendCompositeElement>()!!.text.split(".")))
        }
    }, Unit).data as PatternTree
}

private tailrec fun removeParens(expr : ArendExpr) : ArendExpr {
    val innerTuple = expr.childOfType<ArendTuple>()?.takeIf { it.startOffset == expr.startOffset && it.endOffset == expr.endOffset } ?: return expr
    val innerExpr = innerTuple.tupleExprList.singleOrNull()?.exprList?.singleOrNull() ?: return expr
    return removeParens(innerExpr)
}

private fun explicitnessFromBoolean(explicit: Boolean): PatternTree.Implicitness =
    if (explicit) PatternTree.Implicitness.EXPLICIT else PatternTree.Implicitness.IMPLICIT
