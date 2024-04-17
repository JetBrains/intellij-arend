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
}