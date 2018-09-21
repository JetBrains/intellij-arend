package org.arend.highlight

import com.intellij.openapi.util.Condition
import com.intellij.openapi.vfs.VirtualFile

class ArendProblemHighlightFilter : Condition<VirtualFile> {
    override fun value(t: VirtualFile?): Boolean {
        return true
    }

}