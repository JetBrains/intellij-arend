package org.arend.actions

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.arend.core.expr.Expression
import org.arend.core.expr.LamExpression
import org.arend.psi.ArendExpr
import org.arend.psi.ext.ArendLamExprImplMixin
import org.arend.refactoring.prettyPopupExpr
import org.arend.util.asSequence

class ArendShowTypeAction : ArendPopupAction() {
    override fun getHandler() = object : ArendPopupHandler(requestFocus) {
        override fun pretty(project: Project, subCore: Expression, subPsi: ArendExpr, range: TextRange) = when {
            subPsi is ArendLamExprImplMixin && subCore is LamExpression -> {
                val index = subPsi.parameters.indexOfFirst { range in it.textRange }
                if (index < 0) prettyPopupExpr(project, subCore.type)
                else {
                    val link = subCore.parameters.asSequence().elementAt(index)
                    prettyPopupExpr(project, link.typeExpr)
                }
            }
            else -> prettyPopupExpr(project, subCore.type)
        }
    }
}