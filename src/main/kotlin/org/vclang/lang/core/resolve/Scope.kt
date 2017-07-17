package org.vclang.lang.core.resolve

import org.vclang.lang.core.psi.ext.VcCompositeElement

interface Scope {
    val names: Set<String>

    fun resolve(name: String): VcCompositeElement?

    class InvalidScopeException : RuntimeException()
}

class FilteredScope(
        private val scope: Scope,
        private val predicateSet: Set<String>,
        private val include: Boolean
) : Scope {
    override val names: Set<String>
        get() = if (include) (scope.names `intersect` predicateSet) else (scope.names - predicateSet)

    override fun resolve(name: String): VcCompositeElement? = if (include) {
        if (predicateSet.contains(name)) scope.resolve(name) else null
    } else {
        if (predicateSet.contains(name)) null else scope.resolve(name)
    }
}

class MergeScope(private val scope1: Scope, private val scope2: Scope) : Scope {
    override val names: Set<String>
        get() = scope1.names + scope2.names

    override fun resolve(name: String): VcCompositeElement? =
            choose(scope1.resolve(name), scope2.resolve(name))

    private fun <T : VcCompositeElement> choose(ref1: T?, ref2: T?): T? {
        ref1 ?: return ref2
        ref2 ?: return ref1
        throw Scope.InvalidScopeException()
    }
}

object EmptyScope : Scope {
    override val names: Set<String> = emptySet()

    override fun resolve(name: String): VcCompositeElement? = null
}

class NamespaceScope(namespace: Namespace) : Scope, Namespace by namespace

class OverridingScope(private val parent: Scope, private val child: Scope) : Scope {
    override val names: Set<String>
        get() = parent.names + child.names

    override fun resolve(name: String): VcCompositeElement? =
            child.resolve(name) ?: parent.resolve(name)
}
