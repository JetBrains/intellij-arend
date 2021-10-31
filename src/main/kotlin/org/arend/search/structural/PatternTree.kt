package org.arend.search.structural

import com.intellij.openapi.util.Key
import com.intellij.refactoring.suggested.startOffset
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
 * searched expression is not a valid Arend PSI element. We use dummy PSI element just for compatibility with the
 * Structural Search API, while the actual pattern object is a tree of symbols.
 * Pattern of the Proof Search contains infix symbols that cannot be completely resolved at the time of its writing.
 */
internal sealed interface PatternTree {

    @JvmInline
    value class BranchingNode(val subNodes: List<PatternTree>) : PatternTree

    @JvmInline
    value class LeafNode(val element: ArendCompositeElement) : PatternTree

    object Wildcard : PatternTree
}

internal fun deconstructArendExpr(expr: ArendExpr): PatternTree {
    val concrete = appExprToConcrete(removeParens(expr))!!
    return concrete.accept(object : BaseConcreteExpressionVisitor<Unit>() {
        override fun visitApp(expr: Concrete.AppExpression, params: Unit): Concrete.Expression {
            val function = expr.function.accept(this, Unit).data as PatternTree
            val args = expr.arguments.map2Array { it.expression.accept(this, Unit).data as PatternTree }
            return Concrete.HoleExpression(PatternTree.BranchingNode(listOf(function, *args)))
        }

        override fun visitHole(expr: Concrete.HoleExpression, params: Unit?): Concrete.Expression {
            val data = expr.data as ArendExpr
            return if (data.text == "_") {
                Concrete.HoleExpression(PatternTree.Wildcard)
            }else {
                Concrete.HoleExpression(deconstructArendExpr(data))
            }
        }

        override fun visitReference(expr: Concrete.ReferenceExpression, params: Unit?): Concrete.Expression {
            return Concrete.HoleExpression(PatternTree.LeafNode(expr.data as ArendCompositeElement))
        }
    }, Unit).data as PatternTree
}

private tailrec fun removeParens(expr : ArendExpr) : ArendExpr {
    val innerTuple = expr.childOfType<ArendTuple>()?.takeIf { it.startOffset == expr.startOffset } ?: return expr
    val innerExpr = innerTuple.tupleExprList.singleOrNull()?.exprList?.singleOrNull() ?: return expr
    return removeParens(innerExpr)
}

internal val PATTERN_TREE = Key<PatternTree>("arend.structural.search.pattern.tree")

/**
 * Use only in context of structural search
 */
internal fun ArendExpr.getPatternTree() : PatternTree = this.getUserData(PATTERN_TREE)!!

internal fun ArendExpr.setPatternTree(tree: PatternTree) = this.putUserData(PATTERN_TREE, tree)
