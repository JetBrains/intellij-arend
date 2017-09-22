package org.vclang.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import com.jetbrains.jetpad.vclang.frontend.term.Abstract
import com.jetbrains.jetpad.vclang.frontend.term.AbstractDefinitionVisitor
import org.vclang.VcIcons
import org.vclang.psi.VcDefFunction
import org.vclang.psi.stubs.VcDefFunctionStub
import javax.swing.Icon

abstract class FunctionDefinitionAdapter : DefinitionAdapter<VcDefFunctionStub>, VcDefFunction, Abstract.FunctionDefinition {
    constructor(node: ASTNode) : super(node)

    constructor(stub: VcDefFunctionStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getParameters(): List<Abstract.Parameter> {
        TODO("not implemented")
    }

    override fun getResultType(): Abstract.Expression? {
        TODO("not implemented")
    }

    override fun getBody(): Abstract.FunctionBody {
        TODO("not implemented")
    }

    override fun <R : Any?> accept(visitor: AbstractDefinitionVisitor<out R>): R = visitor.visitFunction(this)

    override fun getIcon(flags: Int): Icon = VcIcons.FUNCTION_DEFINITION
}
