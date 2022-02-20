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

    /**
     * @return null if the signature cannot be matched against [query],
     * list of concrete nodes where the matching occurred otherwise
     */
    fun match(parameters: List<Concrete.Expression>, codomain: Concrete.Expression, scope: Scope): List<Concrete.Expression>? {
        val cachingScope = CachingScope.make(scope)
        val qualifiedReferables = lazy(LazyThreadSafetyMode.NONE) {
            val set = mutableSetOf<Referable>()
            codomain.accept(FreeVariableCollectorConcrete(set), null)
            set.groupBy { it.refName }
        }
        val codomainResult = matchDisjunct(query.codomain, codomain, cachingScope, qualifiedReferables) ?: return null
        if (parameters.isEmpty()) {
            return codomainResult.takeIf { query.parameters.isEmpty() }
        }
        val results = mutableListOf(*codomainResult.toTypedArray())
        for (patternParameter in query.parameters) {
            val firstMatchedParameter =
                parameters.firstNotNullOfOrNull { matchDisjunct(patternParameter, it, cachingScope, qualifiedReferables) }
                    ?: return null
            results.addAll(firstMatchedParameter)
        }
        return results
    }

    @Suppress("RedundantNullableReturnType")
    private fun matchDisjunct(jointPattern: ProofSearchJointPattern, concrete: Concrete.Expression, scope: Scope, referables: Lazy<Map<String, List<Referable>>>) : List<Concrete.Expression>? {
        return jointPattern.patterns.flatMap {
            val patternConcrete = reassembleConcrete(it, scope, referables) ?: return@matchDisjunct null
            performMatch(patternConcrete, concrete) ?: return@matchDisjunct null
        }
    }

    private fun performMatch(
        pattern: Concrete.Expression,
        matched: Concrete.Expression
    ): List<Concrete.Expression>? {
        if (performTopMatch(pattern, matched)) {
            return listOf(matched)
        }
        if (matched is Concrete.AppExpression) {
            val prefixMatch = performMatch(pattern, matched.function)
            if (prefixMatch != null) {
                return prefixMatch
            }
            for (arg in matched.arguments) {
                val argMatch = performMatch(pattern, arg.expression)
                if (argMatch != null) {
                    return argMatch
                }
            }
        }
        if (matched is Concrete.SigmaExpression) {
            for (projection in matched.parameters) {
                val projMatch = performMatch(pattern, projection.type)
                if (projMatch != null) {
                    return projMatch
                }
            }
        }
        if (matched is Concrete.PiExpression) {
            val codomainMatch = performMatch(pattern, matched.codomain)
            if (codomainMatch != null) {
                return codomainMatch
            }
            return matched.parameters.firstNotNullOfOrNull { performMatch(pattern, it.type) }
        }
        if (matched is Concrete.LetExpression) {
            for (clause in matched.clauses) {
                val clauseMatch = performMatch(pattern, clause.term)
                if (clauseMatch != null) {
                    return clauseMatch
                }
            }
            val patternMatch = performMatch(pattern, matched.expression)
            if (patternMatch != null) {
                return patternMatch
            }
        }
        if (matched is Concrete.LamExpression) {
            return performMatch(pattern, matched.body)
        }
        return null
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