package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.util.PsiTreeUtil
import org.arend.psi.ArendConstructor
import org.arend.psi.ArendConstructorClause
import org.arend.psi.parser.api.ArendPattern


abstract class ArendConstructorClauseImplMixin(node: ASTNode) : ArendSourceNodeImpl(node), ArendConstructorClause {
    override fun getData(): ArendConstructorClauseImplMixin = this

    override fun getPatterns(): List<ArendPattern> = PsiTreeUtil.getChildrenOfTypeAsList(this, ArendPattern::class.java)

    override fun getConstructors(): List<ArendConstructor> = constructorList

    override fun isLocal() = false
}