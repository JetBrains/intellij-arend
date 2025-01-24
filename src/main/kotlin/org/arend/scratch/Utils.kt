package org.arend.scratch

import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.openapi.vfs.VirtualFile

const val SCRATCH_SUFFIX = "ars"

val VirtualFile.isArendScratch: Boolean
    get() = SCRATCH_SUFFIX == this.extension &&
            ScratchFileService.getInstance().getRootType(this) is ScratchRootType