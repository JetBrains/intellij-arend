package org.vclang.resolving.namespace

import com.jetbrains.jetpad.vclang.naming.namespace.ModuleNamespace
import com.jetbrains.jetpad.vclang.naming.reference.GlobalReferable
import com.jetbrains.jetpad.vclang.term.Group

class VcModuleNamespace : ModuleNamespace {
    private val submoduleNamespaces = mutableMapOf<String, VcModuleNamespace>()
    private var registeredClass: Group? = null

    override fun getNames(): Set<String> = submoduleNamespaces.keys

    override fun getSubmoduleNamespace(submodule: String): VcModuleNamespace? =
            submoduleNamespaces[submodule]

    override fun getRegisteredClass(): GlobalReferable? = registeredClass?.referable

    internal fun registerClass(module: Group) {
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
