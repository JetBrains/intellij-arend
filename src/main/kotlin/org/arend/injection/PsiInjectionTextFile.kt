package org.arend.injection

import com.intellij.openapi.util.TextRange
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.impl.source.PsiFileImpl
import org.arend.core.expr.Expression
import org.arend.naming.scope.EmptyScope
import org.arend.naming.scope.Scope


class PsiInjectionTextFile(provider: FileViewProvider) : PsiFileImpl(InjectionTextFileElementType, InjectionTextFileElementType, provider) {
    var injectionRanges = ArrayList<List<TextRange>>()
    var injectedExpressions = ArrayList<Expression?>()
    var scope: Scope = EmptyScope.INSTANCE
    var errorRanges = ArrayList<TextRange>()

    val hasInjection: Boolean
        get() = injectionRanges.isNotEmpty()

    override fun accept(visitor: PsiElementVisitor) {
        visitor.visitFile(this)
    }

    override fun getFileType() = InjectionTextFileType.INSTANCE

    private fun insertPosition(pos: Int, injectionRanges: List<TextRange>, inclusive: Boolean): Int {
        var skipped = 0
        for (injectionRange in injectionRanges) {
            if (if (inclusive) pos <= skipped + injectionRange.length else pos < skipped + injectionRange.length) {
                return injectionRange.startOffset + pos
            }
            skipped += injectionRange.length
        }
        return -1
    }

    fun addErrorRange(range: TextRange, injectionRanges: List<TextRange>) {
        val start = insertPosition(range.startOffset, injectionRanges, false)
        val end = insertPosition(range.endOffset, injectionRanges, true)
        if (start >= 0 && end >= 0) {
            errorRanges.add(TextRange(start, end))
        }
    }
}