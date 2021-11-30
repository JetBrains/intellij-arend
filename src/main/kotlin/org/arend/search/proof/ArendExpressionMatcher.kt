package org.arend.search.proof

import com.intellij.psi.util.parentOfType
import org.arend.error.DummyErrorReporter
import org.arend.naming.BinOpParser
import org.arend.naming.reference.AliasReferable
import org.arend.naming.reference.Referable
import org.arend.naming.scope.CachingScope
import org.arend.naming.scope.Scope
import org.arend.psi.ArendExpr
import org.arend.psi.ext.impl.CoClauseDefAdapter
import org.arend.psi.ext.impl.DefinitionAdapter
import org.arend.psi.ext.impl.FunctionDefinitionAdapter
import org.arend.term.Fixity
import org.arend.term.concrete.Concrete
import org.arend.term.prettyprint.FreeVariableCollectorConcrete
import org.arend.util.appExprToConcrete

internal class ArendExpressionMatcher(val tree: PatternTree) {
    val binOpParser = BinOpParser(DummyErrorReporter.INSTANCE)

    fun match(expr: ArendExpr): Boolean {
        val referable = expr.parentOfType<DefinitionAdapter<*>>() ?: return false
        val concrete = getType(referable) ?: return false
        val mscope = CachingScope.make(expr.scope)
        val parsedConcrete =
            if (concrete is Concrete.BinOpSequenceExpression) binOpParser.parse(concrete) else concrete
        val qualifiedReferables by lazy(LazyThreadSafetyMode.NONE) {
            val set = mutableSetOf<Referable>()
            parsedConcrete.accept(FreeVariableCollectorConcrete(set), null)
            set.groupBy { it.refName }
        }
        val patternConcrete = reassembleConcrete(tree, mscope, qualifiedReferables) ?: return false
        return performMatch(patternConcrete, parsedConcrete)
    }


    private fun performMatch(
        pattern: Concrete.Expression,
        matched: Concrete.Expression
    ): Boolean {
        if (performTopMatch(pattern, matched)) {
            return true
        }
        if (matched is Concrete.AppExpression) {
            if (performMatch(pattern, matched.function)) {
                return true
            }
            for (arg in matched.arguments) {
                if (performMatch(pattern, arg.expression)) {
                    return true
                }
            }
        }
        return false
    }

    private fun performTopMatch(pattern: Concrete.Expression,
                                matched: Concrete.Expression) : Boolean {
        if (pattern is Concrete.HoleExpression) {
            return true
        }
        if (pattern is Concrete.AppExpression && matched is Concrete.AppExpression) {
            val patternFunction = pattern.function
            val matchedFunction = matched.function
            val concreteArguments = matched.arguments.mapNotNull { if (it.isExplicit) it.expression else null }
            if (!performMatch(patternFunction, matchedFunction)) {
                return false
            }
            val patternArguments = pattern.arguments.map { it.expression }
            if (patternArguments.size != concreteArguments.size) {
                return false
            }
            for ((patternArg, matchedArg) in patternArguments.zip(concreteArguments)) {
                if (!performMatch(patternArg, matchedArg)) {
                    return false
                }
            }
            return true
        } else if (pattern is Concrete.ReferenceExpression && matched is Concrete.ReferenceExpression) {
            return pattern.underlyingReferable?.resolveAlias() == matched.underlyingReferable?.resolveAlias()
        } else {
            return false
        }
    }

    private fun reassembleConcrete(
        tree: PatternTree,
        scope: Scope,
        references: Map<String, List<Referable>>
    ): Concrete.Expression? =
        when (tree) {
            is PatternTree.BranchingNode -> {
                val binOpList = ArrayList<Concrete.BinOpSequenceElem>(tree.subNodes.size)
                for (i in tree.subNodes.indices) {
                    val expr = reassembleConcrete(tree.subNodes[i].first, scope, references) ?: break
                    val explicitness = tree.subNodes[i].second.toBoolean()
                    val binOp = if (i == 0) {
                        Concrete.BinOpSequenceElem(expr, Fixity.NONFIX, explicitness)
                    } else {
                        Concrete.BinOpSequenceElem(expr, Fixity.UNKNOWN, explicitness)
                    }
                    binOpList.add(binOp)
                }
                if (binOpList.size != tree.subNodes.size) {
                    null
                } else {
                    binOpParser.parse(Concrete.BinOpSequenceExpression(null, binOpList, null))
                }
            }
            is PatternTree.LeafNode -> {
                val referable = Scope.resolveName(scope, tree.referenceName, false)
                    ?: references[tree.referenceName.last()]?.let { disambiguate(it, tree.referenceName) }
                if (referable != null) {
                    val refExpr = Concrete.FixityReferenceExpression.make(null, referable, Fixity.UNKNOWN, null, null)
                    refExpr ?: Concrete.HoleExpression(tree.referenceName)
                } else {
                    null
                }
            }
            PatternTree.Wildcard -> Concrete.HoleExpression(null)
        }
}


private fun getType(def: DefinitionAdapter<*>): Concrete.Expression? {
    // todo: reuse core definitions where possible to achieve matching with explicitly untyped definitions and implicit arguments
    return when (def) {
        is CoClauseDefAdapter -> def.resultType?.let(::appExprToConcrete)
        is FunctionDefinitionAdapter -> def.resultType?.let(::appExprToConcrete)
        else -> null
    }
}

private fun disambiguate(candidates: List<Referable>, path: List<String>): Referable? {
    var result: Referable? = null
    for (candidate in candidates) {
        val longName = candidate.refLongName ?: continue
        if (longName.toList().subList(longName.size() - path.size, longName.size()) == path) {
            if (result == null) {
                result = candidate
            } else {
                // there are two referables with the same suffix, it is ambiguous
                return null
            }
        }
    }
    return result
}

private fun Referable.resolveAlias(): Referable = if (this is AliasReferable) underlyingReferable else this