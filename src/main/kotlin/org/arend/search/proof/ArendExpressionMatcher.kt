package org.arend.search.proof

import org.arend.error.DummyErrorReporter
import org.arend.naming.BinOpParser
import org.arend.naming.reference.Referable
import org.arend.naming.scope.CachingScope
import org.arend.naming.scope.Scope
import org.arend.term.Fixity
import org.arend.term.concrete.Concrete
import org.arend.term.prettyprint.FreeVariableCollectorConcrete

internal class ArendExpressionMatcher(private val query: ProofSearchQuery) {

    fun match(parameters: List<Concrete.Expression>, codomain: Concrete.Expression, scope: Scope): Boolean {
        val cachingScope = CachingScope.make(scope)
        val qualifiedReferables = lazy(LazyThreadSafetyMode.NONE) {
            val set = mutableSetOf<Referable>()
            codomain.accept(FreeVariableCollectorConcrete(set), null)
            set.groupBy { it.refName }
        }
        if (!matchDisjunct(query.codomain, codomain, cachingScope, qualifiedReferables)) return false
        if (parameters.isEmpty()) {
            return query.parameters.isEmpty()
        }
        for (patternParameter in query.parameters) {
            if (parameters.all { !matchDisjunct(patternParameter, it, cachingScope, qualifiedReferables) }) return false
        }
        return true
    }

    private fun matchDisjunct(pattern: ProofSearchJointPattern, concrete: Concrete.Expression, scope: Scope, referables: Lazy<Map<String, List<Referable>>>) : Boolean {
        return pattern.patterns.all {
            val patternConcrete = reassembleConcrete(it, scope, referables) ?: return@all false
            performMatch(patternConcrete, concrete)
        }
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
        if (matched is Concrete.SigmaExpression) {
            for (projection in matched.parameters) {
                if (performMatch(pattern, projection.type)) {
                    return true
                }
            }
        }
        if (matched is Concrete.PiExpression) {
            return performMatch(pattern, matched.codomain) || matched.parameters.all { performMatch(pattern,  it.type) }
        }
        if (matched is Concrete.LetExpression) {
            for (clause in matched.clauses) {
                if (performMatch(pattern, clause.term)) {
                    return true
                }
            }
            if (performMatch(pattern, matched.expression)) {
                return true
            }
        }
        if (matched is Concrete.LamExpression) {
            return performMatch(pattern, matched.body)
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
            val mapping = doubleArgumentIterable(pattern.arguments, matched.arguments) ?: return false
            if (!performTopMatch(patternFunction, matchedFunction)) {
                return false
            }
            for ((patternArg, matchedArg) in mapping) {
                if (!performTopMatch(patternArg, matchedArg)) {
                    return false
                }
            }
            return true
        } else if (pattern is Concrete.ReferenceExpression && matched is Concrete.ReferenceExpression) {
            return pattern.underlyingReferable?.recursiveReferable() == matched.underlyingReferable?.recursiveReferable()
        } else {
            return false
        }
    }

    private fun Referable.recursiveReferable(): Referable {
        val underlying = underlyingReferable
        return if (underlying === this) this else underlying.recursiveReferable()
    }

    private fun reassembleConcrete(
        tree: PatternTree,
        scope: Scope,
        references: Lazy<Map<String, List<Referable>>>
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
                    ?: references.value[tree.referenceName.last()]?.let { disambiguate(it, tree.referenceName) }
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

private fun doubleArgumentIterable(patternArguments : List<Concrete.Argument>, matchArguments : List<Concrete.Argument>) : Iterable<Pair<Concrete.Expression, Concrete.Expression>>? {
    var indexInMatch = 0
    val container : MutableList<Pair<Concrete.Expression, Concrete.Expression>> = mutableListOf()
    for (patternArg in patternArguments) {
        if (indexInMatch == matchArguments.size) {
            return null
        }
        if (patternArg.isExplicit) {
            while (!matchArguments[indexInMatch].isExplicit) {
                indexInMatch += 1
                if (indexInMatch == matchArguments.size) {
                    return null
                }
            }
        }
        if (patternArg.isExplicit != matchArguments[indexInMatch].isExplicit) {
            return null
        }
        container.add(patternArg.expression to matchArguments[indexInMatch].expression)
        indexInMatch += 1
    }
    return container
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


private val binOpParser = BinOpParser(DummyErrorReporter.INSTANCE)