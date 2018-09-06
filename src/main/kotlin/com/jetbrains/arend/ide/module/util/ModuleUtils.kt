package com.jetbrains.arend.ide.module.util

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.jetbrains.arend.ide.ardlpsi.ArdlFile
import com.jetbrains.jetpad.vclang.util.FileUtils

val Module.defaultRoot: VirtualFile?
    get() = ModuleRootManager.getInstance(this).contentEntries.firstOrNull()?.file

val Module.ardlFile: ArdlFile?
    get() {
        val virtualFile = defaultRoot?.findChild(name + FileUtils.LIBRARY_EXTENSION) ?: return null
        return PsiManager.getInstance(project).findFile(virtualFile) as? ArdlFile
    }

val Module.isArdModule: Boolean
    get() = ardlFile != null
