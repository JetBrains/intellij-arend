package com.jetbrains.arend.ide.highlight

import com.intellij.openapi.util.Condition
import com.intellij.openapi.vfs.VirtualFile

class ArdProblemHighlightFilter : Condition<VirtualFile> {
    override fun value(t: VirtualFile?): Boolean {
        return true
    }

}