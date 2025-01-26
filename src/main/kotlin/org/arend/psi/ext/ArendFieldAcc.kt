package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.arend.psi.ArendElementTypes
import org.arend.psi.childOfType
import org.arend.term.abs.Abstract

class ArendFieldAcc(node: ASTNode) : ArendSourceNodeImpl(node), Abstract.FieldAcc {
    val number: PsiElement?
        get() = findChildByType(ArendElementTypes.NUMBER)

    val refIdentifier: ArendRefIdentifier?
        get() = childOfType()

    override fun getData() = this

    override fun getNumber() = number?.text?.toInt()

    override fun getFieldRef() = refIdentifier?.referent
}