package org.vclang.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import com.jetbrains.jetpad.vclang.term.Precedence
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import com.jetbrains.jetpad.vclang.term.abs.AbstractDefinitionVisitor
import org.vclang.VcIcons
import org.vclang.psi.VcDefFunction
import org.vclang.psi.VcExpr
import org.vclang.psi.VcFunctionBody
import org.vclang.psi.VcTele
import org.vclang.psi.stubs.VcDefFunctionStub
import javax.swing.Icon

abstract class FunctionDefinitionAdapter : DefinitionAdapter<VcDefFunctionStub>, VcDefFunction, Abstract.FunctionDefinition {
    constructor(node: ASTNode) : super(node)

    constructor(stub: VcDefFunctionStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getParameters(): List<VcTele> = teleList

    override fun getResultType(): VcExpr? = expr

    override fun getBody(): VcFunctionBody? = functionBody

    override fun getPrecedence(): Precedence = calcPrecedence(prec)

    override fun <R : Any?> accept(visitor: AbstractDefinitionVisitor<out R>): R = visitor.visitFunction(this)

    override fun getIcon(flags: Int): Icon = VcIcons.FUNCTION_DEFINITION
}
