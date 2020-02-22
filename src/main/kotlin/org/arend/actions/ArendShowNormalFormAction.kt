package org.arend.actions

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.arend.core.expr.Expression
import org.arend.ext.core.ops.NormalizationMode
import org.arend.psi.ArendExpr
import org.arend.refactoring.prettyPopupExpr

class ArendShowNormalFormAction : ArendPopupAction() {
    override fun getHandler() = object : ArendPopupHandler(requestFocus) {
        override fun pretty(project: Project, subCore: Expression, subPsi: ArendExpr, range: TextRange) =
                prettyPopupExpr(project, subCore.normalize(NormalizationMode.NF))
    }
}