package org.arend.quickfix

import com.intellij.psi.util.childrenOfType
import org.arend.psi.ArendPsiFactory
import org.arend.psi.descendantOfType
import org.arend.psi.ext.ArendCaseArg
import org.arend.psi.ext.ArendCaseExpr
import org.arend.psi.ext.ArendReturnExpr

internal fun updateReturnExpression(psiFactory: ArendPsiFactory, element: ArendCaseExpr): ArendReturnExpr? {
    if (element.descendantOfType<ArendReturnExpr>() != null) {
        return null
    }
    var returnKeyword = psiFactory.createReturnKeyword()
    val returnExpr = psiFactory.createReturnExpr()

    val caseArg = element.childrenOfType<ArendCaseArg>().let {
        if (it.isEmpty()) return null
        it.last()
    }

    returnKeyword = element.addAfter(returnKeyword, caseArg)
    return element.addAfter(returnExpr, returnKeyword) as ArendReturnExpr
}
