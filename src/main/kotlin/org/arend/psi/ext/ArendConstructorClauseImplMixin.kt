package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.psi.ArendConstructor
import org.arend.psi.ArendConstructorClause
import org.arend.psi.ArendPattern


abstract class ArendConstructorClauseImplMixin(node: ASTNode) : ArendSourceNodeImpl(node), ArendConstructorClause {
    override fun getData(): ArendConstructorClauseImplMixin = this

    override fun getPatterns(): List<ArendPattern> = patternList

    override fun getConstructors(): List<ArendConstructor> = constructorList
}