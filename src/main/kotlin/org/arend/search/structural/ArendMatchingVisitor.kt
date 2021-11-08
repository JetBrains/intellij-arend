package org.arend.search.structural

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import com.intellij.structuralsearch.impl.matcher.GlobalMatchingVisitor
import org.arend.error.DummyErrorReporter
import org.arend.naming.BinOpParser
import org.arend.psi.ArendExpr
import org.arend.psi.ArendVisitor
import org.arend.psi.ext.ArendFunctionalDefinition

class ArendMatchingVisitor(private val matchingVisitor: GlobalMatchingVisitor) : ArendVisitor() {

    val binOpParser = BinOpParser(DummyErrorReporter.INSTANCE)


    override fun visitExpr(o: ArendExpr) {
        super.visitExpr(o)
        val matchedElement = this.matchingVisitor.element
        matchingVisitor.result = false
        val parentType =
            matchedElement.parentOfType<ArendFunctionalDefinition>()?.returnExpr?.exprList?.getOrNull(0) ?: return
        if (!PsiTreeUtil.isAncestor(parentType, matchedElement, false)) {
            return
        }
        val matcher = ArendExpressionMatcher(o.getPatternTree())
        if (matchedElement is ArendExpr && matcher.match(matchedElement)) {
            matchingVisitor.result = true
        }
    }
}
