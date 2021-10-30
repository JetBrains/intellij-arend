package org.arend.search.structural

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.suggested.startOffset
import com.intellij.structuralsearch.impl.matcher.GlobalMatchingVisitor
import com.intellij.util.containers.map2Array
import org.arend.psi.*
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.ArendFunctionalDefinition
import org.arend.psi.ext.ArendReferenceContainer
import org.arend.term.concrete.BaseConcreteExpressionVisitor
import org.arend.term.concrete.Concrete
import org.arend.util.appExprToConcrete

class ArendMatchingVisitor(private val matchingVisitor: GlobalMatchingVisitor) : ArendVisitor() {

    private sealed interface PatternTree

    @JvmInline
    private value class BranchingNode(val subNodes: List<PatternTree>) : PatternTree

    @JvmInline
    private value class LeafNode(val element: ArendCompositeElement) : PatternTree


    private fun deconstructArendExpr(expr: ArendExpr): PatternTree {
        val concrete = appExprToConcrete(removeParens(expr))!!
        return concrete.accept(object : BaseConcreteExpressionVisitor<Unit>() {
            override fun visitApp(expr: Concrete.AppExpression, params: Unit): Concrete.Expression {
                val function = expr.function.accept(this, Unit).data as PatternTree
                val args = expr.arguments.map2Array { it.expression.accept(this, Unit).data as PatternTree }
                return Concrete.HoleExpression(BranchingNode(listOf(function, *args)))
            }

            override fun visitHole(expr: Concrete.HoleExpression, params: Unit?): Concrete.Expression {
                val data = expr.data as ArendExpr
                return if (data.text == "_") {
                    Concrete.HoleExpression(LeafNode(data))
                }else {
                    Concrete.HoleExpression(deconstructArendExpr(data))
                }
            }

            override fun visitReference(expr: Concrete.ReferenceExpression, params: Unit?): Concrete.Expression {
                return Concrete.HoleExpression(LeafNode(expr.data as ArendCompositeElement))
            }
        }, Unit).data as PatternTree
    }

    private tailrec fun removeParens(expr : ArendExpr) : ArendExpr {
        val innerTuple = expr.childOfType<ArendTuple>()?.takeIf { it.startOffset == expr.startOffset } ?: return expr
        val innerExpr = innerTuple.tupleExprList.singleOrNull()?.exprList?.singleOrNull() ?: return expr
        return removeParens(innerExpr)
    }

    private fun performMatch(tree: PatternTree, concrete: Concrete.Expression): Boolean {
        if (concrete is Concrete.AppExpression && tree is BranchingNode) {
            val patternFunction = tree.subNodes[0]
            val matchFunction = concrete.function
            if (!performMatch(patternFunction, matchFunction)) {
                return false
            }
            val concreteArguments = concrete.arguments.mapNotNull { if (it.isExplicit) it.expression else null }
            val patternArguments = tree.subNodes.subList(1, tree.subNodes.size)
            if (patternArguments.size != concreteArguments.size) {
                return false
            }
            for ((pattern, matched) in patternArguments.zip(concreteArguments)) {
                if (!performMatch(pattern, matched)) {
                    return false
                }
            }
            return true
        } else if ((concrete is Concrete.HoleExpression || concrete is Concrete.ReferenceExpression) && tree is LeafNode) {
            val patternElement = tree.element
            val matchElement = concrete.data as ArendCompositeElement
            if (patternElement.text.equals("_")) {
                return true
            }
            if (patternElement.text.equals(matchElement.text)) {
                return true
            }
            val matchName = if (matchElement is ArendReferenceContainer) matchElement.referenceName else null
            if (patternElement.text.equals(matchName)) {
                return true
            }
            return false
        } else {
            return false
        }
    }


    override fun visitExpr(o: ArendExpr) {
        super.visitExpr(o)
        val matchedElement = this.matchingVisitor.element
        matchingVisitor.result = false
        val parentType = matchedElement.parentOfType<ArendFunctionalDefinition>()?.returnExpr?.exprList?.getOrNull(0) ?: return
        if (!PsiTreeUtil.isAncestor(parentType, matchedElement, false)) {
            return
        }
        if (matchedElement is ArendArgumentAppExpr) {
            val patternTree = deconstructArendExpr(o)
            val concrete = appExprToConcrete(matchedElement) ?: return
            if (performMatch(patternTree, concrete)) {
                matchingVisitor.result = true
            }
        }
    }
}