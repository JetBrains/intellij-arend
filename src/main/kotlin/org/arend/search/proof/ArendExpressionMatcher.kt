package org.arend.search.proof

import org.arend.core.definition.Constructor
import org.arend.core.definition.Definition
import org.arend.core.definition.FunctionDefinition
import org.arend.core.expr.Expression
import org.arend.core.subst.LevelPair
import org.arend.error.DummyErrorReporter
import org.arend.ext.prettyprinting.PrettyPrinterConfig
import org.arend.naming.BinOpParser
import org.arend.naming.reference.Referable
import org.arend.naming.scope.CachingScope
import org.arend.naming.scope.Scope
import org.arend.term.Fixity
import org.arend.term.concrete.Concrete
import org.arend.term.prettyprint.FreeVariableCollectorConcrete
import org.arend.term.prettyprint.ToAbstractVisitor

internal class ArendExpressionMatcher(val tree: PatternTree) {

    fun match(coreDefinition: Definition, scope: Scope): Boolean {
        val resultType = getType(coreDefinition) ?: return false
        val cachingScope = CachingScope.make(scope)
        val completeConcrete = ToAbstractVisitor.convert(resultType, PrettyPrinterConfig.DEFAULT)
        val qualifiedReferables by lazy(LazyThreadSafetyMode.NONE) {
            val set = mutableSetOf<Referable>()
            completeConcrete.accept(FreeVariableCollectorConcrete(set), null)
            set.groupBy { it.refName }
        }
        val patternConcrete = reassembleConcrete(tree, cachingScope, qualifiedReferables) ?: return false
        return performMatch(patternConcrete, completeConcrete)
    }

    private fun getType(def: Definition): Expression? = when (def) {
        is FunctionDefinition -> {
            def.resultType
        }
        is Constructor -> {
            def.getDataTypeExpression(LevelPair.STD)
        }
        else -> null
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
            if (!performTopMatch(patternFunction, matchedFunction)) {
                return false
            }
            val patternArguments = pattern.arguments.map { it.expression }
            if (patternArguments.size != concreteArguments.size) {
                return false
            }
            for ((patternArg, matchedArg) in patternArguments.zip(concreteArguments)) {
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


val binOpParser = BinOpParser(DummyErrorReporter.INSTANCE)