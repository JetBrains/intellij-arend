package org.vclang.lang.core.resolve.namespace

import com.jetbrains.jetpad.vclang.frontend.namespace.ModuleRegistry
import com.jetbrains.jetpad.vclang.module.ModulePath
import com.jetbrains.jetpad.vclang.naming.namespace.ModuleNamespace
import com.jetbrains.jetpad.vclang.naming.namespace.ModuleNamespaceProvider
import com.jetbrains.jetpad.vclang.term.Abstract

class VcModuleNamespaceProvider : ModuleNamespaceProvider, ModuleRegistry {
    private val registered = mutableMapOf<Abstract.ClassDefinition, ModuleNamespace>()
    private var root = VcModuleNamespace()

    override fun forModule(definition: Abstract.ClassDefinition): ModuleNamespace? =
            registered[definition]

    override fun root(): VcModuleNamespace = root

    override fun registerModule(
            modulePath: ModulePath,
            module: Abstract.ClassDefinition
    ): ModuleNamespace {
        val namespace = registerModuleNamespace(modulePath, module)
        namespace.registerClass(module)
        return namespace
    }

    override fun unregisterModule(path: ModulePath) {
        val namespace = ensureModuleNamespace(root(), path)
        namespace.unregisterClass()
    }

    fun unregisterAllModules() {
        registered.clear()
        root = VcModuleNamespace()
    }

    private fun registerModuleNamespace(
            modulePath: ModulePath,
            module: Abstract.ClassDefinition
    ): VcModuleNamespace {
        check(registered[module] == null) { "Module namespace already registered" }
        val namespace = ensureModuleNamespace(root(), modulePath)
        registered.put(module, namespace)
        return namespace
    }

    companion object {
        fun ensureModuleNamespace(
                rootNamespace: VcModuleNamespace,
                modulePath: ModulePath
        ): VcModuleNamespace {
            if (modulePath.toList().isEmpty()) {
                return rootNamespace
            }
            val parentNamespace = ensureModuleNamespace(rootNamespace, modulePath.parent)
            return parentNamespace.ensureSubmoduleNamespace(modulePath.name)
        }
    }
}
