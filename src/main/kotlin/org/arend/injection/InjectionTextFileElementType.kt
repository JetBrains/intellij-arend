package org.arend.injection

import com.intellij.lang.ASTFactory
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import org.arend.InjectionTextLanguage


object InjectionTextFileElementType : IFileElementType("INJECTION_TEXT_FILE", InjectionTextLanguage.INSTANCE) {
    val INJECTION_TEXT = IElementType("INJECTION_TEXT", InjectionTextLanguage.INSTANCE)

    override fun parseContents(chameleon: ASTNode) = ASTFactory.leaf(INJECTION_TEXT, chameleon.chars)
}
