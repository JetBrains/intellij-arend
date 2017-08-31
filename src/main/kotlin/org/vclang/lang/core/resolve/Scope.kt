package org.vclang.lang.core.resolve

import com.intellij.codeInsight.lookup.LookupElement
import org.vclang.lang.core.psi.ext.VcCompositeElement

interface Scope {
    val symbols: Set<LookupElement>

    fun resolve(name: String): VcCompositeElement?
}

class FilteredScope(
        private val scope: Scope,
        private val includeSet: Set<String>,
        private val include: Boolean
) : Scope {
    override val symbols: Set<LookupElement>
        get() {
            return if (include) {
                scope.symbols.filter { it.psiElement?.text in includeSet }
            } else {
                scope.symbols.filterNot { it.psiElement?.text in includeSet }
            }.toSet()
        }

    override fun resolve(name: String): VcCompositeElement? = if (include) {
        if (includeSet.contains(name)) scope.resolve(name) else null
    } else {
        if (includeSet.contains(name)) null else scope.resolve(name)
    }
}

class MergeScope(private val scope1: Scope, private val scope2: Scope) : Scope {
    override val symbols: Set<LookupElement>
        get() = scope1.symbols + scope2.symbols

    override fun resolve(name: String): VcCompositeElement? =
            choose(scope1.resolve(name), scope2.resolve(name))

    private fun <T : VcCompositeElement> choose(ref1: T?, ref2: T?): T? = ref1 ?: ref2
}

object EmptyScope : Scope {
    override val symbols: Set<LookupElement> = emptySet()

    override fun resolve(name: String): VcCompositeElement? = null
}

class NamespaceScope(namespace: Namespace) : Scope, Namespace by namespace

class OverridingScope(private val parent: Scope, private val child: Scope) : Scope {
    override val symbols: Set<LookupElement>
        get() = parent.symbols + child.symbols

    override fun resolve(name: String): VcCompositeElement? =
            child.resolve(name) ?: parent.resolve(name)
}

object PreludeScope : Scope, Namespace by PreludeNamespace
