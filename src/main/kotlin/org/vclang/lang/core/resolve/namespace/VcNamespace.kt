package org.vclang.lang.core.resolve.namespace

import com.jetbrains.jetpad.vclang.error.GeneralError
import com.jetbrains.jetpad.vclang.naming.error.DuplicateDefinitionError
import com.jetbrains.jetpad.vclang.naming.namespace.Namespace
import com.jetbrains.jetpad.vclang.term.Abstract

class VcNamespace() : Namespace {
    private val names = mutableMapOf<String, Abstract.Definition>()

    constructor(other: VcNamespace) : this() {
        names.putAll(other.names)
    }

    constructor(definition: Abstract.Definition) : this() {
        addDefinition(definition)
    }

    fun addDefinition(definition: Abstract.Definition) {
        addDefinition(definition.name, definition)
    }

    fun addDefinition(name: String, definition: Abstract.Definition) {
        val previous = names.put(name, definition)
        if (!(previous == null || previous === definition)) {
            throw object : Namespace.InvalidNamespaceException() {
                override fun toError(): GeneralError {
                    return DuplicateDefinitionError(previous, definition)
                }
            }
        }
    }

    fun addAll(other: VcNamespace) {
        for ((key, value) in other.names) {
            addDefinition(key, value)
        }
    }

    override fun getNames(): Set<String> = names.keys

    override fun resolveName(name: String): Abstract.Definition? = names[name]
}
