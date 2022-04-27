package org.arend.injection.actions

import org.arend.core.expr.Expression
import org.arend.ext.core.ops.NormalizationMode

class NormalizationCache {
    private val innerCache: MutableMap<Expression, Expression> = mutableMapOf()

    fun getNormalizedExpression(expr: Expression) : Expression {
        return if (innerCache.values.any { expr === it}) expr // referential equality is IMPORTANT. equals considers terms to be equal up to normal form. Here we explicitly distinguish nf and non-nf
        else innerCache.computeIfAbsent(expr) { it.normalize(NormalizationMode.RNF) }
    }

    internal fun enrich(other: NormalizationCache) {
        innerCache.putAll(other.innerCache)
    }
}