package org.vclang.lang.core.resolve

import org.vclang.lang.core.psi.ext.VcCompositeElement
import org.vclang.lang.core.psi.ext.VcNamedElement

interface Namespace {
    val names: Set<String>

    fun resolve(name: String): VcCompositeElement?
}

object EmptyNamespace : Namespace {
    override val names: Set<String> = emptySet()

    override fun resolve(name: String): VcCompositeElement? = null
}

class SimpleNamespace : Namespace {
    private val items: MutableMap<String, VcCompositeElement> = mutableMapOf()

    override val names: Set<String>
        get() = items.keys

    override fun resolve(name: String): VcCompositeElement? = items[name]

    fun put(definition: VcNamedElement?) = definition?.name?.let { put(it, definition) }

    fun put(name: String, definition: VcCompositeElement) = items.put(name, definition)

    fun putAll(other: SimpleNamespace) = items.putAll(other.items)
}
