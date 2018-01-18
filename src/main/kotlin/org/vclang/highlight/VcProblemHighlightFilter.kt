package org.vclang.highlight

import com.intellij.openapi.util.Condition
import com.intellij.openapi.vfs.VirtualFile

class VcProblemHighlightFilter : Condition<VirtualFile> {
    override fun value(t: VirtualFile?): Boolean {
        return true
    }

}