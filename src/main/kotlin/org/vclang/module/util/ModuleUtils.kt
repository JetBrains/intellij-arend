package org.vclang.module.util

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.jetbrains.jetpad.vclang.util.FileUtils
import org.vclang.vclpsi.VclFile

val Module.defaultRoot: VirtualFile?
    get() = ModuleRootManager.getInstance(this).contentEntries.firstOrNull()?.file

val Module.vclFile: VclFile?
    get() {
        val virtualFile = defaultRoot?.findChild(name + FileUtils.LIBRARY_EXTENSION) ?: return null
        return PsiManager.getInstance(project).findFile(virtualFile) as? VclFile
    }

val Module.isVcModule: Boolean
    get() = vclFile != null
