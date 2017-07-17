package org.vclang.lang.core.resolve

import org.vclang.lang.core.psi.ext.VcNamedElement
import org.vclang.lang.core.psi.ext.VcCompositeElement

interface Namespace {
    val names: Set<String>

    fun resolve(name: String): VcCompositeElement?

    class InvalidNamespaceException : RuntimeException()
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

    fun put(def: VcNamedElement) = def.name?.let { put(it, def) }

    fun put(name: String, def: VcCompositeElement) {
        items.put(name, def)?.let {
            if (it !== def) {
                throw Namespace.InvalidNamespaceException()
            }
        }
    }

    fun putAll(other: SimpleNamespace) = items.putAll(other.items)
}
