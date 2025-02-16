package org.arend.injection

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.components.service
import com.intellij.openapi.util.TextRange
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.impl.source.PsiFileImpl
import org.arend.core.expr.Expression
import org.arend.error.DummyErrorReporter
import org.arend.naming.resolving.CollectingResolverListener
import org.arend.naming.resolving.visitor.ExpressionResolveNameVisitor
import org.arend.naming.scope.Scope
import org.arend.psi.ext.ArendReferenceElement
import org.arend.psi.ext.ArendReplLine
import org.arend.server.ArendServerService
import org.arend.term.abs.ConcreteBuilder
import org.arend.term.concrete.Concrete


class PsiInjectionTextFile(provider: FileViewProvider) : PsiFileImpl(InjectionTextFileElementType, InjectionTextFileElementType, provider) {
    var injectionRanges = ArrayList<List<TextRange>>()
    var injectedExpressions = ArrayList<Expression?>()
    var errorRanges = ArrayList<TextRange>()
    val concreteExpressions = ArrayList<Concrete.Expression?>()

    val hasInjection: Boolean
        get() = injectionRanges.isNotEmpty()

    override fun accept(visitor: PsiElementVisitor) {
        visitor.visitFile(this)
    }

    override fun getFileType() = InjectionTextFileType.INSTANCE

    fun annotate(scope: Scope) {
        val files = InjectedLanguageManager.getInstance(project).getInjectedPsiFiles(firstChild ?: return) ?: return
        if (concreteExpressions.size >= files.size) return
        val listener = CollectingResolverListener(true)
        val typingInfo = project.service<ArendServerService>().server.typingInfo
        for (pair in files.subList(concreteExpressions.size, files.size)) {
            val expr = (pair.first.firstChild as? ArendReplLine)?.expr
            if (expr != null) {
                val concrete = ConcreteBuilder.convertExpression(expr)
                concrete.accept(ExpressionResolveNameVisitor(scope, mutableListOf(), typingInfo, DummyErrorReporter.INSTANCE, listener), null)
                concreteExpressions.add(concrete)
            } else {
                concreteExpressions.add(null)
            }
        }

        for (resolvedReference in listener.getCacheStructure(null).cache) {
            (resolvedReference.reference as? ArendReferenceElement)?.putResolved(resolvedReference.referable)
        }
    }
}