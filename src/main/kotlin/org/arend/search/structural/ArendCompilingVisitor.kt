package org.arend.search.structural

import com.intellij.dupLocator.util.NodeFilter
import com.intellij.psi.PsiElement
import com.intellij.structuralsearch.impl.matcher.compiler.GlobalCompilingVisitor
import com.intellij.structuralsearch.impl.matcher.handlers.SubstitutionHandler
import com.intellij.structuralsearch.impl.matcher.handlers.TopLevelMatchingHandler
import org.arend.psi.ArendAppExpr
import org.arend.psi.ArendExpr
import org.arend.psi.ArendVisitor
import org.arend.psi.ext.ArendReferenceElement

class ArendCompilingVisitor(private val globalVisitor: GlobalCompilingVisitor) : ArendVisitor() {
    fun compile(elements : Array<out PsiElement>?) {
        elements ?: return
        val context = globalVisitor.context
        val pattern = context.pattern
        for (element in elements) {
            element.accept(this)
            pattern.setHandler(element, TopLevelMatchingHandler(pattern.getHandler(element)))
        }
    }

    private fun getHandler(element: PsiElement) = globalVisitor.context.pattern.getHandler(element)

    override fun visitElement(element: PsiElement) {
        globalVisitor.handle(element)
        super.visitElement(element)
    }

    override fun visitAppExpr(o: ArendAppExpr) {
        super.visitAppExpr(o)
        getHandler(o).setFilter { it is ArendAppExpr }
    }

    override fun visitExpr(o: ArendExpr) {
        super.visitExpr(o)
        getHandler(o).setFilter { true }
    }

    override fun visitReferenceElement(o: ArendReferenceElement) {
        visitElement(o)
        val handler = getHandler(o)
        getHandler(o).filter =
            if (handler is SubstitutionHandler) NodeFilter { it is PsiElement } // accept all
            else NodeFilter { it is ArendReferenceElement }
    }
}