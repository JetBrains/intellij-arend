package org.arend.annotation

import com.intellij.codeInsight.daemon.impl.HighlightRangeExtension
import com.intellij.psi.PsiFile
import org.arend.psi.ArendFile


class ArendHighlightRangeExtension : HighlightRangeExtension {
    override fun isForceHighlightParents(file: PsiFile) = file is ArendFile
}