package com.jetbrains.arend.ide.annotation

import com.intellij.codeInsight.daemon.impl.HighlightRangeExtension
import com.intellij.psi.PsiFile
import com.jetbrains.arend.ide.psi.ArdFile


class ArdHighlightRangeExtension : HighlightRangeExtension {
    override fun isForceHighlightParents(file: PsiFile) = file is ArdFile
}