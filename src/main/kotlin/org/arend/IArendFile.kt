package org.arend

import com.intellij.psi.PsiFile
import org.arend.psi.ext.ArendCompositeElement
import org.arend.term.group.Group
import java.util.concurrent.atomic.AtomicLong

interface IArendFile: PsiFile, ArendCompositeElement {
    var lastModification: AtomicLong

    fun moduleInitialized(): Boolean = true
}