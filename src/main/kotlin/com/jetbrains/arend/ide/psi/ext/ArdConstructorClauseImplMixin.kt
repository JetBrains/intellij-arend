package com.jetbrains.arend.ide.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.arend.ide.psi.ArdConstructor
import com.jetbrains.arend.ide.psi.ArdConstructorClause
import com.jetbrains.arend.ide.psi.ArdPattern


abstract class ArdConstructorClauseImplMixin(node: ASTNode) : ArdSourceNodeImpl(node), ArdConstructorClause {
    override fun getData(): ArdConstructorClauseImplMixin = this

    override fun getPatterns(): List<ArdPattern> = patternList

    override fun getConstructors(): List<ArdConstructor> = constructorList
}