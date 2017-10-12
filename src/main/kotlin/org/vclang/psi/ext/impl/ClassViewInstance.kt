package org.vclang.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import com.jetbrains.jetpad.vclang.term.abs.AbstractDefinitionVisitor
import org.vclang.psi.VcDefInstance
import org.vclang.psi.stubs.VcDefInstanceStub

abstract class InstanceAdapter : DefinitionAdapter<VcDefInstanceStub>, VcDefInstance {
    constructor(node: ASTNode) : super(node)

    constructor(stub: VcDefInstanceStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun <R : Any?> accept(visitor: AbstractDefinitionVisitor<out R>?): R? = null // TODO[classes]
}
