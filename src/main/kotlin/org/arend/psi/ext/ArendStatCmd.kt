package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import org.arend.naming.scope.Scope
import org.arend.psi.*
import org.arend.psi.ArendElementTypes.*
import org.arend.term.ChildNamespaceCommand
import org.arend.term.NamespaceCommand
import org.arend.term.abs.Abstract

class ArendStatCmd(node: ASTNode) : ArendSourceNodeImpl(node), Abstract.NamespaceCommandHolder, ChildNamespaceCommand {
    override fun getKind(): NamespaceCommand.Kind = when (firstRelevantChild.elementType) {
        IMPORT_KW -> NamespaceCommand.Kind.IMPORT
        OPEN_KW -> NamespaceCommand.Kind.OPEN
        else -> error("Incorrect expression: namespace command")
    }

    val longName: ArendLongName?
        get() = childOfType()

    val nsUsing: ArendNsUsing?
        get() = childOfType()

    val lparen: PsiElement?
        get() = findChildByType(LPAREN)

    val rparen: PsiElement?
        get() = findChildByType(RPAREN)

    val hidingKw: PsiElement?
        get() = findChildByType(HIDING_KW)

    val importKw: PsiElement?
        get() = findChildByType(IMPORT_KW)

    val openKw: PsiElement?
        get() = findChildByType(OPEN_KW)

    override fun getReferenceList() = longName?.refIdentifierList ?: emptyList()

    override fun getPath() = longName?.refIdentifierList?.map { it.referenceName } ?: emptyList()

    override fun isUsing(): Boolean {
        val using = nsUsing
        return using == null || using.usingKw != null
    }

    override fun getOpenedReferences(): List<ArendNsId> = nsUsing?.nsIdList ?: emptyList()

    override fun getHiddenReferences(): List<ArendScId> = getChildrenOfType()

    override fun getParentGroup() = parent.ancestor<ArendGroup>()

    override fun getOpenedReference() = longName

    companion object {
        fun getScopeContext(element: PsiElement?): Scope.ScopeContext = when (element?.elementType) {
            DOT -> Scope.ScopeContext.DYNAMIC
            PLEVEL_KW -> Scope.ScopeContext.PLEVEL
            HLEVEL_KW -> Scope.ScopeContext.HLEVEL
            else -> Scope.ScopeContext.STATIC
        }

    }
}
