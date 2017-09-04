package org.vclang.resolve.namespace

import com.jetbrains.jetpad.vclang.naming.namespace.ModuleNamespace
import com.jetbrains.jetpad.vclang.term.Abstract

class VcModuleNamespace : ModuleNamespace {
    private val submoduleNamespaces = mutableMapOf<String, VcModuleNamespace>()
    private var registeredClass: Abstract.ClassDefinition? = null

    override fun getNames(): Set<String> = submoduleNamespaces.keys

    override fun getSubmoduleNamespace(submodule: String): VcModuleNamespace? =
            submoduleNamespaces[submodule]

    override fun getRegisteredClass(): Abstract.ClassDefinition? = registeredClass

    internal fun registerClass(module: Abstract.ClassDefinition) {
        check(registeredClass == null) { "Class already registered" }
        registeredClass = module
    }

    internal fun unregisterClass() {
        checkNotNull(registeredClass) { "Cannot unregister unregistered class" }
        registeredClass = null
    }

    internal fun ensureSubmoduleNamespace(submodule: String): VcModuleNamespace =
            submoduleNamespaces.computeIfAbsent(submodule) { VcModuleNamespace() }
}
