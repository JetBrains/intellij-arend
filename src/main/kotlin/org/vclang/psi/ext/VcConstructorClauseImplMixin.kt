package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import org.vclang.psi.VcConstructor
import org.vclang.psi.VcConstructorClause
import org.vclang.psi.VcPattern


abstract class VcConstructorClauseImplMixin(node: ASTNode) : VcCompositeElementImpl(node), VcConstructorClause {
    override fun getData(): VcConstructorClauseImplMixin = this

    override fun getPatterns(): List<VcPattern> = patternList

    override fun getConstructors(): List<VcConstructor> = constructorList
}