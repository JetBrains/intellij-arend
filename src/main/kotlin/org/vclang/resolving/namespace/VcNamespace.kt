package org.vclang.resolving.namespace

import com.jetbrains.jetpad.vclang.error.GeneralError
import com.jetbrains.jetpad.vclang.naming.namespace.Namespace

/*  TODO[abstract]
class VcNamespace : Namespace {
    private val names = mutableMapOf<String, Abstract.Definition>()

    fun addDefinition(definition: Abstract.Definition) {
        addDefinition(definition.name!!, definition)
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

    override fun getElements(): Set<String> = names.keys

    override fun resolveName(name: String): Abstract.Definition? = names[name]
}
*/
