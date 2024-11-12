package org.arend.psi.arc

import com.intellij.openapi.components.Service
import com.intellij.openapi.vfs.VirtualFile

@Service(Service.Level.PROJECT)
class ArcErrorService {
    val errors = HashMap<VirtualFile, String>()

    companion object {
        val DEFINITION_IS_NOT_LOADED = "Definition (.+):(.+) is not loaded".toRegex()
    }
}