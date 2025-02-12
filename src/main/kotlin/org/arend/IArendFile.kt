package org.arend

import com.intellij.psi.PsiFile
import org.arend.psi.ext.ArendCompositeElement

interface IArendFile: PsiFile, ArendCompositeElement {
    fun moduleInitialized(): Boolean = true
}