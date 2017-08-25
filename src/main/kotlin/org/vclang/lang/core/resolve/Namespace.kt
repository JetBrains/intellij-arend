package org.vclang.lang.core.resolve

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import org.vclang.lang.core.psi.ext.VcCompositeElement
import org.vclang.lang.core.psi.ext.VcNamedElement

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

    fun put(definition: VcNamedElement?) = definition?.name?.let { items.put(it, definition) }

    fun put(name: String, definition: VcCompositeElement) = items.put(name, definition)

    fun putAll(other: SimpleNamespace) = items.putAll(other.items)
}
