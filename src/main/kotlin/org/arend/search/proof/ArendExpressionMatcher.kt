package org.arend.search.proof

import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import org.arend.error.DummyErrorReporter
import org.arend.naming.binOp.ExpressionBinOpEngine
import org.arend.naming.reference.LocatedReferable
import org.arend.naming.reference.Referable
import org.arend.naming.resolving.typing.TypingInfo
import org.arend.naming.scope.CachingScope
import org.arend.naming.scope.Scope
import org.arend.psi.ArendElementTypes.*
import org.arend.psi.childrenWithLeaves
import org.arend.psi.ext.PsiReferable
import org.arend.term.Fixity
import org.arend.term.concrete.Concrete
import org.arend.term.concrete.Concrete.NumericLiteral
import org.arend.term.prettyprint.FreeVariableCollectorConcrete
import java.math.BigInteger

internal class ArendExpressionMatcher(private val query: ProofSearchQuery) {

    /**
     * @return null if the signature cannot be matched against [query],
     * list of concrete nodes where the matching occurred otherwise
     */
    fun match(parameters: List<Concrete.Expression>, codomain: Concrete.Expression, scope: Scope, def: PsiReferable): ProofSearchMatchingResult? {
        val cachingScope = CachingScope.make(scope)
        val qualifiedReferables = lazy(LazyThreadSafetyMode.NONE) {
            val set = mutableSetOf<Referable>()
            codomain.accept(FreeVariableCollectorConcrete(set), null)
            set.groupBy { it.refName }
        }
        val codomainResult = matchDisjunct(query.codomain, codomain, cachingScope, qualifiedReferables, def) ?: return null
        if (parameters.isEmpty()) {
            return codomainResult.takeIf { query.parameters.isEmpty() }?.let { ProofSearchMatchingResult(emptyList(), it) }
        }
        val parameterResults = mutableListOf<Pair<Concrete.Expression, List<Concrete.Expression>>>()
        val usedParameters = mutableSetOf<Concrete.Expression>()

        loop@ for (patternParameter in query.parameters) {
            for (matchParameter in parameters) {
                if (usedParameters.contains(matchParameter)) {
                    continue
                }
                val match = matchDisjunct(patternParameter, matchParameter, cachingScope, qualifiedReferables, def)
                if (match != null) {
                    usedParameters.add(matchParameter)
                    parameterResults.add(matchParameter to match)
                    continue@loop
                }
            }
            return null
        }
        return ProofSearchMatchingResult(parameterResults, codomainResult)
    }

    private fun matchDisjunct(jointPattern: ProofSearchJointPattern, concrete: Concrete.Expression, scope: Scope, referables: Lazy<Map<String, List<Referable>>>, def: PsiReferable) : List<Concrete.Expression>? {
        return jointPattern.patterns.flatMap {
            val patternConcrete = reassembleConcrete(it, scope, referables, def) ?: return@matchDisjunct null
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
        } else if (pattern is NumericLiteral && matched is NumericLiteral && pattern.number == matched.number) {
            return true
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
        references: Lazy<Map<String, List<Referable>>>,
        def: PsiReferable
    ): Concrete.Expression? =
        when (tree) {
            is PatternTree.BranchingNode -> {
                val binOpList = ArrayList<Concrete.BinOpSequenceElem<Concrete.Expression>>(tree.subNodes.size)
                for (i in tree.subNodes.indices) {
                    val expr = reassembleConcrete(tree.subNodes[i].first, scope, references, def) ?: break
                    val explicitness = tree.subNodes[i].second.toBoolean()
                    val binOp = Concrete.BinOpSequenceElem(expr, if (i == 0 || expr !is Concrete.ReferenceExpression) Fixity.NONFIX else Fixity.UNKNOWN, explicitness)
                    binOpList.add(binOp)
                }
                if (binOpList.size != tree.subNodes.size) {
                    null
                } else {
                    ExpressionBinOpEngine.parse(Concrete.BinOpSequenceExpression(null, binOpList, null), DummyErrorReporter.INSTANCE, TypingInfo.EMPTY)
                }
            }
            is PatternTree.LeafNode -> {
                val referable = Scope.resolveName(scope, tree.referenceName)
                    ?: references.value[tree.referenceName.last()]?.let { disambiguate(it, tree.referenceName) }
                if (referable != null) {
                    val refExpr = Concrete.FixityReferenceExpression.make(null, referable, Fixity.UNKNOWN, null, null)
                    refExpr ?: Concrete.HoleExpression(tree.referenceName)
                } else if (tree.referenceName.getOrNull(0)?.toIntOrNull() != null) {
                    val number = tree.referenceName[0].toInt()
                    findNumericalArgument(def, number)
                } else {
                    null
                }
            }
            PatternTree.Wildcard -> Concrete.HoleExpression(null)
        }
}

private fun findNumericalArgument(element: PsiElement, number: Int): Concrete.Expression? {
    if ((element.elementType == NUMBER || element.elementType == NEGATIVE_NUMBER) && element.text.toInt() == number) {
        return NumericLiteral(null, BigInteger.valueOf(number.toLong()))
    }
    for (child in element.childrenWithLeaves) {
        val result = findNumericalArgument(child, number)
        if (result != null) {
            return result
        }
    }
    return null
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
        val location = (candidate as? LocatedReferable)?.location?.modulePath?.toList() ?: emptyList()
        val longName = candidate.refLongName?.toList() ?: continue
        val actualLongName = location + longName
        if (actualLongName.last() != path.last()) {
            return null
        }
        var pathIndex = 0
        for (fullPathPart in actualLongName) {
            if (pathIndex >= path.lastIndex) {
                break
            }
            if (fullPathPart == path[pathIndex]) {
                pathIndex += 1
            }
        }
        if (pathIndex < path.lastIndex) {
            return null
        } else if (result == null) {
            result = candidate
        } else {
            // there are two referables with the same suffix, it is ambiguous
            return null
        }
    }
    return result
}


internal data class ProofSearchMatchingResult(val inPattern: List<Pair<Concrete.Expression, List<Concrete.Expression>>>, val inCodomain: List<Concrete.Expression>)
