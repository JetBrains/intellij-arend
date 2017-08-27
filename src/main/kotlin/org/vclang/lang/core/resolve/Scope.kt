package org.vclang.lang.core.resolve

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import org.vclang.ide.icons.VcIcons
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

object PreludeNamespace : Namespace {
    override val symbols: Set<LookupElement>
        get() = setOf(
                LookupElementBuilder.create("Nat").withIcon(VcIcons.DATA_DEFINITION),
                LookupElementBuilder.create("zero").withIcon(VcIcons.CONSTRUCTOR),
                LookupElementBuilder.create("suc").withIcon(VcIcons.CONSTRUCTOR),
                LookupElementBuilder.create("I").withIcon(VcIcons.DATA_DEFINITION),
                LookupElementBuilder.create("left").withIcon(VcIcons.CONSTRUCTOR),
                LookupElementBuilder.create("right").withIcon(VcIcons.CONSTRUCTOR),
                LookupElementBuilder.create("Path").withIcon(VcIcons.DATA_DEFINITION),
                LookupElementBuilder.create("path").withIcon(VcIcons.CONSTRUCTOR),
                LookupElementBuilder.create("=").withIcon(VcIcons.FUNCTION_DEFINITION),
                LookupElementBuilder.create("@").withIcon(VcIcons.FUNCTION_DEFINITION),
                LookupElementBuilder.create("coe").withIcon(VcIcons.FUNCTION_DEFINITION),
                LookupElementBuilder.create("iso").withIcon(VcIcons.FUNCTION_DEFINITION),
                LookupElementBuilder.create("TrP").withIcon(VcIcons.DATA_DEFINITION),
                LookupElementBuilder.create("inP").withIcon(VcIcons.CONSTRUCTOR),
                LookupElementBuilder.create("truncP").withIcon(VcIcons.CONSTRUCTOR),
                LookupElementBuilder.create("TrS").withIcon(VcIcons.DATA_DEFINITION),
                LookupElementBuilder.create("inS").withIcon(VcIcons.CONSTRUCTOR),
                LookupElementBuilder.create("truncS").withIcon(VcIcons.CONSTRUCTOR)
        )

    override fun resolve(name: String): VcCompositeElement? = null
}
