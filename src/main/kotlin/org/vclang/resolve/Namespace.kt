package org.vclang.resolve

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import org.vclang.VcIcons
import org.vclang.psi.ext.VcCompositeElement
import org.vclang.psi.ext.VcNamedElement

interface Namespace {
    val symbols: Set<LookupElement>

    fun resolve(name: String): VcCompositeElement?
}

object EmptyNamespace : Namespace {
    override val symbols: Set<LookupElement> = emptySet()

    override fun resolve(name: String): VcCompositeElement? = null
}

class SimpleNamespace : Namespace {
    private val items: MutableMap<String, VcCompositeElement> = mutableMapOf()

    override val symbols: Set<LookupElement>
        get() = items.values.map {
            if (it is VcNamedElement) {
                LookupElementBuilder.createWithIcon(it)
            } else {
                LookupElementBuilder.create(it)
            }
        }.toSet()

    override fun resolve(name: String): VcCompositeElement? = items[name]

    fun put(definition: VcNamedElement?): VcCompositeElement? =
            definition?.name?.let { items.put(it, definition) }

    fun put(name: String, definition: VcCompositeElement): VcCompositeElement? =
            items.put(name, definition)

    fun putAll(other: SimpleNamespace) = items.putAll(other.items)
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
