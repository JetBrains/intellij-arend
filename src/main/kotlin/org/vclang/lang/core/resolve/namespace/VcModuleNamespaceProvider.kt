package org.vclang.lang.core.resolve.namespace

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import com.jetbrains.jetpad.vclang.frontend.namespace.ModuleRegistry
import com.jetbrains.jetpad.vclang.module.ModulePath
import com.jetbrains.jetpad.vclang.naming.namespace.ModuleNamespace
import com.jetbrains.jetpad.vclang.naming.namespace.ModuleNamespaceProvider
import com.jetbrains.jetpad.vclang.term.Abstract
import org.vclang.lang.core.psi.VcFile
import java.util.*

class VcModuleNamespaceProvider : ModuleNamespaceProvider, ModuleRegistry {
    private val registered = HashMap<Abstract.ClassDefinition, ModuleNamespace>()
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
        if (registered[module] != null) {
            throw IllegalStateException()
        }
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

class VcSketchyModuleNamespaceProvider(
        project: Project,
        sourceDir: String
) : ModuleNamespaceProvider {
    private var root: WithPreludeModuleNamespace
    private val initializedModules = mutableSetOf<Abstract.Definition>()

    init {
        val virtualFile = LocalFileSystem.getInstance().findFileByPath(sourceDir)
        val psiFile = virtualFile?.let { PsiManager.getInstance(project).findDirectory(it) }
        val rootModuleNamespace = psiFile?.let { VcSketchyModuleNamespace(this, psiFile) }
        root = WithPreludeModuleNamespace(rootModuleNamespace)
    }

    override fun forModule(definition: Abstract.ClassDefinition): ModuleNamespace? =
            (definition as? VcFile)?.let { VcSketchyModuleNamespace(this, it) }

    override fun root(): ModuleNamespace? = root

    fun registerModule(module: Abstract.ClassDefinition) = initializedModules.add(module)

    fun unregisterModule(module: Abstract.ClassDefinition) = initializedModules.remove(module)

    fun unregisterAllModules() = initializedModules.clear()

    fun isRegistered(module: Abstract.ClassDefinition) = initializedModules.contains(module)

    fun setPrelude(prelude: VcFile) {
        root.prelude = VcSketchyModuleNamespace(this, prelude)
        initializedModules.add(prelude)
    }

    private class WithPreludeModuleNamespace(
            var module: ModuleNamespace? = null,
            var prelude: ModuleNamespace? = null
    ): ModuleNamespace {

        override fun getRegisteredClass(): Abstract.ClassDefinition? = module?.registeredClass

        override fun getNames(): Set<String> = (module?.names ?: emptySet()) + "Prelude"

        override fun getSubmoduleNamespace(submodule: String?): ModuleNamespace? {
            return if (submodule == "Prelude") {
                prelude
            } else {
                module?.getSubmoduleNamespace(submodule)
            }
        }
    }
}
