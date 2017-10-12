package org.vclang.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import com.jetbrains.jetpad.vclang.term.abs.AbstractDefinitionVisitor
import org.vclang.psi.VcDefClassView
import org.vclang.psi.stubs.VcDefClassViewStub

abstract class ClassViewAdapter : DefinitionAdapter<VcDefClassViewStub>, VcDefClassView {
    constructor(node: ASTNode) : super(node)

    constructor(stub: VcDefClassViewStub, nodeType: IStubElementType<*, *>): super(stub, nodeType)

    override fun <R : Any?> accept(visitor: AbstractDefinitionVisitor<out R>?): R? = null // TODO[classes]
}
