package org.arend.quickfix

import com.intellij.psi.util.childrenOfType
import org.arend.psi.ArendPsiFactory
import org.arend.psi.childOfType
import org.arend.psi.ext.ArendCaseArg
import org.arend.psi.ext.ArendCaseExpr
import org.arend.psi.ext.ArendReturnExpr
import org.arend.psi.nextElement

internal fun updateReturnExpression(psiFactory: ArendPsiFactory, element: ArendCaseExpr) {
    if (element.childOfType<ArendReturnExpr>() != null) {
        return
    }
    var returnKeyword = psiFactory.createReturnKeyword()
    var returnExpr = psiFactory.createReturnExpr()
    val whiteSpace = psiFactory.createWhitespace(" ")

    val caseArg = element.childrenOfType<ArendCaseArg>().let {
        if (it.isEmpty()) {
            return
        }
        it.last()
    }
    val whiteSpaceAfterCaseArg = if (caseArg.nextElement == null) {
        element.addAfter(whiteSpace, caseArg)
    } else {
        caseArg.nextElement
    }
    returnKeyword = element.addAfter(returnKeyword, whiteSpaceAfterCaseArg)
    returnExpr = element.addAfter(returnExpr, returnKeyword) as ArendReturnExpr

    element.addBefore(whiteSpace, returnExpr)
    element.addAfter(whiteSpace, returnExpr)
}
