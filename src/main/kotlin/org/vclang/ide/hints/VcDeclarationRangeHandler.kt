package org.vclang.ide.hints

import com.intellij.codeInsight.hint.DeclarationRangeHandler
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.vclang.lang.core.psi.VcDefClass
import org.vclang.lang.core.psi.VcDefData
import org.vclang.lang.core.psi.VcDefFunction
import org.vclang.lang.core.psi.VcDefInstance

class VcDeclarationRangeHandler : DeclarationRangeHandler<PsiElement> {
    override fun getDeclarationRange(container: PsiElement): TextRange {
        val start = container.textRange.startOffset
        return when (container) {
            is VcDefClass -> {
                val lastTele = container.classTeles?.teleList?.lastOrNull()
                TextRange(start, (lastTele ?: container.identifier)!!.textRange.endOffset)
            }
            is VcDefData -> {
                val lastTele = container.teleList.lastOrNull()
                TextRange(start, (lastTele ?: container.identifier)!!.textRange.endOffset)
            }
            is VcDefInstance -> {
                val lastTele = container.teleList.lastOrNull()
                TextRange(start, (lastTele ?: container.identifier)!!.textRange.endOffset)
            }
            is VcDefFunction -> {
                val lastTele = container.teleList.lastOrNull()
                TextRange(start, (lastTele ?: container.identifier)!!.textRange.endOffset)
            }
            else -> container.textRange
        }
    }
}
