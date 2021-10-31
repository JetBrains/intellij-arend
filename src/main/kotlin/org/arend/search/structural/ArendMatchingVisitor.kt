package org.arend.search.structural

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.structuralsearch.impl.matcher.GlobalMatchingVisitor
import org.arend.psi.ArendArgumentAppExpr
import org.arend.psi.ArendExpr
import org.arend.psi.ArendVisitor
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.ArendFunctionalDefinition
import org.arend.psi.ext.ArendReferenceContainer
import org.arend.psi.parentOfType
import org.arend.term.concrete.Concrete
import org.arend.util.appExprToConcrete

class ArendMatchingVisitor(private val matchingVisitor: GlobalMatchingVisitor) : ArendVisitor() {

    private fun performMatch(tree: PatternTree, concrete: Concrete.Expression): Boolean {
        if (tree is PatternTree.Wildcard) {
            return true
        }
        if (concrete is Concrete.AppExpression && tree is PatternTree.BranchingNode) {
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
        } else if ((concrete is Concrete.HoleExpression || concrete is Concrete.ReferenceExpression) && tree is PatternTree.LeafNode) {
            val patternElement = tree.element
            val matchElement = concrete.data as ArendCompositeElement
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
            val patternTree = o.getPatternTree()
            val concrete = appExprToConcrete(matchedElement) ?: return
            if (performMatch(patternTree, concrete)) {
                matchingVisitor.result = true
            }
        }
    }
}