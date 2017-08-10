package org.vclang.lang.core.resolve.namespace

import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFileSystemItem
import com.jetbrains.jetpad.vclang.naming.namespace.ModuleNamespace
import com.jetbrains.jetpad.vclang.term.Abstract
import org.vclang.lang.VcFileType
import org.vclang.lang.VcLanguage
import org.vclang.lang.core.psi.VcFile

class VcModuleNamespace : ModuleNamespace {
    private val submoduleNamespaces = mutableMapOf<String, VcModuleNamespace>()
    private var registeredClass: Abstract.ClassDefinition? = null

    override fun getNames(): Set<String> = submoduleNamespaces.keys

    override fun getSubmoduleNamespace(submodule: String): VcModuleNamespace? =
            submoduleNamespaces[submodule]

    override fun getRegisteredClass(): Abstract.ClassDefinition? = registeredClass

    internal fun registerClass(module: Abstract.ClassDefinition) {
        if (registeredClass != null) throw IllegalStateException()
        registeredClass = module
    }

    internal fun unregisterClass() {
        if (registeredClass == null) throw IllegalStateException()
        registeredClass = null
    }

    internal fun ensureSubmoduleNamespace(submodule: String): VcModuleNamespace =
            submoduleNamespaces.computeIfAbsent(submodule) { VcModuleNamespace() }
}

class VcSketchyModuleNamespace(
        private val provider: VcSketchyModuleNamespaceProvider,
        private val file: PsiFileSystemItem
) : ModuleNamespace {

    override fun getNames(): Set<String> {
        val names = mutableSetOf<String>()
        file.processChildren {
            if (it.isDirectory) {
                names.add(it.name)
            } else if (it.language == VcLanguage) {
                val name = it.name.substring(0, it.name.lastIndexOf('.'))
                names.add(name)
            }
            true
        }
        return names
    }

    override fun getSubmoduleNamespace(submodule: String): ModuleNamespace? {
        if (file is PsiDirectory) {
            file.findSubdirectory(submodule)?.let {
                return VcSketchyModuleNamespace(provider, it)
            }
            file.findFile("$submodule.${VcFileType.defaultExtension}")?.let {
                return VcSketchyModuleNamespace(provider, it)
            }
        }
        return null
    }

    override fun getRegisteredClass(): Abstract.ClassDefinition? =
            if (file is VcFile && provider.isRegistered(file)) file else null
}
