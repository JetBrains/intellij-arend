package org.vclang.annotation

import com.intellij.codeInsight.daemon.impl.HighlightRangeExtension
import com.intellij.psi.PsiFile
import org.vclang.psi.VcFile


class VcHighlightRangeExtension : HighlightRangeExtension {
    override fun isForceHighlightParents(file: PsiFile) = file is VcFile
}