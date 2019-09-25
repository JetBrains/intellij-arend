package org.arend.psi.ext

import com.intellij.psi.PsiElement
import org.arend.psi.ArendWhere
import org.arend.psi.ArendReturnExpr
import org.arend.psi.ArendPrec
import org.arend.psi.ArendNameTele
import org.arend.psi.ArendDefIdentifier

interface ArendFunctionalDefinition : ArendCompositeElement {
    val defIdentifier: ArendDefIdentifier?

    val body: ArendFunctionalBody?

    val nameTeleList: List<ArendNameTele>

    fun getPrec(): ArendPrec?

    val returnExpr: ArendReturnExpr?

    val where: ArendWhere?

    val colon: PsiElement?
}