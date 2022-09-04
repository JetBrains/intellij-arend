package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.util.PsiTreeUtil
import org.arend.psi.getChildrenOfType
import org.arend.term.abs.Abstract


class ArendConstructorClause(node: ASTNode) : ArendSourceNodeImpl(node), Abstract.ConstructorClause {
    override fun getData(): ArendConstructorClause = this

    override fun getPatterns(): List<ArendPattern> = PsiTreeUtil.getChildrenOfTypeAsList(this, ArendPattern::class.java)

    override fun getConstructors(): List<ArendConstructor> = getChildrenOfType()

    override fun isLocal() = false
}