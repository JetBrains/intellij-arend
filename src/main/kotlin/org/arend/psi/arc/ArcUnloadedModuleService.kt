package org.arend.psi.arc

import com.intellij.openapi.components.Service
import com.intellij.openapi.vfs.VirtualFile

@Service(Service.Level.PROJECT)
class ArcUnloadedModuleService {
    private val unloadedModules = HashSet<VirtualFile>()

    fun addUnloadedModule(file: VirtualFile) {
        unloadedModules.add(file)
    }

    fun removeLoadedModule(file: VirtualFile) {
        unloadedModules.remove(file)
    }

    fun containsUnloadedModule(file: VirtualFile): Boolean {
        return unloadedModules.contains(file)
    }

    companion object {
        val DEFINITION_IS_NOT_LOADED = "Definition (.+):(.+) is not loaded".toRegex()
        val NOT_FOUND_MODULE = "Cannot find module: (.+)".toRegex()
    }
}