package org.arend.injection

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.util.PsiTreeUtil
import org.arend.core.expr.Expression
import org.arend.naming.scope.Scope
import org.arend.psi.ext.ArendAtomFieldsAcc
import org.arend.psi.ext.ArendLongName
import org.arend.psi.ext.ArendRefIdentifier


class PsiInjectionTextFile(provider: FileViewProvider) : PsiFileImpl(InjectionTextFileElementType, InjectionTextFileElementType, provider) {
    var injectionRanges = ArrayList<List<TextRange>>()
    var injectedExpressions = ArrayList<Expression?>()
    var errorRanges = ArrayList<TextRange>()

    val hasInjection: Boolean
        get() = injectionRanges.isNotEmpty()

    override fun accept(visitor: PsiElementVisitor) {
        visitor.visitFile(this)
    }

    override fun getFileType() = InjectionTextFileType.INSTANCE

    fun annotate(scope: Scope, offset: Int) {
        val files = InjectedLanguageManager.getInstance(project).getInjectedPsiFiles(firstChild ?: return) ?: return
        val visitor = object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                when (element) {
                    is ArendAtomFieldsAcc -> {
                        val headRefIdentifier = element.atom.literal?.refIdentifier ?: return
                        val headName = headRefIdentifier.referenceName
                        val headRef = scope.resolveName(headName)
                        if (headRef != null) headRefIdentifier.putResolved(headRef)
                        var curScope = scope.resolveNamespace(headName) ?: return
                        for (fieldAcc in element.fieldAccList) {
                            val refIdentifier = fieldAcc.refIdentifier ?: break
                            val name = refIdentifier.referenceName
                            val ref = curScope.resolveName(name)
                            if (ref != null) refIdentifier.putResolved(ref)
                            curScope = curScope.resolveNamespace(name) ?: break
                        }
                    }
                    is ArendLongName -> {
                        var curScope = scope
                        for (refIdentifier in element.refIdentifierList) {
                            val name = refIdentifier.referenceName
                            val ref = curScope.resolveName(name)
                            if (ref != null) refIdentifier.putResolved(ref)
                            curScope = curScope.resolveNamespace(name) ?: break
                        }
                    }
                    is ArendRefIdentifier -> if (element.cachedReferable == null) {
                        val ref = scope.resolveName(element.referenceName)
                        if (ref != null) element.putResolved(ref)
                    }
                }
            }
        }

        for (pair in files) {
            if (pair.second.startOffset < offset) continue
            PsiTreeUtil.processElements(pair.first) { it.accept(visitor); true }
        }
    }
}