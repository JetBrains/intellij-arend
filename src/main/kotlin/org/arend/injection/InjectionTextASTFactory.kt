package org.arend.injection

import com.intellij.lang.ASTFactory
import com.intellij.psi.tree.IElementType


class InjectionTextASTFactory : ASTFactory() {
    override fun createLeaf(type: IElementType, text: CharSequence) =
        if (type == InjectionTextFileElementType.INJECTION_TEXT) PsiInjectionText(text) else null
}