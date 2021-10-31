package org.arend.search.structural

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import com.intellij.structuralsearch.impl.matcher.GlobalMatchingVisitor
import org.arend.error.DummyErrorReporter
import org.arend.naming.BinOpParser
import org.arend.psi.ArendArgumentAppExpr
import org.arend.psi.ArendExpr
import org.arend.psi.ArendVisitor
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.ArendFunctionalDefinition
import org.arend.psi.ext.ArendReferenceContainer
import org.arend.psi.ext.impl.CoClauseDefAdapter
import org.arend.psi.ext.impl.DefinitionAdapter
import org.arend.psi.ext.impl.FunctionDefinitionAdapter
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
            val patternElement = tree.referenceName
            val matchElement = concrete.data as ArendCompositeElement
            if (patternElement == matchElement.text) {
                return true
            }
            val matchName = if (matchElement is ArendReferenceContainer) matchElement.referenceName else null
            if (patternElement == matchName) {
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
            val referable = matchedElement.parentOfType<DefinitionAdapter<*>>() ?: return
            val concrete = getType(referable) ?: return
            val patternTree = o.getPatternTree()
            val parsedConcrete =
                if (concrete is Concrete.BinOpSequenceExpression) BinOpParser(DummyErrorReporter.INSTANCE).parse(concrete) else concrete
            if (performMatch(patternTree, parsedConcrete)) {
                matchingVisitor.result = true
            }
        }
    }

    fun getType(def: DefinitionAdapter<*>): Concrete.Expression? {
        // todo: reuse core definitions where possible to achieve matching with explicitly untyped definitions
        return when (def) {
            is CoClauseDefAdapter -> def.resultType?.let(::appExprToConcrete)
            is FunctionDefinitionAdapter -> def.resultType?.let(::appExprToConcrete)
            else -> null
        }
    }
}