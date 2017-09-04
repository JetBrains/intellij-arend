package org.vclang.codeInsight

import com.intellij.codeInsight.hint.DeclarationRangeHandler
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.vclang.psi.VcDefClass
import org.vclang.psi.VcDefData
import org.vclang.psi.VcDefFunction
import org.vclang.psi.VcDefInstance

class VcDeclarationRangeHandler : DeclarationRangeHandler<PsiElement> {
    override fun getDeclarationRange(container: PsiElement): TextRange {
        val start = container.textRange.startOffset
        return when (container) {
            is VcDefClass -> {
                val lastTele = container.classTeles?.teleList?.lastOrNull()
                TextRange(start, (lastTele ?: container.defIdentifier)!!.textRange.endOffset)
            }
            is VcDefData -> {
                val lastTele = container.teleList.lastOrNull()
                TextRange(start, (lastTele ?: container.defIdentifier)!!.textRange.endOffset)
            }
            is VcDefInstance -> {
                val lastTele = container.teleList.lastOrNull()
                TextRange(start, (lastTele ?: container.defIdentifier)!!.textRange.endOffset)
            }
            is VcDefFunction -> {
                val lastTele = container.teleList.lastOrNull()
                TextRange(start, (lastTele ?: container.defIdentifier)!!.textRange.endOffset)
            }
            else -> container.textRange
        }
    }
}
